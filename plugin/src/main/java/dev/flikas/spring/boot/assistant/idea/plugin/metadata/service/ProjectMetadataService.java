package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.google.gson.Gson;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.AggregatedMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.ConfigurationMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.MutableReference;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service that generates {@link MetadataIndex} from one {@linkplain ModuleRootModel#getSourceRoots() SourceRoot}.
 * <p>
 * It searches and generate index from Spring Configuration Files ({@value METADATA_FILE}, {@value ADDITIONAL_METADATA_FILE})
 * in the source root and watches them for automatically update the index.
 */
@Service(Service.Level.PROJECT)
final class ProjectMetadataService implements Disposable {
  public static final String METADATA_DIR = "META-INF/";
  public static final String METADATA_FILE_NAME = "spring-configuration-metadata.json";
  public static final String ADDITIONAL_METADATA_FILE_NAME = "additional-spring-configuration-metadata.json";
  public static final String METADATA_FILE = METADATA_DIR + METADATA_FILE_NAME;
  public static final String ADDITIONAL_METADATA_FILE = METADATA_DIR + ADDITIONAL_METADATA_FILE_NAME;

  private final Logger log = Logger.getInstance(ProjectMetadataService.class);
  private final ThreadLocal<Gson> gson = ThreadLocal.withInitial(Gson::new);
  private final Project project;
  private final ConcurrentMap<String, MetadataFileRoot> metadataFiles = new ConcurrentHashMap<>();
  @Getter private final MetadataIndex emptyIndex;


  public ProjectMetadataService(Project project) {
    this.project = project;
    this.emptyIndex = MetadataIndex.empty(this.project);
    VirtualFileManager.getInstance().addAsyncFileListener(new FileWatcher(), this);
    project.getMessageBus().connect().subscribe(WorkspaceModelTopics.CHANGED, new ModuleDependenciesWatcher(project));
  }


  public Optional<MutableReference<MetadataIndex>> getIndexForClassRoot(@NotNull VirtualFile classRoot) {
    return tryGetMeta(classRoot).map(m -> m);
  }


  @Override
  public void dispose() {
    // This is a parent disposable for this plugin.
  }


  private Optional<MetadataFileRoot> tryGetMeta(@NotNull VirtualFile classRoot) {
    return Optional.ofNullable(metadataFiles.computeIfAbsent(classRoot.getUrl(), url -> {
      MetadataFileRoot mfr = new MetadataFileRoot(classRoot);
      return mfr.metadata != null ? mfr : null;
    }));
  }


  private class MetadataFileRoot implements MutableReference<MetadataIndex> {
    private final VirtualFile root;
    private VirtualFile metadataFile;
    private long lastMetadataModificationStamp = -1;
    private MetadataIndex metadata;


    private MetadataFileRoot(VirtualFile root) {
      this.root = root;
      reload();
    }


    @Override
    public @Nullable MetadataIndex dereference() {
      reload();
      return this.metadata;
    }


    synchronized void reload() {
      if (this.root.isValid() && this.metadataFile != null && this.metadataFile.isValid()
          && this.metadataFile.getModificationStamp() == this.lastMetadataModificationStamp) {
        return;
      }
      VirtualFile metaFile = findMetaFile().orElse(null);
      if (metaFile != null &&
          (this.metadataFile == null ||
               this.metadataFile.getUrl().equals(metaFile.getUrl())
                   && this.lastMetadataModificationStamp != this.metadataFile.getModificationStamp())) {
        try {
          AggregatedMetadataIndex index = new AggregatedMetadataIndex(generateIndex(metaFile));
          // Spring does not create metadata for types in collections, we should create it by ourselves and expand our index,
          // to better support code-completion, documentation, navigation, etc.
          for (MetadataProperty property : index.getProperties().values()) {
            resolvePropertyType(property).ifPresent(index::addFirst);
          }
          this.metadataFile = metaFile;
          this.lastMetadataModificationStamp = this.metadataFile.getModificationStamp();
          this.metadata = index;
        } catch (IOException e) {
          log.warn("Read metadata file " + metaFile.getUrl() + " failed", e);
        }
      } else {
        this.metadata = null;
      }
    }


    private Optional<VirtualFile> findMetaFile() {
      if (!root.isValid()) return Optional.empty();
      @NotNull Optional<VirtualFile> metadataFile = findFile(root, METADATA_FILE);
      if (metadataFile.isEmpty()) {
        // Some package has additional metadata file only, so we have to load it,
        // otherwise, spring-configuration-processor should merge additional metadata to the main one,
        // thus, the additional metadata file should not be load.
        metadataFile = findFile(root, ADDITIONAL_METADATA_FILE);
      }
      return metadataFile.filter(VirtualFile::isValid);
    }


    @NotNull
    private Optional<VirtualFile> findFile(VirtualFile root, String file) {
      return Optional.ofNullable(VfsUtil.findRelativeFile(root, file.split("/")));
    }


    /**
     * @see ConfigurationMetadata.Property#getType()
     */
    @NotNull
    private Optional<MetadataIndex> resolvePropertyType(@NotNull MetadataProperty property) {
      return property
          .getFullType()
          .filter(t -> PsiTypeUtils.isCollectionOrMap(project, t))
          .flatMap(t -> project.getService(ProjectClassMetadataService.class).getMetadata(property.getName(), t));
    }


    @NotNull
    private MetadataIndex generateIndex(VirtualFile file) throws IOException {
      ConfigurationMetadata meta = ReadAction.compute(() -> {
        try (Reader reader = new InputStreamReader(file.getInputStream(), file.getCharset())) {
          return gson.get().fromJson(reader, ConfigurationMetadata.class);
        }
      });
      return new ConfigurationMetadataIndex(project, file.getUrl(), meta);
    }
  }


  private class FileWatcher implements AsyncFileListener {
    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
      List<VirtualFile> interested = new ArrayList<>();
      for (VFileEvent event : events) {
        ProgressManager.checkCanceled();
        VirtualFile file = event.getFile();
        if (file == null) continue;
        String fileName = file.getName();
        if (!fileName.equals(METADATA_FILE_NAME) && !fileName.equals(ADDITIONAL_METADATA_FILE_NAME)) continue;
        interested.add(file);
      }

      return new ChangeApplier() {
        @Override
        public void afterVfsChange() {
          new IndexUpdater(interested).queue();
        }
      };
    }


    private class IndexUpdater extends Task.Backgroundable {
      private final List<VirtualFile> candidates;


      public IndexUpdater(List<VirtualFile> candidates) {
        super(project, "Reloading spring configuration metadata");
        this.candidates = candidates;
      }


      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        DumbService dumbService = DumbService.getInstance(project);
        for (int i = 0; i < candidates.size(); i++) {
          ProgressManager.checkCanceled();
          indicator.setFraction(i * 1.0D / candidates.size());
          VirtualFile file = candidates.get(i);
          VirtualFile root = dumbService.runReadActionInSmartMode(() -> {
            ProjectFileIndex pfi = ProjectFileIndex.getInstance(project);
            if (!pfi.isInProject(file)) return null;
            return pfi.getClassRootForFile(file);
          });
          if (root == null) continue;
          String relativePath = VfsUtilCore.getRelativePath(file, root);
          if (!(METADATA_DIR + file.getName()).equals(relativePath)) continue;
          indicator.setText("Loading " + file.getPresentableUrl());
          tryGetMeta(root).ifPresent(metadataFileRoot -> {
            metadataFileRoot.reload();
            if (metadataFileRoot.metadata == null) {
              metadataFiles.remove(root.getUrl());
            }
          });
          indicator.setText("");
        }
      }
    }
  }
}
