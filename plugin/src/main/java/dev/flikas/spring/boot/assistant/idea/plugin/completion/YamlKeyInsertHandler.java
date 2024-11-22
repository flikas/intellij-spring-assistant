package dev.flikas.spring.boot.assistant.idea.plugin.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.OriginalNameProvider;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.editor.EditorModificationUtil.insertStringAtCaret;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getCodeStyleIntent;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getIndent;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getOverallIndent;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.CARET;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.UNDEFINED;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.UNKNOWN_CLASS;
import static java.util.Objects.requireNonNull;

public class YamlKeyInsertHandler implements InsertHandler<LookupElement> {
  @Override
  public void handleInsert(final @NotNull InsertionContext context, final @NotNull LookupElement lookupElement) {
    if (!nextCharAfterSpacesAndQuotesIsColon(getStringAfterAutoCompletedValue(context))) {
      String existingIndentation = getExistingIndentation(context, lookupElement);
      Suggestion suggestion = (Suggestion) lookupElement.getObject();
      String indentPerLevel = getCodeStyleIntent(context);
      Module module = findModule(context);

      PsiElement currentElement = context.getFile().findElementAt(context.getStartOffset());
      assert currentElement != null : "no element at " + context.getStartOffset();

      LinkedList<? extends OriginalNameProvider> suggestionNodes = new LinkedList<>(
          suggestion.getMatchesForReplacement());
      PsiElement insertAt = findInsertPlace(currentElement, suggestionNodes);
      if (insertAt != null && suggestionNodes.isEmpty()) {
        // This means the suggested property is already in the file, let's move caret to that property then return.
        this.deleteLookupTextAndRetrieveOldValue(context, currentElement);

        ASTNode node = null;
        if (insertAt instanceof YAMLKeyValue) {
          node = insertAt.getNode().findChildByType(YAMLTokenTypes.COLON);
        }
        if (node == null) {
          node = insertAt.getNode();
        }
        context.getEditor().getCaretModel().moveToOffset(node.getStartOffset() + node.getTextLength());
        return;
      }

      this.deleteLookupTextAndRetrieveOldValue(context, currentElement);

      String prefix = "";
      if (insertAt != null) {
        deleteEmptyLine(context);
        // need to move caret to the element's next line
        ASTNode node = insertAt.getNode();
        PsiElement indentElement = insertAt.getLastChild().getPrevSibling();
        if (indentElement.getNode().getElementType().equals(YAMLTokenTypes.INDENT)) {
          existingIndentation = indentElement.getText();
        }
        prefix = "\n" + existingIndentation;
        context.getEditor().getCaretModel().moveToOffset(node.getStartOffset() + node.getTextLength());
      }
      String suggestionWithCaret = prefix +
          getSuggestionReplacementWithCaret(module, suggestion, suggestionNodes, existingIndentation, indentPerLevel);
      String suggestionWithoutCaret = suggestionWithCaret.replace(CARET, "");

      insertStringAtCaret(context.getEditor(), suggestionWithoutCaret, false, true, getCaretIndex(suggestionWithCaret));
    }
  }


  /**
   * @return null if no siblings match current lookup element, which means no need to move the caret.
   */
  private PsiElement findInsertPlace(PsiElement currentElement, List<? extends OriginalNameProvider> matches) {
    // Find siblings if it is match the suggestion, or else return null.
    PsiElement elementContext = currentElement.getContext();
    PsiElement parent = requireNonNull(elementContext).getParent();

    return matchSuggestionChildren(parent, elementContext, matches);
  }


  private PsiElement matchSuggestionChildren(
      PsiElement parent, PsiElement childToExclude,
      List<? extends OriginalNameProvider> matchKeys
  ) {
    if (parent instanceof YAMLKeyValue) {
      @NotNull String key = ((YAMLKeyValue) parent).getKeyText();
      if (key.equals(matchKeys.get(0).getOriginalName())) {
        matchKeys.remove(0);
        @Nullable YAMLValue valueNode = ((YAMLKeyValue) parent).getValue();
        if (valueNode != null && matchKeys.size() > 0) {
          return Objects.requireNonNullElse(matchSuggestionChildren(valueNode, null, matchKeys), parent);
        } else {
          return parent;
        }
      }
    } else if (parent != null) {
      for (PsiElement child : parent.getChildren()) {
        if (child != childToExclude) {
          PsiElement place = matchSuggestionChildren(child, childToExclude, matchKeys);
          if (place != null) {
            return place;
          }
        }
      }
    }
    return null;
  }


  private int getCaretIndex(final String suggestionWithCaret) {
    return suggestionWithCaret.indexOf(CARET);
  }


  private String getExistingIndentation(final InsertionContext context, final LookupElement item) {
    final String stringBeforeAutoCompletedValue = getStringBeforeAutoCompletedValue(context, item);
    return getExistingIndentationInRowStartingFromEnd(stringBeforeAutoCompletedValue);
  }


  @NotNull
  private String getStringAfterAutoCompletedValue(final InsertionContext context) {
    return context.getDocument().getText().substring(context.getTailOffset());
  }


  @NotNull
  private String getStringBeforeAutoCompletedValue(
      final InsertionContext context,
      final LookupElement item
  ) {
    return context.getDocument().getText()
        .substring(0, context.getTailOffset() - item.getLookupString().length());
  }


  private boolean nextCharAfterSpacesAndQuotesIsColon(final String string) {
    for (int i = 0; i < string.length(); i++) {
      final char c = string.charAt(i);
      if (c != ' ' && c != '"') {
        return c == ':';
      }
    }
    return false;
  }


  private String getExistingIndentationInRowStartingFromEnd(final String val) {
    int count = 0;
    for (int i = val.length() - 1; i >= 0; i--) {
      final char c = val.charAt(i);
      if (c != '\t' && c != ' ' && c != '-') {
        break;
      }
      count++;
    }
    return val.substring(val.length() - count).replaceAll("-", " ");
  }


  private void deleteLookupTextAndRetrieveOldValue(
      InsertionContext context,
      @NotNull PsiElement elementAtCaret
  ) {
    if (elementAtCaret.getNode().getElementType() != YAMLTokenTypes.SCALAR_KEY) {
      deleteLookupPlain(context);
    } else {
      YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(elementAtCaret, YAMLKeyValue.class);
      assert keyValue != null;
      context.commitDocument();

      // TODO: Whats going on here?
      if (keyValue.getValue() != null) {
        YAMLKeyValue dummyKV =
            YAMLElementGenerator.getInstance(context.getProject()).createYamlKeyValue("foo", "b");
        dummyKV.setValue(keyValue.getValue());
      }

      context.setTailOffset(keyValue.getTextRange().getEndOffset());
      runWriteCommandAction(
          context.getProject(),
          () -> keyValue.getParentMapping().deleteKeyValue(keyValue)
      );
    }
  }


  private void deleteLookupPlain(InsertionContext context) {
    Document document = context.getDocument();
    document.deleteString(context.getStartOffset(), context.getTailOffset());
    context.commitDocument();
  }


  private void deleteEmptyLine(InsertionContext context) {
    Editor editor = context.getEditor();
    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(editor.getCaretModel().getOffset());
    if (DocumentUtil.isLineEmpty(document, lineNumber)) {
      LineOffsets lineOffsets = LineOffsetsUtil.create(document);
      document.deleteString(lineOffsets.getLineStart(lineNumber), lineOffsets.getLineEnd(lineNumber, true));
      context.commitDocument();
    }
  }


  @NotNull
  private String getSuggestionReplacementWithCaret(
      Module module, Suggestion suggestion,
      List<? extends OriginalNameProvider> matchesTopFirst, String existingIndentation, String indentPerLevel
  ) {
    StringBuilder builder = new StringBuilder();
    int i = 0;
    do {
      OriginalNameProvider nameProvider = matchesTopFirst.get(i);
      builder.append("\n").append(existingIndentation).append(getIndent(indentPerLevel, i))
          .append(nameProvider.getOriginalName()).append(":");
      i++;
    } while (i < matchesTopFirst.size());
    builder.delete(0, existingIndentation.length() + 1);
    String indentForNextLevel =
        getOverallIndent(existingIndentation, indentPerLevel, matchesTopFirst.size());
    String sufix = getPlaceholderSufixWithCaret(module, suggestion, indentForNextLevel);
    builder.append(sufix);
    return builder.toString();
  }


  @NotNull
  private String getPlaceholderSufixWithCaret(
      Module module, Suggestion suggestion,
      String indentForNextLevel
  ) {
    if (suggestion.getLastSuggestionNode().isMetadataNonProperty()) {
      return "\n" + indentForNextLevel + CARET;
    }
    SuggestionNodeType nodeType = suggestion.getSuggestionNodeType(module);
    if (nodeType == UNDEFINED || nodeType == UNKNOWN_CLASS) {
      return CARET;
    } else if (nodeType.representsLeaf()) {
      return " " + CARET;
    } else if (nodeType.representsArrayOrCollection()) {
      return "\n" + indentForNextLevel + "- " + CARET;
    } else { // map or class
      return "\n" + indentForNextLevel + CARET;
    }
  }

}
