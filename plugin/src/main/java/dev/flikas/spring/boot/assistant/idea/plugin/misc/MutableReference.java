package dev.flikas.spring.boot.assistant.idea.plugin.misc;

import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface MutableReference<T> {
  static <R> MutableReference<R> immutable(R obj) {
    return () -> obj;
  }

  @Nullable T dereference();

  /**
   * Refresh the inner object, this may cause later {@link #dereference()} returns {@code null}.
   */
  default void refresh() {}
}
