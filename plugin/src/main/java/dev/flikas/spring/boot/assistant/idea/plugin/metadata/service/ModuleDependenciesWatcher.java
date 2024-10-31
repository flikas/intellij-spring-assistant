package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.ImmutableEntityStorage;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridges;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class ModuleDependenciesWatcher implements WorkspaceModelChangeListener {
  private final Project project;


  public ModuleDependenciesWatcher(Project project) {
    this.project = project;
  }


  @Override
  public void changed(@NotNull VersionedStorageChange event) {
    List<EntityChange.Replaced<ModuleEntity>> interested = new ArrayList<>();
    List<EntityChange<ModuleEntity>> changes = event.getChanges(ModuleEntity.class);
    for (EntityChange<ModuleEntity> change : changes) {
      if (change instanceof EntityChange.Replaced<ModuleEntity> replaced) {
        if (!change.getOldEntity().getDependencies().equals(change.getNewEntity().getDependencies())) {
          interested.add(replaced);
        }
      }
    }
    if (!interested.isEmpty()) {
      new Task.Backgroundable(project, "Reloading spring configuration metadata") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          ImmutableEntityStorage workspace = WorkspaceModel.getInstance(project).getCurrentSnapshot();
          for (int i = 0; i < interested.size(); i++) {
            ProgressManager.checkCanceled();
            indicator.setFraction(i * 1.0d / interested.size());
            EntityChange.Replaced<ModuleEntity> replaced = interested.get(i);
            Module module = ModuleBridges.findModule(replaced.getOldEntity(), workspace);
            if (module == null) continue;
            @Nullable ModuleMetadataService svc = module.getServiceIfCreated(ModuleMetadataService.class);
            if (svc instanceof ModuleMetadataServiceImpl impl) {
              indicator.setText2("For module " + module.getName());
              impl.refreshMetadata();
              indicator.setText2("");
            }
          }
        }
      }.queue();
    }
  }
}
