package dev.flikas.spring.boot.assistant.idea.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata.Property.Deprecation.Level.WARNING;

/**
 * Report deprecated properties whose deprecation level is warning, which means that the property is still be bound in the environment.
 * <p>
 * refer to <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html#appendix.configuration-metadata.format.property">Spring Boot Document</a>
 */
public class PropertyDeprecatedInspection extends PropertyDeprecatedInspectionBase {
  @Override
  protected void foundDeprecatedKey(
      YAMLKeyValue keyValue, MetadataProperty property,
      ConfigurationMetadata.Property.Deprecation deprecation, ProblemsHolder holder,
      boolean isOnTheFly
  ) {
    if (deprecation.getLevel() == null || deprecation.getLevel() == WARNING) {
      assert keyValue.getKey() != null;
      holder.registerProblem(
          keyValue.getKey(),
          "Property \"" + property.getNameStr() + "\" is deprecated."
      );
    }
  }
}
