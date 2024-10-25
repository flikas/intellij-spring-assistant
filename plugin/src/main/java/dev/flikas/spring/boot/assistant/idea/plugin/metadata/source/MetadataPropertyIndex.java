package dev.flikas.spring.boot.assistant.idea.plugin.metadata.source;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class MetadataPropertyIndex extends FileBasedIndexExtension<PropertyName, ConfigurationMetadata.Property> {
  public static final String METADATA_FILE_NAME = "spring-configuration-metadata.json";
  public static final String METADATA_FILE = "META-INF/" + METADATA_FILE_NAME;
  public static final String ADDITIONAL_METADATA_FILE_NAME = "additional-spring-configuration-metadata.json";
  public static final String ADDITIONAL_METADATA_FILE = "META-INF/" + ADDITIONAL_METADATA_FILE_NAME;
  public static final ID<PropertyName, ConfigurationMetadata.Property> NAME = ID.create(
      MetadataPropertyIndex.class.getCanonicalName());

  private static final Logger LOG = Logger.getInstance(MetadataPropertyIndex.class);

  private final ThreadLocal<Gson> gson = ThreadLocal.withInitial(Gson::new);


  @Override
  public @NotNull ID<PropertyName, ConfigurationMetadata.Property> getName() {
    return NAME;
  }


  @Override
  public int getVersion() {
    return 0;
  }


  @Override
  public boolean dependsOnFileContent() {
    return true;
  }


  @Override
  public @NotNull DataIndexer<PropertyName, ConfigurationMetadata.Property, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<PropertyName, ConfigurationMetadata.Property> map(@NotNull FileContent inputData) {
        VirtualFile file = inputData.getFile();
        if (file.isInLocalFileSystem()) {
          Project project = inputData.getProject();
          ProjectFileIndex pfi = ProjectFileIndex.getInstance(project);
          VirtualFile root = pfi.getClassRootForFile(file);
          if (root == null) return Collections.emptyMap();
          String relativePath = VfsUtilCore.getRelativePath(file, root);
          if (relativePath == null || relativePath.equals(METADATA_FILE)
              || relativePath.equals(ADDITIONAL_METADATA_FILE)) {
            return Collections.emptyMap();
          }
        }
        ConfigurationMetadata meta = gson.get()
            .fromJson(inputData.getContentAsText().toString(), ConfigurationMetadata.class);
        LOG.info("Indexed file " + file.getPath() + " loaded " + meta.getProperties().size() + " properties");
        return meta.getProperties().stream().collect(Collectors.toMap(p -> PropertyName.adapt(p.getName()), p -> p));
      }
    };
  }


  @Override
  public @NotNull KeyDescriptor<PropertyName> getKeyDescriptor() {
    return new PropertyNameDescriptor();
  }


  @Override
  public @NotNull DataExternalizer<ConfigurationMetadata.Property> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, ConfigurationMetadata.Property value) throws IOException {
        IOUtil.writeUTF(out, gson.get().toJson(value));
      }


      @Override
      public ConfigurationMetadata.Property read(@NotNull DataInput in) throws IOException {
        return gson.get().fromJson(IOUtil.readUTF(in), ConfigurationMetadata.Property.class);
      }
    };
  }


  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return file -> {
      if (!file.getName().equals(METADATA_FILE_NAME) && !file.getName().equals(ADDITIONAL_METADATA_FILE)) return false;
      
      if (file.isInLocalFileSystem()) {
        String path = file.getPath();
        return path.endsWith(METADATA_FILE) || path.endsWith(ADDITIONAL_METADATA_FILE);
      } else {
        String relPath = VfsUtilCore.getRelativePath(file, VfsUtilCore.getRootFile(file));
        return relPath != null && (relPath.equals(METADATA_FILE) || relPath.equals(ADDITIONAL_METADATA_FILE));
      }
    };
  }
}
