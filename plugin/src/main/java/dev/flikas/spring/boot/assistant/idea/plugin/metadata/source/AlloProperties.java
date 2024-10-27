package dev.flikas.spring.boot.assistant.idea.plugin.metadata.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AlloProperties extends AbstractMap<String, ConfigurationMetadata.Property> {
  private static final Logger LOG = Logger.getInstance(AlloProperties.class);

  private final ConcurrentMap<String, ConfigurationMetadata.Property> items = new ConcurrentHashMap<>();


  public AlloProperties(VirtualFile source, ConfigurationMetadata.Property property) {
    add(source, property);
  }


  @Override
  public @NotNull Set<Entry<String, ConfigurationMetadata.Property>> entrySet() {
    return items.entrySet();
  }


  public void add(VirtualFile source, ConfigurationMetadata.Property property) {
    ConfigurationMetadata.Property current = items.putIfAbsent(property.getName(), property);
    if (current != null && !current.equals(property)) {
      LOG.warn("Duplicate property '" + property.getName() + "' in file " + source.getPath() + ", ignored");
    }
  }


  public void addAll(VirtualFile source, Iterable<ConfigurationMetadata.Property> properties) {
    for (ConfigurationMetadata.Property property : properties) {
      add(source, property);
    }
  }


  public AlloProperties merge(VirtualFile source, AlloProperties properties) {
    addAll(source, properties.values());
    return this;
  }
}
