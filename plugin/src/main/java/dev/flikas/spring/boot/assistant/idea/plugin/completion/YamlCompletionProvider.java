package dev.flikas.spring.boot.assistant.idea.plugin.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.YAMLScalar;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;

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
    Module module = findModuleForPsiElement(element);
    if (module == null) {
      return;
    }

    // Find context YAMLPsiElement, stop if context is not at the same line.
    @Nullable YAMLPsiElement context = PsiTreeUtil.getParentOfType(element, false, YAMLPsiElement.class);
    if (context == null) return;
    if (!YAMLUtil.psiAreAtTheSameLine(element, context)) return;

    String queryString = element.getText();
    String ancestorKeys = YAMLUtil.getConfigFullName(context);

    ancestorKeys = StringUtils.removeEnd(ancestorKeys, queryString);
    queryString = StringUtils.remove(queryString, DUMMY_IDENTIFIER);
    queryString = StringUtils.removeEnd(queryString, DUMMY_IDENTIFIER_TRIMMED);
    CompletionService service = CompletionService.getInstance(project);
    YAMLKeyValue nearestKeyValue = PsiTreeUtil.getParentOfType(context, false, YAMLKeyValue.class);
    if (nearestKeyValue != null
        && YAMLUtil.psiAreAtTheSameLine(nearestKeyValue, context)
        && context instanceof YAMLScalar) {
      // User is asking completion for property value
      resultSet.addAllElements(service.findSuggestionForValue(module, ancestorKeys, queryString));
    } else {
      // Key completion
      resultSet.addAllElements(service.findSuggestionForKey(module, ancestorKeys, queryString));
    }
  }
}
