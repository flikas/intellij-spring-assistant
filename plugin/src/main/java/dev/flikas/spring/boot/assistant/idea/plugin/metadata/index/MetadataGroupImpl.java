package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiElementUtils;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiMethodUtils;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@EqualsAndHashCode(of = "metadata")
@ToString(of = "metadata")
class MetadataGroupImpl implements MetadataGroup {
  private final MetadataIndex index;
  @Getter
  private final ConfigurationMetadata.Group metadata;
  private volatile String renderedDocument = null;


  MetadataGroupImpl(MetadataIndex index, ConfigurationMetadata.Group metadata) {
    this.index = index;
    this.metadata = metadata;
  }


  @Override
  public @NotNull String getNameStr() {
    return metadata.getName();
  }


  /**
   * @see ConfigurationMetadata.Group#getType()
   */
  @Override
  public Optional<PsiClass> getType() {
    return Optional.ofNullable(metadata.getType())
        .filter(StringUtils::isNotBlank)
        .map(type -> PsiTypeUtils.findClass(index.project(), type));
  }


  /**
   * @see ConfigurationMetadata.Group#getSourceType()
   */
  @Override
  public Optional<PsiClass> getSourceType() {
    return Optional.ofNullable(metadata.getSourceType())
        .filter(StringUtils::isNotBlank)
        .map(type -> PsiTypeUtils.findClass(index.project(), type));
  }


  @Override
  public @NotNull String getRenderedDescription() {
    if (this.renderedDocument != null) {
      return this.renderedDocument;
    }
    synchronized (this) {
      if (this.renderedDocument != null) {
        return this.renderedDocument;
      }
      HtmlBuilder doc = new HtmlBuilder();
      String desc = metadata.getDescription();
      //Unfortunately, even though there is a 'description' field for the group metadata, `spring boot configuration processor` will never fill it.
      //Here we use group class/method's document instead.
      String descFrom = null;
      if (StringUtils.isBlank(desc)) {
        desc = getSourceMethod().map(PsiElementUtils::getDocument).orElse(null);
        descFrom = getSourceMethod().map(PsiElementUtils::createLinkForDoc).orElse(null);
      }
      if (StringUtils.isBlank(desc)) {
        desc = getType().map(PsiElementUtils::getDocument).orElse(null);
        descFrom = getType().map(PsiElementUtils::createLinkForDoc).orElse(null);
      }
      if (StringUtils.isBlank(desc)) {
        desc = getSourceType().map(PsiElementUtils::getDocument).orElse(null);
        descFrom = getSourceType().map(PsiElementUtils::createLinkForDoc).orElse(null);
      }
      if (StringUtils.isNotBlank(desc)) {
        if (StringUtils.isNotBlank(descFrom)) {
          doc.append(DocumentationMarkup.GRAYED_ELEMENT
              .addText("(Doc below is copied from ")
              .addRaw(descFrom)
              .addText(")\n"));
        }
        doc.appendRaw(desc);
      }
      this.renderedDocument = doc.toString();
    }
    return this.renderedDocument;
  }


  @Override
  public MetadataIndex getIndex() {
    return index;
  }


  /**
   * @see ConfigurationMetadata.Group#getSourceMethod()
   */
  @Override
  public Optional<PsiMethod> getSourceMethod() {
    String sourceMethod = metadata.getSourceMethod();
    if (StringUtils.isBlank(sourceMethod)) return Optional.empty();
    return getSourceType().flatMap(sourceClass -> PsiMethodUtils.findMethodBySignature(sourceClass, sourceMethod));
  }
}
