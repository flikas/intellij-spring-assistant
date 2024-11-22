package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


abstract class MetadataIndexBase implements MetadataIndex {
  private static final Logger LOG = Logger.getInstance(MetadataIndexBase.class);

  protected final Map<PropertyName, MetadataGroupImpl> groups = new HashMap<>();
  protected final Map<PropertyName, MetadataProperty> properties = new HashMap<>();
  protected final Map<PropertyName, MetadataHintImpl> hints = new HashMap<>();
  protected final Project project;


  protected MetadataIndexBase(Project project) {
    this.project = project;
  }


  @Override
  public boolean isEmpty() {
    return properties.isEmpty();
  }


  @Override
  public @NotNull Project getProject() {
    return project;
  }


  @Override
  @Nullable
  public MetadataGroup getGroup(String name) {
    PropertyName key = PropertyName.adapt(name);
    return groups.get(key);
  }


  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public @NotNull Map<PropertyName, MetadataGroup> getGroups() {
    return Collections.unmodifiableMap(groups);
  }


  @Override
  public MetadataProperty getProperty(String name) {
    PropertyName key = PropertyName.adapt(name);
    return properties.get(key);
  }


  @Override
  public MetadataProperty getNearestParentProperty(String name) {
    PropertyName key = PropertyName.adapt(name);
    MetadataProperty property = null;
    while (key != null && !key.isEmpty() && (property = properties.get(key)) == null) {
      key = key.getParent();
    }
    return property;
  }


  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public @NotNull Map<PropertyName, MetadataProperty> getProperties() {
    return Collections.unmodifiableMap(properties);
  }


  @Override
  public MetadataHint getHint(String name) {
    PropertyName key = PropertyName.adapt(name);
    return hints.get(key);
  }


  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public @NotNull Map<PropertyName, MetadataHint> getHints() {
    return Collections.unmodifiableMap(hints);
  }


  @Override
  public MetadataItem getPropertyOrGroup(String name) {
    PropertyName key = PropertyName.adapt(name);
    MetadataItem item = properties.get(key);
    return item != null ? item : groups.get(key);
  }


  protected void add(ConfigurationMetadata.Property p) {
    MetadataPropertyImpl prop = new MetadataPropertyImpl(this, p);
    PropertyName key = PropertyName.of(p.getName());
    MetadataProperty old = this.properties.put(key, prop);
    if (old != null) {
      if (old instanceof AlloProperties allo) {
        allo.add(getSource(), prop);
      } else {
        if (!old.getMetadata().equals(p)) {
          AlloProperties allo = new AlloProperties(getSource(), old);
          allo.add(getSource(), prop);
          this.properties.put(key, allo);
        }
      }
    }
  }


  protected void add(ConfigurationMetadata.Group g) {
    MetadataGroupImpl old = this.groups.put(PropertyName.of(g.getName()), new MetadataGroupImpl(this, g));
    if (old != null && !old.getMetadata().equals(g)) {
      LOG.warn("Duplicate group " + g.getName() + " in " + getSource() + ", ignored");
    }
  }


  protected void add(ConfigurationMetadata.Hint h) {
    MetadataHintImpl old = this.hints.put(PropertyName.of(h.getName()), new MetadataHintImpl(h));
    if (old != null && !old.getMetadata().equals(h)) {
      LOG.warn("Duplicate hint " + h.getName() + " in " + getSource() + ", ignored");
    }
  }


  protected void add(String source, @NotNull ConfigurationMetadata metadata) {
    if (metadata.isEmpty()) return;

    if (metadata.getGroups() != null) {
      metadata.getGroups().forEach(g -> {
        try {
          add(g);
        } catch (Exception e) {
          LOG.warn("Invalid group " + g.getName() + " in " + source + ", skipped", e);
        }
      });
    }
    if (metadata.getHints() != null) {
      metadata.getHints().forEach(h -> {
        try {
          add(h);
        } catch (Exception e) {
          LOG.warn("Invalid hint " + h.getName() + " in " + source + ", skipped", e);
        }
      });
    }
    metadata.getProperties().forEach(p -> {
      try {
        add(p);
      } catch (Exception e) {
        LOG.warn("Invalid property " + p.getName() + " in " + source + ", skipped", e);
      }
    });
  }
}
