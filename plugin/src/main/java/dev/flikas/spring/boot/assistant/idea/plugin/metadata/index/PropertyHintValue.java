package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.psi.PsiElement;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Data
public class PropertyHintValue {
  @NotNull private String value;
  @Nullable private String oneLineDescription;
  @Nullable private String description;
  @Nullable private Icon icon;
  @Nullable private PsiElement psiElement;
}
