package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.project.Project;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataItem;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ModuleMetadataService {
  /**
   * @return Merged spring configuration metadata in this module and its libraries, or {@linkplain MetadataIndex#empty(Project) EMPTY}.
   */
  @NotNull MetadataIndex getIndex();

  /**
   * Retrieve candidates for completion.
   *
   * @param parentName  The context property name for querying, must be existed, such as 'spring.security', can be null or empty
   * @param queryString The user input for completion,
   * @return Collection of {@link MetadataProperty} or {@link MetadataGroup} that matches the query.
   */
  @NotNull Collection<MetadataItem> findSuggestionForCompletion(@Nullable String parentName, String queryString);
}
