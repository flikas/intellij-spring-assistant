package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

public abstract class AbstractMetadataSource implements MetadataSource {
  private long lastModificationStamp = -1;


  @Override
  public void markSynchronized() {
    this.lastModificationStamp = getSource().getModificationCount();
  }


  @Override
  public boolean isChanged() {
    return isValid() && this.lastModificationStamp != getSource().getModificationCount();
  }


  @Override
  public String toString() {
    return getPresentation();
  }
}
