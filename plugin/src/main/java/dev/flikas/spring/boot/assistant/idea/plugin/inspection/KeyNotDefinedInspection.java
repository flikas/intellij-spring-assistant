package dev.flikas.spring.boot.assistant.idea.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.NameTreeNode;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLValue;


public class KeyNotDefinedInspection extends YamlInspectionBase {
  @Override
  protected void visitKeyValue(
      @NotNull Module module, @NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder holder, boolean isOnTheFly
  ) {
    if (keyValue.getKey() == null) return;
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);
    String fullName = YAMLUtil.getConfigFullName(keyValue);
    if (StringUtils.isBlank(fullName)) return;
    YAMLValue value = keyValue.getValue();
    if (value instanceof YAMLScalar || value instanceof YAMLSequence || value == null) {
      MetadataProperty property = service.getIndex().getProperty(fullName);
      if (property != null) return;
    } else if (value instanceof YAMLMapping) {
      NameTreeNode tn = service.getIndex().findInNameTrie(fullName);
      if (tn != null) return;
    } else if (value instanceof YAMLAlias) {
      return; //We do not support alias for now
    }
    // Property is not defined, but maybe its parent has a Map<String,String> or Properties type.
    @Nullable MetadataProperty property = service.getIndex().getNearestParentProperty(fullName);
    if (property == null || !property.canBind(fullName)) {
      registerProblem(keyValue, holder);
    }
  }


  private static void registerProblem(@NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder holder) {
    holder.registerProblem(
        keyValue.getKey(),
        YAMLBundle.message("YamlUnknownKeysInspectionBase.unknown.key", keyValue.getKeyText())
    );
  }
}
