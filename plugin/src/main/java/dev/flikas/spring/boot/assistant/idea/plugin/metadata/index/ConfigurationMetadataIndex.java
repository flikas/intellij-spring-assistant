package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * An index created from a {@link ConfigurationMetadata}
 */
@SuppressWarnings("LombokGetterMayBeUsed")
public class ConfigurationMetadataIndex extends MetadataIndexBase {
  private final MetadataSource source;


  public ConfigurationMetadataIndex(
      @NotNull ConfigurationMetadata metadata, @NotNull PsiElement sourceElement, @NotNull Project project) {
    super(project);
    add(sourceElement.toString(), metadata);
    this.source = new PsiElementMetadataSource(sourceElement);
    this.source.markSynchronized();
  }


  public ConfigurationMetadataIndex(@NotNull FileMetadataSource source, @NotNull Project project) throws IOException {
    super(project);
    add(source.getPresentation(), source.getContent());
    this.source = source;
  }


  @Override
  public @NotNull List<MetadataSource> getSource() {
    return List.of(source);
  }
}
