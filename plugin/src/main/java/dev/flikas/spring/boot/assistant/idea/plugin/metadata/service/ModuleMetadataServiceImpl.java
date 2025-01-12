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
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.FileMetadataSource;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.MetadataFileIndex;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

final class ModuleMetadataServiceImpl implements ModuleMetadataService {
  private static final Logger LOG = Logger.getInstance(ModuleMetadataServiceImpl.class);
  private final Project project;
  private final Module module;
  private MetadataIndex index;


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
    refreshMetadata(Collections.emptySet());
  }


  synchronized void refreshMetadata(Collection<VirtualFile> unIndexedMetaFiles) {
    LOG.trace("Try refreshing metadata for module " + this.module.getName());
    @NotNull GlobalSearchScope scope = new ModuleScope(this.module);
    Collection<VirtualFile> files = DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      HashSet<VirtualFile> metafiles = new HashSet<>(MetadataFileIndex.getFiles(scope));
      for (VirtualFile metafile : unIndexedMetaFiles) {
        if (scope.accept(metafile)) metafiles.add(metafile);
      }
      return metafiles;
    });
    if (this.index instanceof AggregatedMetadataIndex aidx) {
      aidx.refresh();
    }
    Set<String> currentFiles = this.index.getSource().stream()
        .filter(FileMetadataSource.class::isInstance)
        .map(s -> ((FileMetadataSource) s).getSource().getUrl())
        .collect(Collectors.toSet());
    if (currentFiles.containsAll(files.stream().map(VirtualFile::getUrl).collect(Collectors.toSet()))) {
      // No new metadata files, can stop here.
      return;
    }
    // Because the MetadataFileIndex may lag of the creation of new metafiles,
    // we only accept new metafiles from the index (but won't remove files even if the index doesn't contain it),
    // the removal of the non-exists ones is done by AggregatedMetadataIndex#refresh()
    files.removeIf(vf -> currentFiles.contains(vf.getUrl()));
    LOG.info("Module \"" + this.module.getName() + "\"'s metadata needs refresh");
    LOG.info("New metadata files: " + files);
    ProjectMetadataService pms = project.getService(ProjectMetadataService.class);
    AggregatedMetadataIndex meta = this.index instanceof AggregatedMetadataIndex
        ? (AggregatedMetadataIndex) this.index
        : new AggregatedMetadataIndex();
    for (VirtualFile file : files) {
      meta.addLast(pms.getIndexForMetaFile(file));
    }
    if (!meta.isEmpty()) {
      this.index = meta;
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
