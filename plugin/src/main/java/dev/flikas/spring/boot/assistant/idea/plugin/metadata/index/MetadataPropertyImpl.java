package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiElementUtils;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form.DASHED;

@EqualsAndHashCode(of = "metadata")
@ToString(of = "metadata")
class MetadataPropertyImpl implements MetadataProperty {
  private final MetadataIndex index;
  @Getter
  private final ConfigurationMetadata.Property metadata;
  @Getter(AccessLevel.PROTECTED)
  private final PropertyName propertyName;
  private final PsiType propertyType;

  private volatile String renderedDocument = null;


  MetadataPropertyImpl(MetadataIndex index, ConfigurationMetadata.Property metadata) {
    this.index = index;
    this.metadata = metadata;
    this.propertyName = PropertyName.of(metadata.getName());
    if (StringUtils.isBlank(metadata.getType())) {
      this.propertyType = null;
    } else {
      PsiJavaParserFacade parser = JavaPsiFacade.getInstance(index.getProject()).getParserFacade();
      // When reference an inner class, we should use A.B not A$B but spring does.
      String typeString = metadata.getType().replace('$', '.');
      this.propertyType = ReadAction.compute(() -> {
        PsiType t = parser.createTypeFromText(typeString, null);
        return PsiTypeUtils.isPhysical(t) ? t : null;
      });
    }
  }


  @Override
  @NotNull
  public String getName() {
    return propertyName.toString();
  }


  @Override
  public Optional<PsiClass> getType() {
    return Optional.ofNullable(this.propertyType).map(PsiTypeUtils::resolveClassInType);
  }


  @Override
  public Optional<PsiClass> getSourceType() {
    return Optional.ofNullable(metadata.getSourceType())
        .filter(StringUtils::isNotBlank)
        .map(type -> PsiTypeUtils.findClass(index.getProject(), type));
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
      //If this Property is generated from code, it's description won't be filled on creation for better performance,
      //We will read it from source code's javadoc, and cache it here.
      String descFrom = null;
      if (StringUtils.isBlank(desc)) {
        PsiField field = getSourceField().orElse(null);
        if (field != null) {
          desc = PsiElementUtils.getDocument(field);
          descFrom = PsiElementUtils.createLinkForDoc(field);
          if (StringUtils.isBlank(desc)) {
            PsiMethod setter = PropertyUtil.findSetterForField(field);
            if (setter != null) {
              desc = PsiElementUtils.getDocument(setter);
              descFrom = PsiElementUtils.createLinkForDoc(setter);
            }
          }
          if (StringUtils.isBlank(desc)) {
            PsiMethod getter = PropertyUtil.findGetterForField(field);
            if (getter != null) {
              desc = PsiElementUtils.getDocument(getter);
              descFrom = PsiElementUtils.createLinkForDoc(getter);
            }
          }
        }
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


  @Override
  public Optional<PsiType> getFullType() {
    return Optional.ofNullable(this.propertyType).filter(t -> ReadAction.compute(t::isValid));
  }


  @Override
  public Optional<PsiField> getSourceField() {
    return getSourceType().map(type -> type.findFieldByName(getCamelCaseLastName(), true));
  }


  @Override
  public Optional<MetadataHint> getHint() {
    return Optional.ofNullable(
        index.getHints().getOrDefault(propertyName, index.getHints().get(propertyName.append("values"))));
  }


  @Override
  public Optional<MetadataHint> getKeyHint() {
    return Optional.ofNullable(index.getHints().get(propertyName.append("keys")));
  }


  @Override
  public boolean canBind(@NotNull String key) {
    PropertyName keyName = PropertyName.adapt(key);
    return this.propertyName.equals(keyName)
        // A Map property can bind all sub-key-values.
        || this.propertyName.isAncestorOf(keyName) && PsiTypeUtils.isValueMap(index.getProject(),
        getFullType().orElse(null));
  }


  private String getCamelCaseLastName() {
    return PropertyName.toCamelCase(propertyName.getLastElement(DASHED));
  }
}
