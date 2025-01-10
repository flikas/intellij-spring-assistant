package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.MetadataFileIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class CompilationListener implements CompilationStatusListener {
  private final Project project;


  CompilationListener(Project project) {
    this.project = project;
  }


  @Override
  public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
    List<Pair<com.intellij.openapi.module.Module, VirtualFile>> affected = Arrays.stream(
            compileContext.getCompileScope().getAffectedModules())
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
    new Task.Backgroundable(project, "Reloading spring configuration metadata") {
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
        for (Pair<com.intellij.openapi.module.Module, VirtualFile> pair : affected) {
          com.intellij.openapi.module.Module module = pair.getFirst();
          indicator.pushState();
          indicator.setText2(module.getName());
          refreshModuleAndDependencies(Collections.singleton(module), newMetaFiles);
          indicator.popState();
        }
      }
    }.queue();
  }


  private void refreshModuleAndDependencies(
      Iterable<com.intellij.openapi.module.Module> modules, Collection<VirtualFile> additionalMetaFiles) {
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
}
