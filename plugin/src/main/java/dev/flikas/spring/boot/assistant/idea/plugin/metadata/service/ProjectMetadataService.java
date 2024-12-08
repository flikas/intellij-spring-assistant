package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.google.gson.Gson;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.AggregatedMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.ConfigurationMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.MetadataFileIndex;
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

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.MetadataFileIndex.ADDITIONAL_METADATA_FILE_NAME;
import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.MetadataFileIndex.METADATA_FILE_NAME;

/**
 * Service that generates {@link MetadataIndex} from one {@linkplain ModuleRootModel#getSourceRoots() SourceRoot}.
 * <p>
 * It searches and generate index from Spring Configuration Files ({@value METADATA_FILE}, {@value ADDITIONAL_METADATA_FILE})
 * in the source root and watches them for automatically update the index.
 */
@Service(Service.Level.PROJECT)
final class ProjectMetadataService implements Disposable {
  private final Logger log = Logger.getInstance(ProjectMetadataService.class);
  private final ThreadLocal<Gson> gson = ThreadLocal.withInitial(Gson::new);
  private final Project project;
  private final ConcurrentMap<String, IndexFromOneFile> metadataFiles = new ConcurrentHashMap<>();
  @Getter private final MetadataIndex emptyIndex;


  public ProjectMetadataService(Project project) {
    this.project = project;
    this.emptyIndex = MetadataIndex.empty(this.project);
    VirtualFileManager.getInstance().addAsyncFileListener(new FileWatcher(), this);
    project.getMessageBus().connect().subscribe(WorkspaceModelTopics.CHANGED, new ModuleDependenciesWatcher(project));
  }


  public MutableReference<MetadataIndex> getIndexForMetaFile(@NotNull VirtualFile metadataFile) {
    return getIndex(metadataFile);
  }


  @Override
  public void dispose() {
    // This is a parent disposable for FileWatcher.
  }


  private IndexFromOneFile getIndex(@NotNull VirtualFile metadataFile) {
    return metadataFiles.computeIfAbsent(metadataFile.getUrl(), url -> new IndexFromOneFile(metadataFile));
  }


  private class IndexFromOneFile implements MutableReference<MetadataIndex> {
    @NotNull private final VirtualFile metadataFile;
    private long lastMetadataModificationStamp = -1;
    private MetadataIndex metadata;


    private IndexFromOneFile(@NotNull VirtualFile metadataFile) {
      this.metadataFile = metadataFile;
      reload();
    }


    @Override
    public @Nullable MetadataIndex dereference() {
      reload();
      return this.metadata;
    }


    synchronized void reload() {
      if (!this.metadataFile.isValid()) {
        this.metadata = null;
        return;
      }
      if (this.metadataFile.getModificationStamp() == this.lastMetadataModificationStamp) {
        return;
      }
      try {
        AggregatedMetadataIndex index = new AggregatedMetadataIndex(generateIndex(this.metadataFile));
        // Spring does not create metadata for types in collections, we should create it by ourselves and expand our index,
        // to better support code-completion, documentation, navigation, etc.
        for (MetadataProperty property : index.getProperties().values()) {
          resolvePropertyType(property).ifPresent(index::addFirst);
        }
        this.lastMetadataModificationStamp = this.metadataFile.getModificationStamp();
        this.metadata = index;
      } catch (IOException e) {
        log.warn("Read metadata file " + this.metadataFile.getUrl() + " failed", e);
      }
    }


    @Override
    public String toString() {
      return "Metadata index form " + this.metadataFile.getPresentableUrl();
    }
  }


  /**
   * @see ConfigurationMetadata.Property#getType()
   */
  @NotNull
  private Optional<MetadataIndex> resolvePropertyType(@NotNull MetadataProperty property) {
    return property
        .getFullType()
        .filter(t -> PsiTypeUtils.isCollectionOrMap(project, t))
        .flatMap(t -> project.getService(ProjectClassMetadataService.class).getMetadata(property.getNameStr(), t));
  }


  @NotNull
  private MetadataIndex generateIndex(VirtualFile file) throws IOException {
    try (Reader reader = new InputStreamReader(file.getInputStream(), file.getCharset())) {
      ConfigurationMetadata meta = gson.get().fromJson(reader, ConfigurationMetadata.class);
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

      return interested.isEmpty() ? null : new ChangeApplier() {
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
          indicator.setText2("Waiting index...");
          if (!dumbService.runReadActionInSmartMode(() -> {
            indicator.setText2("");
            return MetadataFileIndex.isMetaFile(file, project);
          })) {
            continue;
          }
          indicator.setText("Loading " + file.getPresentableUrl());
          IndexFromOneFile index = getIndex(file);
          index.reload();
          if (index.metadata == null) {
            metadataFiles.remove(file.getUrl());
          }
          indicator.setText("");
        }
      }
    }
  }
}
