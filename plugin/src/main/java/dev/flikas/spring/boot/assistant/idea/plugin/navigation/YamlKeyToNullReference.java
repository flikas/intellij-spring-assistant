package dev.flikas.spring.boot.assistant.idea.plugin.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.light.LightIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class YamlKeyToNullReference extends PsiReferenceBase<YamlKeyToNullReference.YamlKeyIdentifier>
    implements Comparable<YamlKeyToNullReference> {
  @NotNull
  private final YAMLKeyValue yamlKeyValue;


  public YamlKeyToNullReference(@NotNull YAMLKeyValue yamlKeyValue) {
    super(new YamlKeyIdentifier(yamlKeyValue), getTextRange(yamlKeyValue), true);
    this.yamlKeyValue = yamlKeyValue;
  }


  @Override
  public @Nullable PsiElement resolve() {
    return null;
  }


  @Override
  public int compareTo(@NotNull YamlKeyToNullReference o) {
    if (this.equals(o)) {
      return 0;
    } else {
      return this.yamlKeyValue.hashCode() - o.yamlKeyValue.hashCode();
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    YamlKeyToNullReference that = (YamlKeyToNullReference) o;

    return yamlKeyValue.equals(that.yamlKeyValue);
  }


  @Override
  public int hashCode() {
    return yamlKeyValue.hashCode();
  }


  private static TextRange getTextRange(YAMLKeyValue yamlKeyValue) {
    PsiElement key = yamlKeyValue.getKey();
    return key != null ? key.getTextRangeInParent() : TextRange.from(0, yamlKeyValue.getTextLength());
  }


  /**
   * For {@linkplain com.intellij.codeInsight.highlighting.JavaReadWriteAccessDetector JavaReadWriteAccessDetector}
   * detect YamlKeyValue correctly to a writer.
   */
  public static class YamlKeyIdentifier extends LightIdentifier {
    private final YAMLKeyValue myElement;


    public YamlKeyIdentifier(YAMLKeyValue kv) {
      super(kv.getManager(), ReadAction.compute(kv::getText));
      this.myElement = kv;
    }


    @Override
    public boolean isValid() {
      return myElement.isValid();
    }


    @Override
    public TextRange getTextRange() {
      return myElement.getTextRange();
    }


    @Override
    public PsiFile getContainingFile() {
      return myElement.getContainingFile();
    }


    @Override
    public int getStartOffsetInParent() {
      return myElement.getStartOffsetInParent();
    }


    @Override
    public int getTextOffset() {
      return myElement.getTextOffset();
    }


    @Override
    public PsiElement getParent() {
      return myElement.getParent();
    }


    @Override
    public PsiElement getPrevSibling() {
      return myElement.getPrevSibling();
    }


    @Override
    public PsiElement getNextSibling() {
      return myElement.getNextSibling();
    }


    @Override
    public PsiElement copy() {
      return new YamlKeyIdentifier(myElement);
    }
  }
}
