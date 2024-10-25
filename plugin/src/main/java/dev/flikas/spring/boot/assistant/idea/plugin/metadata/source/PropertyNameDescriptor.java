package dev.flikas.spring.boot.assistant.idea.plugin.metadata.source;

import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

class PropertyNameDescriptor implements KeyDescriptor<PropertyName> {
  @Override
  public int getHashCode(PropertyName value) {
    return value.hashCode();
  }


  @Override
  public void save(@NotNull DataOutput out, PropertyName value) throws IOException {
    out.writeUTF(value.toString());
  }


  @Override
  public boolean isEqual(PropertyName val1, PropertyName val2) {
    if (val1 == val2) {
      return true;
    } else if (val1 == null || val2 == null) {
      return false;
    } else {
      return val1.equals(val2);
    }
  }


  @Override
  public PropertyName read(@NotNull DataInput in) throws IOException {
    return PropertyName.of(in.readUTF());
  }
}
