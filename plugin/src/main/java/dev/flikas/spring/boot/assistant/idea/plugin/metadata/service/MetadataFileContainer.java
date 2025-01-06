package dev.flikas.spring.boot.assistant.idea.plugin.metadata.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.AggregatedMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.ConfigurationMetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.FileMetadataSource;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.MutableReference;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;

/**
 * A container of a loaded metadata file, can auto-reload while the file has changed or removed.
 */
class MetadataFileContainer implements MutableReference<MetadataIndex> {
  private static final Logger LOG = Logger.getInstance(ProjectMetadataService.class);
  @NotNull private final FileMetadataSource source;
  @NotNull private final Project project;
  private MetadataIndex metadata;


  MetadataFileContainer(@NotNull VirtualFile metadataFile, @NotNull Project project) {
    this.source = new FileMetadataSource(metadataFile);
    this.project = project;
    refresh();
  }


  @Override
  public @Nullable MetadataIndex dereference() {
    refresh();
    return this.metadata;
  }


  @Override
  public synchronized void refresh() {
    if (!this.source.isValid()) {
      if (!this.source.tryReloadIfInvalid()) {
        this.metadata = null;
        return;
      }
    } else if (!this.source.isChanged()) {
      return;
    }
    try {
      AggregatedMetadataIndex index = new AggregatedMetadataIndex(
          new ConfigurationMetadataIndex(this.source, this.project));
      // Spring does not create metadata for types in collections, we should create it by ourselves and expand our index,
      // to better support code-completion, documentation, navigation, etc.
      for (MetadataProperty property : index.getProperties().values()) {
        resolvePropertyType(property).ifPresent(index::addFirst);
      }
      this.metadata = index;
    } catch (IOException e) {
      LOG.warn("Read metadata file " + this.source.getPresentation() + " failed", e);
    }
  }


  /**
   * @see ConfigurationMetadata.Property#getType()
   */
  @NotNull
  private Optional<MetadataIndex> resolvePropertyType(@NotNull MetadataProperty property) {
    return property.getFullType().filter(t -> PsiTypeUtils.isCollectionOrMap(project, t))
        .flatMap(t -> project.getService(ProjectClassMetadataService.class).getMetadata(property.getNameStr(), t));
  }


  @Override
  public String toString() {
    return "Metadata index form " + this.source.getPresentation();
  }
}
