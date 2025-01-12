package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import org.jetbrains.annotations.NotNull;

public interface ModuleMetadataService {

  static ModuleMetadataService getInstance(Module module) {
    return module.getService(ModuleMetadataService.class);
  }

  /**
   * @return Merged spring configuration metadata in this module and its libraries, or {@linkplain MetadataIndex#empty(Project) EMPTY}.
   */
  @NotNull MetadataIndex getIndex();

}
