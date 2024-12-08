package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.diagnostic.Logger;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Aggregate properties which have the same name.
 */
public class HomonymProperties extends AbstractMap<String, MetadataProperty> implements MetadataProperty {
  private static final Logger LOG = Logger.getInstance(HomonymProperties.class);

  private final ConcurrentMap<String, MetadataProperty> items = new ConcurrentHashMap<>();
  @Delegate
  private MetadataProperty mainProperty;


  public HomonymProperties(String source, MetadataProperty property) {
    add(source, property);
  }


  @Override
  public @NotNull Set<Entry<String, MetadataProperty>> entrySet() {
    return items.entrySet();
  }


  public void add(@NotNull String source, MetadataProperty property) {
    MetadataProperty current = items.putIfAbsent(property.getNameStr(), property);
    if (current != null && !current.equals(property)) {
      LOG.warn("Duplicate property " + property.getNameStr() + " in " + source + ", ignored");
    } else if (current == null) {
      if (this.mainProperty == null) {
        this.mainProperty = property;
      } else if (this.mainProperty.getMetadata().getDeprecation() != null
          && property.getMetadata().getDeprecation() == null) {
        this.mainProperty = property;
      } else if (property.getMetadata().getDeprecation() == null) {
        LOG.warn("Duplicate property '" + property.getNameStr() + "' & '"
            + this.mainProperty.getNameStr() + "' in " + source + ", ignored");
      }
    }
  }


  public void addAll(String source, Iterable<MetadataProperty> properties) {
    for (MetadataProperty property : properties) {
      add(source, property);
    }
  }


  public HomonymProperties merge(String source, HomonymProperties properties) {
    addAll(source, properties.values());
    return this;
  }
}
