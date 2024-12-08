package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiMethod;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata.Group;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Optional;

public interface MetadataGroup extends MetadataItem {

  @Override
  default @NotNull Pair<String, Icon> getIcon() {
    return new Pair<>("AllIcons.FileTypes.SourceMap", AllIcons.FileTypes.SourceMap);
  }

  Optional<PsiMethod> getSourceMethod();

  Group getMetadata();
}
