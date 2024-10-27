package dev.flikas.spring.boot.assistant.idea.plugin.metadata.source;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Objects;

public final class SourceTrackedProperty {
  private final VirtualFile sourceFile;
  private final ConfigurationMetadata.Property property;


  public SourceTrackedProperty(VirtualFile sourceFile, ConfigurationMetadata.Property property) {
    this.sourceFile = sourceFile;
    this.property = property;
  }


  public VirtualFile sourceFile() {return sourceFile;}


  public ConfigurationMetadata.Property property() {return property;}


  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (SourceTrackedProperty) obj;
    return Objects.equals(this.sourceFile, that.sourceFile) &&
        Objects.equals(this.property, that.property);
  }


  @Override
  public int hashCode() {
    return Objects.hash(sourceFile, property);
  }


  @Override
  public String toString() {
    return "SourceTrackedProperty[" +
        "sourceFile=" + sourceFile + ", " +
        "property=" + property + ']';
  }

}
