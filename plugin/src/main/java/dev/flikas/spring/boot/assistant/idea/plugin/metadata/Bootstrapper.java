package dev.flikas.spring.boot.assistant.idea.plugin.metadata;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.ProjectScope;
import dev.flikas.spring.boot.assistant.idea.plugin.filetype.SpringBootConfigurationYamlFileType;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Bootstrapper implements StartupActivity {
  private static final Logger LOG = Logger.getInstance(Bootstrapper.class);


  @Override
  public void runActivity(@NotNull Project project) {
    DumbService.getInstance(project).runWhenSmart(() ->
        new Task.Backgroundable(project, "Loading spring configuration metadata") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            StopWatch stopWatch = StopWatch.createStarted();
            indicator.setIndeterminate(true);
            indicator.setText2("Waiting index...");
            Set<Module> modules = DumbService.getInstance(project).runReadActionInSmartMode(() -> {
              indicator.setText2("");
              Set<Module> candidates = new HashSet<>();
              FileTypeIndex.processFiles(SpringBootConfigurationYamlFileType.INSTANCE, vf -> {
                candidates.add(ProjectFileIndex.getInstance(project).getModuleForFile(vf));
                return true;
              }, ProjectScope.getAllScope(project));
              return candidates;
            });
            LOG.info(
                "Bootstrap for modules: " + modules.stream().map(Module::getName).collect(Collectors.joining(",")));
            indicator.setIndeterminate(false);
            int i = 0;
            for (Module module : modules) {
              ProgressManager.checkCanceled();
              indicator.setFraction(i++ * 1.0d / modules.size());
              indicator.setText2("For module " + module.getName());
              module.getService(ModuleMetadataService.class).getIndex();
            }
            stopWatch.stop();
            LOG.info("Bootstrap takes " + stopWatch.getTime() + "ms");
          }
        }.queue());
  }
}
