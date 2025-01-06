package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.MutableReference;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.openapi.compiler.CompilerTopics.COMPILATION_STATUS;

/**
 * Service that generates {@link MetadataIndex} from one {@linkplain ModuleRootModel#getSourceRoots() SourceRoot}.
 * <p>
 * It searches and generate index from Spring Configuration Files ({@value METADATA_FILE}, {@value ADDITIONAL_METADATA_FILE})
 * in the source root and watches them for automatically update the index.
 */
@Service(Service.Level.PROJECT)
final class ProjectMetadataService implements Disposable {
  private final Project project;
  private final ConcurrentMap<String, MetadataFileContainer> metadataFiles = new ConcurrentHashMap<>();
  @Getter private final MetadataIndex emptyIndex;


  public ProjectMetadataService(Project project) {
    this.project = project;
    this.emptyIndex = MetadataIndex.empty(this.project);
    project.getMessageBus().connect().subscribe(COMPILATION_STATUS, new CompilationListener(project));
    project.getMessageBus().connect().subscribe(WorkspaceModelTopics.CHANGED, new ModuleDependenciesWatcher(project));
  }


  public MutableReference<MetadataIndex> getIndexForMetaFile(@NotNull VirtualFile metadataFile) {
    return getIndex(metadataFile);
  }


  @Override
  public void dispose() {
    // This is a parent disposable for FileWatcher.
  }


  private MetadataFileContainer getIndex(@NotNull VirtualFile metadataFile) {
    return metadataFiles.computeIfAbsent(metadataFile.getUrl(),
        url -> new MetadataFileContainer(metadataFile, project));
  }
}
