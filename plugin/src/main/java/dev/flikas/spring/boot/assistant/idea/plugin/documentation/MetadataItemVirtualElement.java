package dev.flikas.spring.boot.assistant.idea.plugin.documentation;

import com.intellij.lang.Language;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataItem;
import org.jetbrains.annotations.NotNull;

public class MetadataItemVirtualElement extends LightElement {
  @NotNull private final MetadataItem metadataItem;


  public MetadataItemVirtualElement(@NotNull MetadataItem metadataItem) {
    super(PsiManager.getInstance(metadataItem.getIndex().project()), Language.ANY);
    this.metadataItem = metadataItem;
  }


  public @NotNull MetadataItem getMetadataItem() {
    return metadataItem;
  }


  @Override
  public String toString() {
    return this.metadataItem.getNameStr();
  }
}
