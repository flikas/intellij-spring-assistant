package dev.flikas.spring.boot.assistant.idea.plugin.metadata.source;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class MetadataFileIndex extends ScalarIndexExtension<String> {
  public static final String METADATA_FILE_NAME = "spring-configuration-metadata.json";
  public static final String METADATA_FILE = "META-INF/" + METADATA_FILE_NAME;
  public static final String ADDITIONAL_METADATA_FILE_NAME = "additional-spring-configuration-metadata.json";
  public static final String ADDITIONAL_METADATA_FILE = "META-INF/" + ADDITIONAL_METADATA_FILE_NAME;
  public static final String PLUGIN_INDEX_NAMESPACE = "dev.flikas.spring-boot-assistant.";
  public static final ID<String, Void> NAME = ID.create(
      PLUGIN_INDEX_NAMESPACE + MetadataFileIndex.class.getSimpleName());

  private static final String UNIQUE_KEY = "META";


  @NotNull
  public static Collection<VirtualFile> getFiles(@NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, UNIQUE_KEY, scope);
  }


  @Override
  public @NotNull ID<String, Void> getName() {
    return NAME;
  }


  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<String, Void> map(@NotNull FileContent inputData) {
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
        return Collections.singletonMap(UNIQUE_KEY, null);
      }
    };
  }


  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return file -> {
      if (!file.getName().equals(METADATA_FILE_NAME) && !file.getName().equals(ADDITIONAL_METADATA_FILE_NAME)) {
        return false;
      }
      if (file.getName().equals(ADDITIONAL_METADATA_FILE_NAME)) {
        VirtualFile parent = file.getParent();
        if (parent != null && parent.findChild(METADATA_FILE_NAME) != null) {
          // Some package has additional metadata file only, we have to load it,
          // otherwise, spring-configuration-processor should merge additional metadata to the main one,
          // thus, the additional metadata file should not be load.
          return false;
        }
      }
      if (file.isInLocalFileSystem()) {
        String path = file.getPath();
        return path.endsWith(METADATA_FILE) || path.endsWith(ADDITIONAL_METADATA_FILE);
      } else {
        String relPath = VfsUtilCore.getRelativePath(file, VfsUtilCore.getRootFile(file));
        return relPath != null && (relPath.equals(METADATA_FILE) || relPath.equals(ADDITIONAL_METADATA_FILE));
      }
    };
  }


  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }


  @Override
  public int getVersion() {
    return 0;
  }


  @Override
  public boolean dependsOnFileContent() {
    return false;
  }
}
