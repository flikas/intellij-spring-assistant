package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * A spring configuration metadata property
 */
public interface MetadataProperty extends MetadataItem {
  /**
   * The PsiType include type arguments, for example, {@code Map<String, String>}.
   *
   * @see ConfigurationMetadata.Property#getType()
   */
  Optional<PsiType> getFullType();

  /**
   * @return the field that this property will be bound to, null if not present.
   */
  Optional<PsiField> getSourceField();

  /**
   * get hint or value hint for this property.
   */
  Optional<MetadataHint> getHint();

  /**
   * get available values for this property.
   * <p>
   * if this property is Map, get available values for map values.
   * <p>
   * if there is no hint, return empty list.
   */
  @NotNull List<PropertyHintValue> getHintValues();

  /**
   * get key hint for this property if it is a Map.
   */
  Optional<MetadataHint> getKeyHint();

  /**
   * get available values for this map property's keys.
   * <p>
   * if there is no hint, return empty list.
   */
  @NotNull List<PropertyHintValue> getKeyHintValues();

  /**
   * @return whether the specified key can be bound to this property.
   */
  boolean canBind(@NotNull String key);

  default boolean isMapType() {
    return getFullType().filter(p -> PsiTypeUtils.isMap(getIndex().project(), p)).isPresent();
  }

  ConfigurationMetadata.Property getMetadata();
}
