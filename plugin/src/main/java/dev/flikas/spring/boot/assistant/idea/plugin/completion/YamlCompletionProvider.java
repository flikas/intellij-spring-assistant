package dev.flikas.spring.boot.assistant.idea.plugin.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataItem;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.handler.YamlKeyInsertHandler;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.YAMLSequence;

import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static java.util.Objects.requireNonNull;

class YamlCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(
      @NotNull final CompletionParameters completionParameters,
      @NotNull final ProcessingContext processingContext,
      @NotNull final CompletionResultSet resultSet
  ) {
    PsiElement element = completionParameters.getPosition();
    if (element instanceof PsiComment) {
      return;
    }
    Project project = element.getProject();
    if (ReadAction.compute(() -> DumbService.isDumb(project))) {
      return;
    }
    Module module = findModule(element);
    if (module == null) {
      return;
    }

    PsiElement elementContext = element.getContext();
    PsiElement parent = requireNonNull(elementContext).getParent();
    if (parent instanceof YAMLSequence) {
      // let's force user to create array element prefix before he can ask for suggestions
      return;
    }

    // Find context YAMLKeyValue, stop if context is not at the same line.
    @Nullable YAMLPsiElement context = PsiTreeUtil.getContextOfType(element, false, YAMLPsiElement.class);
    if (context == null) return;
    if (!YAMLUtil.psiAreAtTheSameLine(element, context)) return;

    String propertyName = context.getText();
    String ancestorKeys = YAMLUtil.getConfigFullName(context);
    if (StringUtils.isNotBlank(ancestorKeys)) {
      propertyName = ancestorKeys + "." + propertyName;
    }

    propertyName = StringUtils.removeEnd(propertyName.trim(), CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);

    for (MetadataItem metaItem : service.getIndex().findPropertyOrGroupByPrefix(propertyName)) {
      LookupElement le = switch (metaItem) {
        case MetadataProperty property -> createLookupElement(property);
        case MetadataGroup group -> createLookupElement(group);
        default -> throw new IllegalStateException("Unexpected value: " + metaItem);
      };
      if (le != null) resultSet.addElement(le);
    }
  }


  private LookupElement createLookupElement(MetadataProperty property) {
    ConfigurationMetadata.Property.Deprecation deprecation = property.getMetadata().getDeprecation();
    if (deprecation != null && deprecation.getLevel() == ConfigurationMetadata.Property.Deprecation.Level.ERROR) {
      // Fully unsupported property should not be included in suggestion
      return null;
    }
    LookupElementBuilder leb = LookupElementBuilder
        .create(property.getName())
        .withIcon(AllIcons.Nodes.Property)
        .withStrikeoutness(deprecation != null)
        .withInsertHandler(new YamlKeyInsertHandler());
    if (StringUtils.isNotBlank(property.getMetadata().getDescription())) {
      leb = leb.withTailText("(" + property.getMetadata().getDescription() + ")", true);
    }
    if (StringUtils.isNotBlank(property.getMetadata().getType())) {
      leb = leb.withTypeText(GenericUtil.shortenJavaType(property.getMetadata().getType()), true);
    }
    return leb;
  }


  private LookupElement createLookupElement(MetadataGroup group) {
    return LookupElementBuilder
        .create(group.getName())
        .withIcon(AllIcons.FileTypes.SourceMap)
        .withInsertHandler(new YamlKeyInsertHandler());
  }
}
