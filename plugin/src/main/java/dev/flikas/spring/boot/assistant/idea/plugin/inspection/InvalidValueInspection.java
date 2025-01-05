package dev.flikas.spring.boot.assistant.idea.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtilCore;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLCompoundValue;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;

import java.util.Optional;
import java.util.regex.Pattern;

public class InvalidValueInspection extends YamlInspectionBase {
  private static final Logger log = Logger.getInstance(InvalidValueInspection.class);
  private static final Pattern SPRING_BOOT_PLACEHOLDER_EXPRESSION = Pattern.compile("\\$\\{.+}");
  private static final Pattern SPRING_BOOT_PLACEHOLDER_ONLY_EXPRESSION = Pattern.compile("\\$\\{[^:]+}");
  private static final ConversionService springConversionService = new ApplicationConversionService();


  @Override
  protected void visitKeyValue(
      @NotNull Module module, @NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder holder, boolean isOnTheFly
  ) {
    YAMLValue yamlValue = keyValue.getValue();
    if (yamlValue == null) return;
    if (yamlValue instanceof YAMLAlias) return; //TODO Support alias.
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);

    String propertyName = YAMLUtil.getConfigFullName(keyValue);
    MetadataProperty property = service.getIndex().getProperty(propertyName);
    if (property == null) {
      // It is not a leaf node, or there should be a KeyNotDefined problem. In any case, we do not have to report any problems here.
      return;
    }
    Optional<String> valueType = property.getType().map(PsiClass::getQualifiedName);
    if (valueType.isEmpty()) return;

    Project project = yamlValue.getProject();
    PsiType type = property.getFullType().orElseThrow();
    switch (yamlValue) {
      case YAMLScalar scalar -> {
        // A YAML scalar can map to a scalar value or a list of one element.
        PsiType elementType;
        if (PsiTypeUtils.isCollection(project, type)) {
          elementType = PsiTypeUtils.getElementType(project, type);
        } else if (PsiTypeUtils.isValueType(type)) {
          elementType = type;
        } else {
          holder.registerProblem(
              yamlValue,
              "Property '" + propertyName + "' has an invalid value, here should be a yaml mapping of type '"
                  + type.getPresentableText() + "'");
          return;
        }
        assert elementType != null;
        validateValue(scalar, elementType.getCanonicalText(), holder);
      }
      case YAMLSequence sequenceValue -> {
        if (!PsiTypeUtils.isCollection(project, type)) {
          holder.registerProblem(
              yamlValue,
              "Property '" + propertyName + "' has an invalid value, here should be a value of type '"
                  + type.getPresentableText() + "'");
          return;
        }
        PsiType elementType = PsiTypeUtils.getElementType(project, type);
        assert elementType != null;
        if (!PsiTypeUtils.isValueType(elementType)) {
          // There should be a more specific call to validate its children.
          // TODO Support List<List<...>>
          return;
        }
        for (YAMLSequenceItem item : sequenceValue.getItems()) {
          @Nullable YAMLValue value = item.getValue();
          if (value instanceof YAMLScalar scalar) {
            validateValue(scalar, elementType.getCanonicalText(), holder);
          } else if (value instanceof YAMLCompoundValue) {
            holder.registerProblem(
                value,
                "Property '" + propertyName + "' has an invalid value, here should be a yaml scalar of type '"
                    + elementType.getPresentableText() + "'");
          }
          //TODO Support YAMLAlias.
        }
      }
      case YAMLMapping mappingValue -> {
        if (!PsiTypeUtils.isMap(project, type)) {
          // Property isValid, its value in YAML is a mapping, but the property's type is not a Map: this may happen on
          // property deprecation, for example, "spring.profiles" & "spring.profiles.active/group/include/...".
          // If it happens, we should only validate values while the actual value type coincides with the property's type.
          return;
        }
        PsiType[] kvType = PsiTypeUtils.getKeyValueType(project, type);
        assert kvType != null && kvType.length == 2;
        // Key is not a value type: Unsupported.
        boolean canValidateKey = PsiTypeUtils.isValueType(kvType[0]);
        // Value is not a value type: There should be a more specific call to validate its children.
        boolean canValidateValue = PsiTypeUtils.isValueType(kvType[1]);
        mappingValue.acceptChildren(new YamlPsiElementVisitor() {
          @Override
          public void visitKeyValue(@NotNull YAMLKeyValue kv) {
            @Nullable YAMLValue value = kv.getValue();
            if (value instanceof YAMLScalar scalar) {
              if (canValidateKey) {
                String keyText = YAMLUtil.getConfigFullName(kv).substring(propertyName.length() + 1);
                validateValue(kv.getKey(), keyText, kvType[0].getCanonicalText(), holder);
              }
              if (canValidateValue) {
                validateValue(scalar, kvType[1].getCanonicalText(), holder);
              }
            } else {
              super.visitKeyValue(kv);
            }
          }
        });
      }
      default -> log.warn("Unsupported yaml node type: " + PsiUtilCore.getElementType(yamlValue));
    }
  }


  private void validateValue(YAMLScalar yamlValue, String valueTypeClass, ProblemsHolder holder) {
    validateValue(yamlValue, yamlValue.getTextValue(), valueTypeClass, holder);
  }


  private void validateValue(
      PsiElement propertyElement, String propertyValue, String valueTypeClass, ProblemsHolder holder
  ) {
    if (isValueSpringBootPlaceholderExpression(propertyValue)) {
      if (isValueSpringBootPlaceholderOnlyExpression(propertyValue)) {
        return;
      }

      int startIndex = propertyValue.indexOf(':') + 1;
      int endIndex = propertyValue.lastIndexOf('}');
      propertyValue = propertyValue.substring(startIndex, endIndex);
    }

    try {
      Class<?> valueClass = Class.forName(valueTypeClass);
      springConversionService.convert(propertyValue, valueClass);
    } catch (ClassNotFoundException e) {
      log.warn(InvalidValueInspection.class.getSimpleName() + ":: Cannot load value class " + valueTypeClass, e);
    } catch (ConversionException e) {
      holder.registerProblem(
          propertyElement,
          "Value \"" + propertyValue + "\" cannot be converted to: " + valueTypeClass);
    }
  }


  private boolean isValueSpringBootPlaceholderExpression(String value) {
    return SPRING_BOOT_PLACEHOLDER_EXPRESSION.matcher(value).matches();
  }


  private boolean isValueSpringBootPlaceholderOnlyExpression(String value) {
    return SPRING_BOOT_PLACEHOLDER_ONLY_EXPRESSION.matcher(value).matches();
  }
}
