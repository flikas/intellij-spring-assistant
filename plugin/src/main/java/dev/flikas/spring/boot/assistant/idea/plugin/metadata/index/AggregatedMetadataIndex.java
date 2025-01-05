package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.project.Project;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.MutableReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AggregatedMetadataIndex implements MetadataIndex {
  private final Deque<MutableReference<? extends MetadataIndex>> indexes = new ConcurrentLinkedDeque<>();


  public AggregatedMetadataIndex() {
  }


  public AggregatedMetadataIndex(MetadataIndex... indexes) {
    for (MetadataIndex index : indexes) {
      addLast(index);
    }
  }


  public void addFirst(MetadataIndex index) {
    addFirst(MutableReference.immutable(index));
  }


  public void addFirst(MutableReference<? extends MetadataIndex> index) {
    this.indexes.addFirst(index);
  }


  public void addLast(MetadataIndex index) {
    addLast(MutableReference.immutable(index));
  }


  public void addLast(MutableReference<? extends MetadataIndex> index) {
    this.indexes.addLast(index);
  }


  /**
   * Refresh all indexes, remove ones that the source file is removed, reload ones that the source file is changed.
   */
  public void refresh() {
    Iterator<MutableReference<? extends MetadataIndex>> iterator = this.indexes.iterator();
    while (iterator.hasNext()) {
      MutableReference<? extends MetadataIndex> ref = iterator.next();
      ref.refresh();
      if (ref.dereference() == null) iterator.remove();
    }
  }


  @Override
  public boolean isEmpty() {
    return getIndexStream().allMatch(MetadataIndex::isEmpty);
  }


  @Override
  public @NotNull Project project() {
    return getIndexStream().map(MetadataIndex::project).reduce((p1, p2) -> {
      if (p1 == p2) {
        return p1;
      } else {
        throw new IllegalStateException("Not the same project");
      }
    }).orElseThrow();
  }


  @Override
  public @NotNull List<MetadataSource> getSource() {
    return getIndexStream()
        .map(MetadataIndex::getSource)
        .flatMap(List::stream)
        .toList();
  }


  @Override
  public @Nullable MetadataGroup getGroup(String name) {
    return getIndexStream().map(index -> index.getGroup(name)).filter(Objects::nonNull).findFirst().orElse(null);
  }


  @Override
  public @NotNull Map<PropertyName, MetadataGroup> getGroups() {
    return getIndexStream().flatMap(index -> index.getGroups().entrySet().stream()).collect(
        Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }


  @Override
  public MetadataProperty getProperty(String name) {
    return getIndexStream()
        .map(index -> index.getProperty(name))
        .filter(Objects::nonNull)
        .findFirst().orElse(null);
  }


  @Override
  public MetadataProperty getNearestParentProperty(String name) {
    return getIndexStream()
        .map(index -> index.getNearestParentProperty(name))
        .filter(Objects::nonNull)
        .findFirst().orElse(null);
  }


  @Override
  public @NotNull Map<PropertyName, MetadataProperty> getProperties() {
    return getIndexStream().flatMap(index -> index.getProperties().entrySet().stream()).collect(
        Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }


  @Override
  public MetadataHint getHint(String name) {
    return getIndexStream()
        .map(index -> index.getHint(name))
        .filter(Objects::nonNull)
        .findFirst().orElse(null);
  }


  @Override
  public @NotNull Map<PropertyName, MetadataHint> getHints() {
    return getIndexStream().flatMap(index -> index.getHints().entrySet().stream()).collect(
        Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }


  @Override
  public MetadataItem getPropertyOrGroup(String name) {
    return getIndexStream()
        .map(index -> index.getPropertyOrGroup(name))
        .filter(Objects::nonNull)
        .findFirst().orElse(null);
  }


  @Override
  public @Nullable NameTreeNode findInNameTrie(String prefix) {
    return getIndexStream()
        .map(index -> index.findInNameTrie(prefix))
        .filter(Objects::nonNull)
        .reduce(NameTreeNode::merge)
        .orElse(null);
  }


  private @NotNull Stream<? extends MetadataIndex> getIndexStream() {
    return indexes.stream()
        .map(MutableReference::dereference)
        .filter(Objects::nonNull);
  }
}
