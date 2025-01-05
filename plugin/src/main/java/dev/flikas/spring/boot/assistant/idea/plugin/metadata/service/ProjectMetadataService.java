package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.google.gson.Gson;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.AggregatedMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.ConfigurationMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.FileMetadataSource;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.openapi.compiler.CompilerTopics.COMPILATION_STATUS;
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
    FileWatcher fileWatcher = new FileWatcher();
    VirtualFileManager.getInstance().addAsyncFileListener(fileWatcher, this);
    project.getMessageBus().connect().subscribe(COMPILATION_STATUS, fileWatcher);
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


  /**
   * @see ConfigurationMetadata.Property#getType()
   */
  @NotNull
  private Optional<MetadataIndex> resolvePropertyType(@NotNull MetadataProperty property) {
    return property.getFullType().filter(t -> PsiTypeUtils.isCollectionOrMap(project, t))
        .flatMap(t -> project.getService(ProjectClassMetadataService.class).getMetadata(property.getNameStr(), t));
  }


  private class IndexFromOneFile implements MutableReference<MetadataIndex> {
    @NotNull private final FileMetadataSource source;
    private MetadataIndex metadata;


    private IndexFromOneFile(@NotNull VirtualFile metadataFile) {
      this.source = new FileMetadataSource(metadataFile);
      refresh();
    }


    @Override
    public @Nullable MetadataIndex dereference() {
      refresh();
      return this.metadata;
    }


    @Override
    public synchronized void refresh() {
      if (!this.source.isValid()) {
        if (!this.source.tryReloadIfInvalid()) {
          this.metadata = null;
          return;
        }
      } else if (!this.source.isChanged()) {
        return;
      }
      try {
        AggregatedMetadataIndex index = new AggregatedMetadataIndex(
            new ConfigurationMetadataIndex(this.source, project));
        // Spring does not create metadata for types in collections, we should create it by ourselves and expand our index,
        // to better support code-completion, documentation, navigation, etc.
        for (MetadataProperty property : index.getProperties().values()) {
          resolvePropertyType(property).ifPresent(index::addFirst);
        }
        this.metadata = index;
      } catch (IOException e) {
        log.warn("Read metadata file " + this.source.getPresentation() + " failed", e);
      }
    }


    @Override
    public String toString() {
      return "Metadata index form " + this.source.getPresentation();
    }
  }


  private class FileWatcher implements AsyncFileListener, CompilationStatusListener {
    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
      List<VFileEvent> interested = new ArrayList<>();
      for (VFileEvent event : events) {
        ProgressManager.checkCanceled();
        String fileName = switch (event) {
          case VFileCreateEvent e -> e.getChildName();
          case VFileContentChangeEvent e -> e.getFile().getName();
          case VFileMoveEvent e -> e.getFile().getName();
          case VFileDeleteEvent e -> e.getFile().getName();
          case VFileCopyEvent e -> e.getFile().getName();
          default -> null;
        };
        if (fileName == null) continue;
        if (!fileName.equals(METADATA_FILE_NAME) && !fileName.equals(ADDITIONAL_METADATA_FILE_NAME)) continue;
        interested.add(event);
      }

      return interested.isEmpty() ? null : new ChangeApplier() {
        @Override
        public void afterVfsChange() {
          new IndexUpdater(interested).queue();
        }
      };
    }


    @Override
    public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
      List<Pair<Module, VirtualFile>> affected = Arrays.stream(compileContext.getCompileScope().getAffectedModules())
          .map(m -> {
            VirtualFile outputDir = compileContext.getModuleOutputDirectory(m);
            if (outputDir == null) {
              return null;
            } else {
              return Pair.pair(m, outputDir);
            }
          })
          .filter(Objects::nonNull)
          .toList();
      //The index recreates too late, we have to find the generated metadata files without the index.
      new Task.Backgroundable(project, "Reload spring configuration metadata") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          List<VirtualFile> newMetaFiles = affected.stream()
              .map(p -> MetadataFileIndex.findMetaFileInClassRoot(p.getSecond()))
              .filter(Objects::nonNull)
              .toList();
          if (newMetaFiles.isEmpty()) return;
          // Looks like the IDE won't reload the generated metadata file automatically,
          // so we have to refresh it for use by IndexFromOneFile#reSync
          newMetaFiles.forEach(vf -> vf.refresh(true, false));
          for (Pair<Module, VirtualFile> pair : affected) {
            Module module = pair.getFirst();
            indicator.pushState();
            indicator.setText2(module.getName());
            refreshModuleAndDependencies(Collections.singleton(module), newMetaFiles);
            indicator.popState();
          }
        }
      }.queue();
    }


    private void refreshModuleAndDependencies(Iterable<Module> modules, Collection<VirtualFile> additionalMetaFiles) {
      for (Module module : modules) {
        ModuleMetadataServiceImpl mms = (ModuleMetadataServiceImpl) module.getServiceIfCreated(
            ModuleMetadataService.class);
        if (mms != null) {
          mms.refreshMetadata(additionalMetaFiles);
        }
        refreshModuleAndDependencies(ModuleManager.getInstance(project).getModuleDependentModules(module),
            additionalMetaFiles);
      }
    }


    private class IndexUpdater extends Task.Backgroundable {
      private final List<VFileEvent> candidates;


      public IndexUpdater(List<VFileEvent> candidates) {
        super(project, "Reload spring configuration metadata");
        this.candidates = candidates;
      }


      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        DumbService dumbService = DumbService.getInstance(project);
        for (int i = 0; i < candidates.size(); i++) {
          ProgressManager.checkCanceled();
          indicator.setFraction(i * 1.0D / candidates.size());
          VFileEvent event = candidates.get(i);
          VirtualFile added = null, removed = null;
          switch (event) {
            case VFileCreateEvent e -> added = e.getFile();
            case VFileDeleteEvent e -> removed = e.getFile();
            case VFileMoveEvent e -> {
              removed = e.getFile();
              added = e.getNewParent().findChild(e.getFile().getName());
            }
            case VFileCopyEvent e -> added = e.getNewParent().findChild(e.getNewChildName());
            case VFileContentChangeEvent e -> added = e.getFile();
            default -> {continue;}
          }
          if (removed != null && !removed.isValid()) {
            metadataFiles.remove(removed.getUrl()).refresh();
          }
          if (added == null) continue;
          final VirtualFile finalAdded = added;
          indicator.pushState();
          indicator.setText2("Waiting index...");
          if (!dumbService.runReadActionInSmartMode(() -> {
            indicator.popState();
            return MetadataFileIndex.isMetaFile(finalAdded, project);
          })) {
            continue;
          }
          indicator.pushState();
          indicator.setText("Loading " + finalAdded.getPresentableUrl());
          IndexFromOneFile index = getIndex(finalAdded);
          index.refresh();
          if (index.metadata == null) {
            metadataFiles.remove(finalAdded.getUrl());
          } else {
            //File is newly created, we need reload the module that the file belongs to,
            //and all the modules which depends on it.
            //The file creation event maybe covered by the file changed event, so we should refresh modules even if
            //file changed.
            dumbService.runReadActionInSmartMode(() -> {
              Module module = ProjectFileIndex.getInstance(project).getModuleForFile(finalAdded, false);
              if (module != null) {
                refreshModuleAndDependencies(Collections.singleton(module), Collections.emptySet());
              }
            });
          }
          indicator.popState();
        }
      }
    }
  }
}
