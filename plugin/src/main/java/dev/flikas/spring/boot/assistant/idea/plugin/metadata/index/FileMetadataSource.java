package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.google.gson.Gson;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

@SuppressWarnings("LombokGetterMayBeUsed")
public class FileMetadataSource extends AbstractMetadataSource {
  private static final ThreadLocal<Gson> gson = ThreadLocal.withInitial(Gson::new);
  @Getter private VirtualFile source;


  public FileMetadataSource(VirtualFile source) {
    this.source = source;
  }


  /**
   * If current source {@link VirtualFile} is invalid, try to recreate it with the same URL,
   * if succeeded, return true.
   *
   * @return true if current source file is invalid and the recreation is succeeded.
   */
  public boolean tryReloadIfInvalid() {
    if (!isValid()) {
      VirtualFile vf = VirtualFileManager.getInstance().refreshAndFindFileByUrl(this.source.getUrl());
      if (vf != null) {
        this.source = vf;
        return true;
      }
    }
    return false;
  }


  @Override
  public boolean isValid() {
    return source.isValid();
  }


  @Override
  public String getPresentation() {
    return source.toString();
  }


  public ConfigurationMetadata getContent() throws IOException {
    try (Reader reader = new InputStreamReader(source.getInputStream(), source.getCharset())) {
      ConfigurationMetadata metadata = gson.get().fromJson(reader, ConfigurationMetadata.class);
      markSynchronized();
      return metadata;
    }
  }
}
