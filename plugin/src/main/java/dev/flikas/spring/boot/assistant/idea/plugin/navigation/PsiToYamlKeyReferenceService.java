package dev.flikas.spring.boot.assistant.idea.plugin.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import dev.flikas.spring.boot.assistant.idea.plugin.filetype.SpringBootConfigurationYamlFileType;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service(Service.Level.PROJECT)
public final class PsiToYamlKeyReferenceService {
  private final Project project;
  private IndexHolder indexHolder;


  public PsiToYamlKeyReferenceService(Project project) {
    this.project = project;
  }


  @NotNull
  public Collection<YamlKeyToNullReference> findReference(PsiElement psiElement) {
    if (!(psiElement instanceof PsiField || psiElement instanceof PsiClass)) {
      return Collections.emptySet();
    }
    Map<String, Set<YamlKeyToNullReference>> index = refreshIndex();
    if (index == null) {
      DumbService.getInstance(project)
          .showDumbModeNotificationForFunctionality("Index is not ready", DumbModeBlockedFunctionality.FindUsages);
      return Collections.emptySet();
    }
    return index.getOrDefault(getCanonicalName(psiElement), Collections.emptySet());
  }


  private Map<String, Set<YamlKeyToNullReference>> refreshIndex() {
    Collection<VirtualFile> files = DumbService.getInstance(project).runReadActionInSmartMode(
        () -> FileTypeIndex.getFiles(SpringBootConfigurationYamlFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)));

    if (this.indexHolder == null || !this.indexHolder.isEquals(files)) {
      synchronized (this) {
        if (this.indexHolder == null || !this.indexHolder.isEquals(files)) {
          this.indexHolder = new IndexHolder(files);
        }
      }
    }
    return this.indexHolder.getIndex();
  }


  private void indexYamlKey(
      Map<String, Set<YamlKeyToNullReference>> index, ModuleMetadataService metadataService, YAMLKeyValue kv) {
    if (kv.getKey() == null) return;
    String fullName = ReadAction.compute(() -> YAMLUtil.getConfigFullName(kv));
    // find if any property matches this key
    MetadataProperty property = metadataService.getIndex().getProperty(fullName);
    if (property != null) {
      // It is wierd but ReferencesSearch uses the 'source element' not the 'target element' of the returned PsiReference.
      // So here we create a YamlKeyToNullReference whose source is the target YamlKey.
      property.getSourceField().ifPresent(field -> {
        index.computeIfAbsent(getCanonicalName(field), key -> new ConcurrentSkipListSet<>())
            .add(new YamlKeyToNullReference(kv));
      });
    }
    // find if any group matches this key
    MetadataGroup group = metadataService.getIndex().getGroup(fullName);
    if (group != null) {
      group.getType().ifPresent(type -> {
        index.computeIfAbsent(getCanonicalName(type), key -> new ConcurrentSkipListSet<>())
            .add(new YamlKeyToNullReference(kv));
      });
    }
    //recursive into sub-keys
    @Nullable YAMLValue val = kv.getValue();
    if (val instanceof YAMLMapping) {
      ((YAMLMapping) val).getKeyValues().forEach(k -> indexYamlKey(index, metadataService, k));
    } else if (val instanceof YAMLSequence) {
      ((YAMLSequence) val).getItems().stream()
          .flatMap(item -> ReadAction.compute(item::getKeysValues).stream())
          .forEach(k -> indexYamlKey(index, metadataService, k));
    }
  }


  @Nullable
  private static String getCanonicalName(PsiElement element) {
    if (element instanceof PsiField) {
      PsiClass containingClass = ReadAction.compute(() -> ((PsiField) element).getContainingClass());
      if (containingClass == null) {
        //Not a standard java field, should not happen
        return null;
      }
      return ReadAction.compute(() -> containingClass.getQualifiedName() + "." + ((PsiField) element).getName());
    } else if (element instanceof PsiClass) {
      return ReadAction.compute(() -> ((PsiClass) element).getQualifiedName());
    } else {
      throw new UnsupportedOperationException();
    }
  }


  private class IndexHolder extends UserDataHolderBase {
    private final Set<String> fileUrls;
    private final Collection<VirtualFile> files;


    private IndexHolder(Collection<VirtualFile> files) {
      this.files = files;
      this.fileUrls = files.stream().map(VirtualFile::getUrl)
          .collect(Collectors.toSet());
    }


    public boolean isEquals(Iterable<VirtualFile> files) {
      return StreamSupport.stream(files.spliterator(), false).map(VirtualFile::getUrl).collect(Collectors.toSet())
          .equals(this.fileUrls);
    }


    /**
     * @return A map for {@linkplain #getCanonicalName(PsiElement) canonical} name of a field or class to the YamlKeyValues in application.yaml
     */
    public Map<String, Set<YamlKeyToNullReference>> getIndex() {
      if (files.isEmpty()) return Collections.emptyMap();
      return CachedValuesManager.getManager(project).getCachedValue(this, () -> {
        // In this block, we return null at any error, makes this function is **Result equivalence**.
        Map<String, Set<YamlKeyToNullReference>> index = new HashMap<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
          if (!file.isValid()) return null;
          PsiFile psiFile = ReadAction.compute(() -> psiManager.findFile(file));
          if (!(psiFile instanceof YAMLFile)) return null;
          Module module = ModuleUtil.findModuleForFile(psiFile);
          if (module == null) return null;
          ModuleMetadataService metadataService = module.getService(ModuleMetadataService.class);
          Collection<YAMLKeyValue> topLevelKeys = ReadAction.compute(() -> ((YAMLFile) psiFile)
              .getDocuments().stream()
              .map(YAMLDocument::getTopLevelValue)
              .filter(YAMLMapping.class::isInstance)
              .flatMap(yv -> ((YAMLMapping) yv).getKeyValues().stream())
              .toList());
          for (YAMLKeyValue kv : topLevelKeys) {
            indexYamlKey(index, metadataService, kv);
          }
        }
        return CachedValueProvider.Result.create(index, files);
      });
    }
  }
}
