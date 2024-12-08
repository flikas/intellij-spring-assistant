package dev.flikas.spring.boot.assistant.idea.plugin.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import dev.flikas.spring.boot.assistant.idea.plugin.documentation.MetadataItemVirtualElement;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataItem;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.editor.EditorModificationUtil.insertStringAtCaret;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getCodeStyleIntent;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getIndent;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getOverallIndent;
import static java.util.Objects.requireNonNull;

public class YamlKeyInsertHandler implements InsertHandler<LookupElement> {
  private static final String CARET = "<caret>";


  @Override
  public void handleInsert(final @NotNull InsertionContext context, final @NotNull LookupElement lookupElement) {
    if (nextCharAfterSpacesAndQuotesIsColon(getStringAfterAutoCompletedValue(context))) {
      return;
    }
    String existingIndentation = getExistingIndentation(context, lookupElement);
    MetadataItem suggestion = ((MetadataItemVirtualElement) requireNonNull(
        lookupElement.getPsiElement())).getMetadataItem();
    String indentPerLevel = getCodeStyleIntent(context);
    @NotNull Project project = context.getProject();

    PsiElement currentElement = context.getFile().findElementAt(context.getStartOffset());
    assert currentElement != null : "no element at " + context.getStartOffset();

    AtomicReference<PropertyName> suggestionNameRef = new AtomicReference<>(
        PropertyName.adapt(lookupElement.getLookupString()));
    PsiElement insertAt = findInsertPlace(currentElement, suggestionNameRef);
    PropertyName suggestionName = suggestionNameRef.get();
    if (insertAt != null && suggestionName.isEmpty()) {
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
        getSuggestionReplacementWithCaret(project, suggestion, suggestionName, existingIndentation, indentPerLevel);
    String suggestionWithoutCaret = suggestionWithCaret.replace(CARET, "");

    insertStringAtCaret(context.getEditor(), suggestionWithoutCaret, false, true, getCaretIndex(suggestionWithCaret));
  }


  /**
   * Find correct position to insert suggestion.
   * <p>
   * If there is a sibling matches the selected suggestion, we should insert suggestion under that, because YAML does not
   * allow duplicate keys.
   *
   * @return null if no siblings match current lookup element, which means no need to move the caret.
   */
  @Nullable
  private PsiElement findInsertPlace(PsiElement currentElement, AtomicReference<PropertyName> suggestion) {
    // Find siblings if it is match the suggestion, or else return null.
    PsiElement elementContext = currentElement.getContext();
    PsiElement parent = requireNonNull(elementContext).getParent();

    return matchSuggestionChildren(parent, elementContext, suggestion);
  }


  private PsiElement matchSuggestionChildren(
      PsiElement parent, PsiElement childToExclude,
      AtomicReference<PropertyName> matchKey
  ) {
    if (parent instanceof YAMLKeyValue) {
      PropertyName parentName = PropertyName.adapt(((YAMLKeyValue) parent).getKeyText());
      PropertyName firstPart = matchKey.get().chop(1);
      if (parentName.equals(firstPart)) {
        matchKey.set(matchKey.get().subName(1));
        @Nullable YAMLValue valueNode = ((YAMLKeyValue) parent).getValue();
        if (valueNode != null && !matchKey.get().isEmpty()) {
          return Objects.requireNonNullElse(matchSuggestionChildren(valueNode, null, matchKey), parent);
        } else {
          return parent;
        }
      }
    } else if (parent != null) {
      for (PsiElement child : parent.getChildren()) {
        if (child != childToExclude) {
          PsiElement place = matchSuggestionChildren(child, childToExclude, matchKey);
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
  private String getStringBeforeAutoCompletedValue(final InsertionContext context, final LookupElement item) {
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


  private void deleteLookupTextAndRetrieveOldValue(InsertionContext context, @NotNull PsiElement elementAtCaret) {
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
      WriteCommandAction.runWriteCommandAction(context.getProject(), keyValue::delete);
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
      @NotNull Project project, MetadataItem suggestion,
      PropertyName matchesTopFirst, String existingIndentation, String indentPerLevel
  ) {
    StringBuilder builder = new StringBuilder();
    int i = 0;
    do {
      String nameProvider = matchesTopFirst.getElement(i, Form.DASHED);
      builder.append("\n").append(existingIndentation).append(getIndent(indentPerLevel, i))
          .append(nameProvider).append(":");
      i++;
    } while (i < matchesTopFirst.getNumberOfElements());
    builder.delete(0, existingIndentation.length() + 1);
    String indentForNextLevel =
        getOverallIndent(existingIndentation, indentPerLevel, matchesTopFirst.getNumberOfElements());
    String suffix = getPlaceholderSuffixWithCaret(project, suggestion, indentForNextLevel);
    builder.append(suffix);
    return builder.toString();
  }


  @NotNull
  private String getPlaceholderSuffixWithCaret(
      @NotNull Project project, MetadataItem suggestion, String indentForNextLevel) {
    if (suggestion instanceof MetadataGroup) {
      return "\n" + indentForNextLevel + CARET;
    } else if (suggestion instanceof MetadataProperty property) {
      PsiType propType = property.getFullType().orElse(null);
      if (PsiTypeUtils.isValueType(propType)) {
        return " " + CARET;
      } else if (PsiTypeUtils.isCollection(project, propType)) {
        return "\n" + indentForNextLevel + "- " + CARET;
      } else if (PsiTypeUtils.isMap(project, propType)) { // map or class
        return "\n" + indentForNextLevel + CARET;
      } else {
        return CARET;
      }
    } else {
      throw new IllegalStateException("Unsupported type of suggestion: " + suggestion.getClass());
    }
  }
}
