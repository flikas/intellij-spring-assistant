package dev.flikas.spring.boot.assistant.idea.plugin.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.flikas.spring.boot.assistant.idea.plugin.documentation.MetadataItemVirtualElement;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataItem;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLPsiElement;

import java.util.Collection;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;

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

    // Find context YAMLPsiElement, stop if context is not at the same line.
    @Nullable YAMLPsiElement context = PsiTreeUtil.getContextOfType(element, false, YAMLPsiElement.class);
    if (context == null) return;
    if (!YAMLUtil.psiAreAtTheSameLine(element, context)) return;

//    PsiElement parent = context.getParent();
//    if (parent instanceof YAMLSequence) {
//      // let's force user to create array element prefix before he can ask for suggestions
//      return;
//    }
    // TODO Value completion (enum & hints)
    String queryString = element.getText();
    String ancestorKeys = YAMLUtil.getConfigFullName(context);

    ancestorKeys = StringUtils.removeEnd(ancestorKeys, queryString);
    queryString = StringUtils.remove(queryString, DUMMY_IDENTIFIER);
    queryString = StringUtils.removeEnd(queryString, DUMMY_IDENTIFIER_TRIMMED);
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);
    Collection<MetadataItem> candidates = service.findSuggestionForCompletion(ancestorKeys, queryString);
    for (MetadataItem metaItem : candidates) {
      LookupElement le = switch (metaItem) {
        case MetadataProperty property -> createLookupElement(ancestorKeys, property);
        case MetadataGroup group -> createLookupElement(ancestorKeys, group);
        default -> throw new IllegalStateException("Unexpected value: " + metaItem);
      };
      if (le != null) resultSet.addElement(le);
    }
  }


  private static LookupElement createLookupElement(String propertyNameAncestors, MetadataProperty property) {
    ConfigurationMetadata.Property.Deprecation deprecation = property.getMetadata().getDeprecation();
    if (deprecation != null && deprecation.getLevel() == ConfigurationMetadata.Property.Deprecation.Level.ERROR) {
      // Fully unsupported property should not be included in suggestions
      return null;
    }
    LookupElementBuilder leb = LookupElementBuilder
        .create(removeParent(propertyNameAncestors, property.getNameStr()))
        .withIcon(property.getIcon().getSecond())
        .withPsiElement(new MetadataItemVirtualElement(property))
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


  private static LookupElement createLookupElement(String propertyNameAncestors, MetadataGroup group) {
    return LookupElementBuilder
        .create(removeParent(propertyNameAncestors, group.getNameStr()))
        .withIcon(group.getIcon().getSecond())
        .withPsiElement(new MetadataItemVirtualElement(group))
        .withInsertHandler(new YamlKeyInsertHandler());
  }


  private static String removeParent(String parent, String name) {
    PropertyName parentKey = PropertyName.adapt(parent);
    PropertyName key = PropertyName.adapt(name);
    assert parentKey.isAncestorOf(key) : "Invalid parent and child:" + parentKey + "," + key;
    return key.subName(parentKey.getNumberOfElements()).toString();
  }
}
