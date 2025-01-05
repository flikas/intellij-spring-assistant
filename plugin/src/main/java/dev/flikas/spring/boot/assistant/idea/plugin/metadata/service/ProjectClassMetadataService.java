package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.AggregatedMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.ConfigurationMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils.getCanonicalTextOfType;
import static java.util.function.Predicate.not;

/**
 * Service that provides {@link MetadataIndex} from {@link MetadataProperty#getFullType()}.
 * <p>
 * And watch for update continuously.
 */
@Service(Service.Level.PROJECT)
final class ProjectClassMetadataService implements Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectClassMetadataService.class);

  private final Project project;


  public ProjectClassMetadataService(Project project) {
    this.project = project;
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      //TODO Watch Psi changes
      //TODO Watch class's modification & update metadata
    }, this);
  }


  @NotNull
  public Optional<MetadataIndex> getMetadata(@NotNull String baseName, @NotNull PsiType type) {
    if (PsiTypeUtils.isValueType(type)) {
      return Optional.empty();
    }
    return Optional.of(generateMetadataInSmartMode(new AggregatedMetadataIndex(), PropertyName.of(baseName), type))
        .filter(not(MetadataIndex::isEmpty));
  }


  @NotNull
  private MetadataIndex generateMetadataInSmartMode(
      AggregatedMetadataIndex index, PropertyName basename, PsiType type
  ) {
    return DumbService.getInstance(project).runReadActionInSmartMode(() -> generateMetadata(index, basename, type));
  }


  @NotNull
  private MetadataIndex generateMetadata(AggregatedMetadataIndex index, PropertyName basename, PsiType type) {
    LOG.debug("Generating metadata for: " + basename + " -> " + type.getPresentableText());
    if (PsiTypeUtils.isValueType(type)) {
      // Exit condition: value type do not need to index.
      return index;
    }
    if (PsiTypeUtils.isMap(project, type)) {
      try {
        PsiType[] kvType = PsiTypeUtils.getKeyValueType(project, type);
        if (!(kvType != null && kvType.length == 2)) {
          LOG.warn("Unsupported map type: " + type);
          return index;
        }
        if (!PsiTypeUtils.isValueType(kvType[0])) {
          LOG.warn(basename + " has unsupported Map key type: " + type);
          return index;
        }
        generateMetadata(index, basename.appendAnyMapKey(), kvType[1]);
      } catch (Exception e) {
        LOG.warn(basename + " has illegal Map type: " + type);
        return index;
      }
    } else if (PsiTypeUtils.isCollection(project, type)) {
      try {
        PsiType elementType = PsiTypeUtils.getElementType(project, type);
        assert elementType != null;
        generateMetadata(index, basename.appendAnyNumericalIndex(), elementType);
      } catch (Exception e) {
        LOG.warn(basename + " has illegal Collection type: " + type);
        return index;
      }
    } else {
      PsiClass valueClass = PsiTypeUtils.resolveClassInType(type);
      if (valueClass == null) return index;
      ConfigurationMetadata metadata = new ConfigurationMetadata();
      String[] writableProperties = PropertyUtil.getWritableProperties(valueClass, true);
      for (String fieldName : writableProperties) {
        PsiField field = valueClass.findFieldByName(fieldName, true);
        if (field == null) continue;
        PropertyName name = basename.append(PropertyName.toKebabCase(fieldName));
        ConfigurationMetadata.Property meta = new ConfigurationMetadata.Property();
        meta.setName(name.toString());
        PsiType propertyType = PropertyUtil.getPropertyType(field);
        if (propertyType instanceof PsiPrimitiveType primitiveType) {
          propertyType = primitiveType.getBoxedType(field);
        }
        if (propertyType == null) continue;
        if (PsiTypeUtils.isValueType(propertyType)) {
          // Leaf property, whose value can be converted to/from a single string
          meta.setType(getCanonicalTextOfType(propertyType));
          meta.setSourceType(valueClass.getQualifiedName());
          PsiExpression initializer = field.getInitializer();
          if (initializer instanceof PsiLiteralExpression literal) {
            meta.setDefaultValue(literal.getValue());
          }
          metadata.getProperties().add(meta);
        } else {
          // Nested class, recursive in.
          generateMetadata(index, name, propertyType);
        }
      }
      index.addLast(new ConfigurationMetadataIndex(metadata, valueClass, project));
    }
    return index;
  }


  @Override
  public void dispose() {
  }
}
