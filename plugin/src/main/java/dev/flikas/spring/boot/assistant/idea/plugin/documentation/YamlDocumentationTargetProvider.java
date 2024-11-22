package dev.flikas.spring.boot.assistant.idea.plugin.documentation;

import com.intellij.openapi.module.Module;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.flikas.spring.boot.assistant.idea.plugin.filetype.SpringBootConfigurationYamlFileType;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataItem;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiElementUtils;
import in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collections;
import java.util.List;

public class YamlDocumentationTargetProvider implements PsiDocumentationTargetProvider {
  @Override
  public @NotNull List<@NotNull DocumentationTarget> documentationTargets(
      @NotNull PsiElement element, @Nullable PsiElement originalElement
  ) {
    if (originalElement != null) element = originalElement;
    if (!PsiElementUtils.isInFileOfType(element, SpringBootConfigurationYamlFileType.INSTANCE)) {
      return Collections.emptyList();
    }
    Module module = PsiCustomUtil.findModule(element);
    if (module == null) {
      return Collections.emptyList();
    }
    // Find context YAMLKeyValue, stop if context is not at the same line.
    YAMLKeyValue keyValue = PsiTreeUtil.getContextOfType(element, false, YAMLKeyValue.class);
    if (keyValue == null) return Collections.emptyList();
    if (!YAMLUtil.psiAreAtTheSameLine(element, keyValue)) return Collections.emptyList();

    String propertyName = YAMLUtil.getConfigFullName(keyValue);
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);
    @Nullable MetadataItem propertyOrGroup = service.getIndex().getPropertyOrGroup(propertyName);
    if (propertyOrGroup == null) return Collections.emptyList();

    return switch (propertyOrGroup) {
      case MetadataProperty property -> List.of(new PropertyDocumentationTarget(property));
      case MetadataGroup group -> List.of(new GroupDocumentationTarget(group));
      default -> throw new IllegalStateException("Unsupported type: " + propertyOrGroup.getClass());
    };
  }
}
