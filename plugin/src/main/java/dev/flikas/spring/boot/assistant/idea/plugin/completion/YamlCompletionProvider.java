package dev.flikas.spring.boot.assistant.idea.plugin.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.YAMLSequence;

import java.util.ArrayList;
import java.util.List;

import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType.yaml;
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

    String propertyName = YAMLUtil.getConfigFullName(context);
    ModuleMetadataService service = module.getService(ModuleMetadataService.class);

    suggestions = service.findSuggestionsForQueryPrefix(
        yaml,
        element,
        ancestralKeys,
        queryWithDotDelimitedPrefixes,
        null
    );

    if (suggestions != null) {
      suggestions.forEach(resultSet::addElement);
    }
  }
}
