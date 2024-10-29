package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.diagnostic.Logger;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AlloProperties extends AbstractMap<String, MetadataProperty> implements MetadataProperty {
  private static final Logger LOG = Logger.getInstance(AlloProperties.class);

  private final ConcurrentMap<String, MetadataProperty> items = new ConcurrentHashMap<>();
  @Delegate
  private MetadataProperty mainProperty;


  public AlloProperties(String source, MetadataProperty property) {
    add(source, property);
  }


  @Override
  public @NotNull Set<Entry<String, MetadataProperty>> entrySet() {
    return items.entrySet();
  }


  public void add(@NotNull String source, MetadataProperty property) {
    MetadataProperty current = items.putIfAbsent(property.getName(), property);
    if (current != null && !current.equals(property)) {
      LOG.warn("Duplicate property '" + property.getName() + "' in file " + source + ", ignored");
    } else {
      if (this.mainProperty == null) {
        this.mainProperty = items.get(property.getName());
      } else if (this.mainProperty.getMetadata().getDeprecation() != null) {
        if (property.getMetadata().getDeprecation() == null) {
          this.mainProperty = items.get(property.getName());
        } else {
          LOG.warn("Duplicate property '" + property.getName() + "' & '"
              + this.mainProperty.getName() + "' in file " + source + ", ignored");
        }
      } else if (property.getMetadata().getDeprecation() == null) {
        LOG.warn("Duplicate property '" + property.getName() + "' & '"
            + this.mainProperty.getName() + "' in file " + source + ", ignored");
      }
    }
  }


  public void addAll(String source, Iterable<MetadataProperty> properties) {
    for (MetadataProperty property : properties) {
      add(source, property);
    }
  }


  public AlloProperties merge(String source, AlloProperties properties) {
    addAll(source, properties.values());
    return this;
  }
}
