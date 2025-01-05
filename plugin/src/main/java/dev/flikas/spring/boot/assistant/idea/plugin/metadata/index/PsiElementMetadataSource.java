package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiModificationTracker;
import lombok.Getter;

public class PsiElementMetadataSource extends AbstractMetadataSource {
  private final PsiElement sourceElement;
  @Getter private final ModificationTracker source;


  public PsiElementMetadataSource(PsiElement sourceElement) {
    this.sourceElement = sourceElement;
    //TODO this modification tracker is not for this PsiElement only, but for the all project.
    //     If there is not an alternative solution for tracking this PsiElement's modification,
    //     let's remove the modification tracking from the MetadataSource interface.
    this.source = PsiModificationTracker.getInstance(sourceElement.getProject()).forLanguage(JavaLanguage.INSTANCE);
  }


  @Override
  public String getPresentation() {
    return sourceElement.toString();
  }


  @Override
  public boolean isValid() {
    return this.sourceElement.isValid();
  }
}
