package dev.flikas.spring.boot.assistant.idea.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata.Property.Deprecation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLValue;

public abstract class PropertyDeprecatedInspectionBase extends YamlInspectionBase {
  @Override
  protected void visitKeyValue(
      @NotNull Module module, @NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder holder, boolean isOnTheFly
  ) {
    YAMLValue yamlValue = keyValue.getValue();
    if (yamlValue == null) return;
    if (yamlValue instanceof YAMLAlias) return; //TODO Support YAML alias.

    String propertyName = YAMLUtil.getConfigFullName(keyValue);
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);
    MetadataProperty property = service.getIndex().getProperty(propertyName);
    if (property == null) return;

    if (yamlValue instanceof YAMLMapping && !property.isMapType()) {
      // Property isValid, its value in YAML is a mapping, but the property's type is not a Map: this may happen on
      // property deprecation, for example, "spring.profiles" & "spring.profiles.active/group/include/...".
      // If it happens, we should only prompt deprecation while the actual value type coincides with the property's type.
      return;
    }
    Deprecation deprecation = property.getMetadata().getDeprecation();
    if (deprecation != null) {
      foundDeprecatedKey(keyValue, property, deprecation, holder, isOnTheFly);
    }
  }


  protected abstract void foundDeprecatedKey(
      YAMLKeyValue keyValue, MetadataProperty property, Deprecation deprecation,
      ProblemsHolder holder, boolean isOnTheFly
  );
}
