package dev.flikas.spring.boot.assistant.idea.plugin.documentation;

import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.model.Pointer;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.PropertyHintValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class HintDocumentationVirtualElement extends LightElement implements DocumentationTarget {
  @NotNull private final PropertyHintValue hintValue;


  public HintDocumentationVirtualElement(@NotNull PropertyHintValue hintValue, @NotNull PsiManager manager) {
    super(manager, Language.ANY);
    this.hintValue = hintValue;
  }


  @Override
  public String toString() {
    return hintValue.getValue();
  }


  @SuppressWarnings("UnstableApiUsage")
  @Override
  public @NotNull Pointer<HintDocumentationVirtualElement> createPointer() {
    return Pointer.hardPointer(this);
  }


  @Override
  public String getText() {
    return toString();
  }


  @SuppressWarnings("UnstableApiUsage")
  @Override
  public @NotNull TargetPresentation computePresentation() {
    return TargetPresentation.builder(hintValue.getValue())
        .icon(hintValue.getIcon())
        .presentation();
  }


  @Override
  public @Nullable DocumentationResult computeDocumentation() {
    HtmlChunk doc = HtmlChunk.fragment(
        DocumentationMarkup.DEFINITION_ELEMENT.addText(hintValue.getValue()),
        HtmlChunk.hr(),
        DocumentationMarkup.CONTENT_ELEMENT.addText(Objects.requireNonNullElse(hintValue.getDescription(), "")));
    return DocumentationResult.documentation(doc.toString());
  }
}
