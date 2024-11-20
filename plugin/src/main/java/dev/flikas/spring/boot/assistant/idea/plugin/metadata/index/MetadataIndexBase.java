package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiMethodUtils;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form.DASHED;


abstract class MetadataIndexBase implements MetadataIndex {
  private static final Logger LOG = Logger.getInstance(MetadataIndexBase.class);

  protected final Map<PropertyName, Group> groups = new HashMap<>();
  protected final Map<PropertyName, MetadataProperty> properties = new HashMap<>();
  protected final Map<PropertyName, Hint> hints = new HashMap<>();
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
    Property prop = new Property(p);
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
    Group old = this.groups.put(PropertyName.of(g.getName()), new Group(g));
    if (old != null && !old.getMetadata().equals(g)) {
      LOG.warn("Duplicate group " + g.getName() + " in " + getSource() + ", ignored");
    }
  }


  protected void add(ConfigurationMetadata.Hint h) {
    Hint old = this.hints.put(PropertyName.of(h.getName()), new Hint(h));
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


  @EqualsAndHashCode(of = "metadata")
  @ToString(of = "metadata")
  protected class Property implements MetadataProperty {
    @Getter
    private final ConfigurationMetadata.Property metadata;
    @Getter(AccessLevel.PROTECTED)
    private final PropertyName propertyName;
    private final PsiType propertyType;


    public Property(ConfigurationMetadata.Property metadata) {
      this.metadata = metadata;
      this.propertyName = PropertyName.of(metadata.getName());
      if (StringUtils.isBlank(metadata.getType())) {
        this.propertyType = null;
      } else {
        PsiJavaParserFacade parser = JavaPsiFacade.getInstance(project).getParserFacade();
        // When reference an inner class, we should use A.B not A$B but spring does.
        String typeString = metadata.getType().replace('$', '.');
        this.propertyType = ReadAction.compute(() -> {
          PsiType t = parser.createTypeFromText(typeString, null);
          return PsiTypeUtils.isPhysical(t) ? t : null;
        });
      }
    }


    @Override
    @NotNull
    public String getName() {
      return propertyName.toString();
    }


    @Override
    public Optional<PsiClass> getType() {
      return Optional.ofNullable(this.propertyType).map(PsiTypeUtils::resolveClassInType);
    }


    @Override
    public Optional<PsiClass> getSourceType() {
      return Optional.ofNullable(metadata.getSourceType())
          .filter(StringUtils::isNotBlank)
          .map(type -> PsiTypeUtils.findClass(project, type));
    }


    @Override
    public MetadataIndex getIndex() {
      return MetadataIndexBase.this;
    }


    @Override
    public Optional<PsiType> getFullType() {
      return Optional.ofNullable(this.propertyType).filter(t -> ReadAction.compute(t::isValid));
    }


    @Override
    public Optional<PsiField> getSourceField() {
      return getSourceType().map(type -> type.findFieldByName(getCamelCaseLastName(), true));
    }


    @Override
    public Optional<MetadataHint> getHint() {
      return Optional.ofNullable(hints.getOrDefault(propertyName, hints.get(propertyName.append("values"))));
    }


    @Override
    public Optional<MetadataHint> getKeyHint() {
      return Optional.ofNullable(hints.get(propertyName.append("keys")));
    }


    @Override
    public boolean canBind(@NotNull String key) {
      PropertyName keyName = PropertyName.adapt(key);
      return this.propertyName.equals(keyName)
          // A Map property can bind all sub-key-values.
          || this.propertyName.isAncestorOf(keyName) && PsiTypeUtils.isValueMap(project, getFullType().orElse(null));
    }


    private String getCamelCaseLastName() {
      return PropertyName.toCamelCase(propertyName.getLastElement(DASHED));
    }
  }


  @SuppressWarnings("LombokGetterMayBeUsed")
  @RequiredArgsConstructor
  @EqualsAndHashCode(of = "metadata")
  @ToString(of = "metadata")
  protected class Group implements MetadataGroup {
    @Getter
    private final ConfigurationMetadata.Group metadata;


    @Override
    public @NotNull String getName() {
      return metadata.getName();
    }


    /**
     * @see ConfigurationMetadata.Group#getType()
     */
    @Override
    public Optional<PsiClass> getType() {
      return Optional.ofNullable(metadata.getType())
          .filter(StringUtils::isNotBlank)
          .map(type -> PsiTypeUtils.findClass(project, type));
    }


    /**
     * @see ConfigurationMetadata.Group#getSourceType()
     */
    @Override
    public Optional<PsiClass> getSourceType() {
      return Optional.ofNullable(metadata.getSourceType())
          .filter(StringUtils::isNotBlank)
          .map(type -> PsiTypeUtils.findClass(project, type));
    }


    @Override
    public MetadataIndex getIndex() {
      return MetadataIndexBase.this;
    }


    /**
     * @see ConfigurationMetadata.Group#getSourceMethod()
     */
    @Override
    public Optional<PsiMethod> getSourceMethod() {
      String sourceMethod = metadata.getSourceMethod();
      if (StringUtils.isBlank(sourceMethod)) return Optional.empty();
      return getSourceType().flatMap(sourceClass -> PsiMethodUtils.findMethodBySignature(sourceClass, sourceMethod));
    }
  }


  @SuppressWarnings("LombokGetterMayBeUsed")
  @RequiredArgsConstructor
  @EqualsAndHashCode(of = "metadata")
  @ToString(of = "metadata")
  protected class Hint implements MetadataHint {
    @Getter
    private final ConfigurationMetadata.Hint metadata;
  }
}
