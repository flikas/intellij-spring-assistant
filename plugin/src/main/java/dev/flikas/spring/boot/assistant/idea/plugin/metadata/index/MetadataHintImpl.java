package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@SuppressWarnings("LombokGetterMayBeUsed")
@RequiredArgsConstructor
@EqualsAndHashCode(of = "metadata")
@ToString(of = "metadata")
class MetadataHintImpl implements MetadataHint {
  @Getter
  private final ConfigurationMetadata.Hint metadata;
}
