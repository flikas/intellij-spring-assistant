package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.util.ModificationTracker;

public interface MetadataSource {

  /**
   * Get the presentation string of this source
   */
  String getPresentation();

  /**
   * Get source element of this metadata
   */
  ModificationTracker getSource();

  /**
   * @return true if the source is still isValid.
   */
  boolean isValid();

  /**
   * @return true if this source has changed since last {@link #markSynchronized()}
   */
  boolean isChanged();

  /**
   * Set the current state of source has been synchronized.
   * After marked, {@link #isChanged()} should return {@code true}.
   */
  void markSynchronized();
}
