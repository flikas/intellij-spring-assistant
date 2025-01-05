package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.project.Project;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface MetadataIndex {
  static MetadataIndex empty(Project project) {
    return new Empty(project);
  }

  boolean isEmpty();

  @NotNull Project project();

  /**
   * Source file url or source type FQN, maybe empty string.
   */
  @NotNull List<MetadataSource> getSource();

  @NotNull Map<PropertyName, MetadataGroup> getGroups();

  @NotNull Map<PropertyName, MetadataProperty> getProperties();

  @NotNull Map<PropertyName, MetadataHint> getHints();

  @Nullable MetadataGroup getGroup(String name);

  @Nullable MetadataProperty getProperty(String name);

  @Nullable MetadataProperty getNearestParentProperty(String name);

  @Nullable MetadataHint getHint(String name);

  @Nullable MetadataItem getPropertyOrGroup(String name);

  @Nullable NameTreeNode findInNameTrie(String prefix);

  //region empty implement
  record Empty(Project project) implements MetadataIndex {
    @Override
    public boolean isEmpty() {
      return true;
    }


    @Override
    public @NotNull Project project() {
      return project;
    }


    @Override
    public @NotNull List<MetadataSource> getSource() {
      return Collections.emptyList();
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
    public @Nullable NameTreeNode findInNameTrie(String prefix) {
      return null;
    }
  }
  //endregion
}
