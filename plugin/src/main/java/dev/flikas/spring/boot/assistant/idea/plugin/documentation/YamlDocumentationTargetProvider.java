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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;

public class YamlDocumentationTargetProvider implements PsiDocumentationTargetProvider {
  @Override
  public @Nullable DocumentationTarget documentationTarget(
      @NotNull PsiElement element, @Nullable PsiElement originalElement
  ) {
    if (element instanceof MetadataItemVirtualElement mve) {
      return getDocumentationTarget(mve.getMetadataItem());
    } else if (element instanceof HintDocumentationVirtualElement hdve) {
      return hdve;
    }

    if (originalElement != null) element = originalElement;
    if (!PsiElementUtils.isInFileOfType(element, SpringBootConfigurationYamlFileType.INSTANCE)) {
      return null;
    }
    Module module = findModuleForPsiElement(element);
    if (module == null) {
      return null;
    }
    // Find context YAMLKeyValue, stop if context is not at the same line.
    YAMLKeyValue keyValue = PsiTreeUtil.getContextOfType(element, false, YAMLKeyValue.class);
    if (keyValue == null) return null;
    if (!YAMLUtil.psiAreAtTheSameLine(element, keyValue)) return null;

    String propertyName = YAMLUtil.getConfigFullName(keyValue);
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);
    @Nullable MetadataItem propertyOrGroup = service.getIndex().getPropertyOrGroup(propertyName);
    if (propertyOrGroup == null) return null;

    return getDocumentationTarget(propertyOrGroup);
  }


  private static @NotNull DocumentationTarget getDocumentationTarget(@NotNull MetadataItem propertyOrGroup) {
    return switch (propertyOrGroup) {
      case MetadataProperty property -> new PropertyDocumentationTarget(property);
      case MetadataGroup group -> new GroupDocumentationTarget(group);
      default -> throw new IllegalStateException("Unsupported type: " + propertyOrGroup.getClass());
    };
  }
}
