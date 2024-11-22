package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.project.Project;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface MetadataIndex {
  static MetadataIndex empty(Project project) {
    return new MetadataIndex() {
      //region empty implements
      @Override
      public boolean isEmpty() {
        return true;
      }


      @Override
      public @NotNull Project getProject() {
        return project;
      }


      @Override
      public @NotNull String getSource() {
        return "";
      }


      @Override
      public @Nullable MetadataGroup getGroup(String name) {
        return null;
      }


      @Override
      public MetadataProperty getProperty(String name) {
        return null;
      }


      @Override
      public MetadataProperty getNearestParentProperty(String name) {
        return null;
      }


      @Override
      public MetadataHint getHint(String name) {
        return null;
      }


      @Override
      public @NotNull Map<PropertyName, MetadataGroup> getGroups() {
        return Map.of();
      }


      @Override
      public @NotNull Map<PropertyName, MetadataProperty> getProperties() {
        return Map.of();
      }


      @Override
      public @NotNull Map<PropertyName, MetadataHint> getHints() {
        return Map.of();
      }


      @Override
      public MetadataItem getPropertyOrGroup(String name) {
        return null;
      }


      @Override
      public @NotNull Map<PropertyName, MetadataItem> findPropertyOrGroupByPrefix(String prefix) {
        return Map.of();
      }
      //endregion
    };
  }

  boolean isEmpty();

  @NotNull Project getProject();

  /**
   * Source file url or source type FQN, maybe empty string.
   */
  @NotNull String getSource();

  @NotNull Map<PropertyName, MetadataGroup> getGroups();

  @NotNull Map<PropertyName, MetadataProperty> getProperties();

  @NotNull Map<PropertyName, MetadataHint> getHints();

  @Nullable MetadataGroup getGroup(String name);

  @Nullable MetadataProperty getProperty(String name);

  @Nullable MetadataProperty getNearestParentProperty(String name);

  @Nullable MetadataHint getHint(String name);

  @Nullable MetadataItem getPropertyOrGroup(String name);

  @NotNull Map<PropertyName, MetadataItem> findPropertyOrGroupByPrefix(String prefix);
}
