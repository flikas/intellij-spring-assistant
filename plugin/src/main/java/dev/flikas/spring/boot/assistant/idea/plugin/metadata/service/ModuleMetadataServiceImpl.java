package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.AggregatedMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.MetadataFileIndex;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

final class ModuleMetadataServiceImpl implements ModuleMetadataService {
  private static final Logger LOG = Logger.getInstance(ModuleMetadataServiceImpl.class);
  private final Project project;
  private final Module module;
  private MetadataIndex index;
  private Set<String> metaFilesUrlSnapshot = new HashSet<>();


  public ModuleMetadataServiceImpl(Module module) {
    this.module = module;
    this.project = module.getProject();
    this.index = this.project.getService(ProjectMetadataService.class).getEmptyIndex();
    // read metadata for the first time
    refreshMetadata();
  }


  @Override
  public @NotNull MetadataIndex getIndex() {
    return index;
  }


  synchronized void refreshMetadata() {
    LOG.trace("Try refreshing metadata for module " + this.module.getName());
    @NotNull GlobalSearchScope scope = new ModuleScope(this.module);
    VirtualFile[] files = DumbService.getInstance(project).runReadActionInSmartMode(() ->
        MetadataFileIndex.getFiles(scope).toArray(VirtualFile[]::new));
    Set<String> filesUrl = Arrays.stream(files).map(VirtualFile::getUrl).collect(Collectors.toSet());

    if (metaFilesUrlSnapshot.equals(filesUrl)) {
      // No dependency changed, no need to refresh metadata.
      return;
    }
    LOG.info("Module \"" + this.module.getName() + "\"'s metadata needs refresh");
    LOG.info("Class root candidates: " + Arrays.toString(files));
    ProjectMetadataService pms = project.getService(ProjectMetadataService.class);
    AggregatedMetadataIndex meta = new AggregatedMetadataIndex();
    for (VirtualFile file : files) {
      meta.addLast(pms.getIndexForMetaFile(file));
    }
    if (!meta.isEmpty()) {
      this.index = meta;
      this.metaFilesUrlSnapshot = filesUrl;
    }
  }


  private static class ModuleScope extends GlobalSearchScope {
    private final ProjectFileIndex projectFileIndex;
    private final Object2IntMap<VirtualFile> roots;
    private final Set<Module> dependencies;
    private final @NotNull Project project;


    private ModuleScope(Module module) {
      super(module.getProject());
      project = module.getProject();
      this.projectFileIndex = ProjectFileIndex.getInstance(project);
      VirtualFile[] classRoots = OrderEnumerator
          .orderEntries(module)
          .recursively()
          .withoutDepModules()
          .withoutSdk()
          .classes()
          .getRoots();
      Object2IntOpenHashMap<VirtualFile> map = new Object2IntOpenHashMap<>(classRoots.length);
      int i = 0;
      for (VirtualFile root : classRoots) {
        map.put(root, i++);
      }
      this.roots = map;
      Set<Module> modules = new HashSet<>();
      OrderEnumerator.orderEntries(module).recursively().forEachModule(modules::add);
      this.dependencies = modules;
    }


    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }


    @Override
    public boolean isSearchInLibraries() {
      return true;
    }


    @Override
    public boolean contains(@NotNull VirtualFile file) {
      VirtualFile root = projectFileIndex.getClassRootForFile(file);
      if (root != null) {
        return roots.containsKey(root);
      } else {
        Module module = projectFileIndex.getModuleForFile(file, false);
        return this.dependencies.contains(module);
      }
    }
  }
}
