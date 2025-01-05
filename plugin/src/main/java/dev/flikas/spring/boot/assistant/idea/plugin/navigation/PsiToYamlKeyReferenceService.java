package dev.flikas.spring.boot.assistant.idea.plugin.navigation;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import dev.flikas.spring.boot.assistant.idea.plugin.filetype.SpringBootConfigurationYamlFileType;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
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

@Service(Service.Level.PROJECT)
public final class PsiToYamlKeyReferenceService {
  private final Project project;
  /**
   * A map for {@linkplain #getCanonicalName(PsiElement) canonical} name of a field or class to the YamlKeyValues in application.yaml
   */
  private Map<String, Set<YamlKeyToNullReference>> index = new HashMap<>();


  public PsiToYamlKeyReferenceService(Project project) {
    this.project = project;
    //FIXME refresh index while the yaml file changed or added or removed.
    //TODO Use platform index mechanism instead.
  }


  @NotNull
  public Collection<YamlKeyToNullReference> findReference(PsiElement psiElement) {
    if (!(psiElement instanceof PsiField || psiElement instanceof PsiClass)) {
      return Collections.emptySet();
    }
    return index.getOrDefault(getCanonicalName(psiElement), Collections.emptySet());
  }


  private synchronized void reindex() {
    Map<String, Set<YamlKeyToNullReference>> index = new HashMap<>();
    Collection<VirtualFile> files = DumbService.getInstance(project)
        .runReadActionInSmartMode(() -> FileTypeIndex.getFiles(
            SpringBootConfigurationYamlFileType.INSTANCE, GlobalSearchScope.projectScope(project)));
    PsiManager psiManager = PsiManager.getInstance(project);
    for (VirtualFile file : files) {
      if (!file.isValid()) continue;
      PsiFile psiFile = psiManager.findFile(file);
      if (!(psiFile instanceof YAMLFile)) continue;
      Module module = ModuleUtil.findModuleForFile(psiFile);
      if (module == null) continue;
      ModuleMetadataService metadataService = module.getService(ModuleMetadataService.class);
      for (YAMLKeyValue kv : YAMLUtil.getTopLevelKeys((YAMLFile) psiFile)) {
        indexYamlKey(index, metadataService, kv);
      }
    }
    this.index = index;
  }


  private void indexYamlKey(
      Map<String, Set<YamlKeyToNullReference>> index, ModuleMetadataService metadataService, YAMLKeyValue kv
  ) {
    ProgressManager.checkCanceled();
    if (kv.getKey() == null) return;
    String fullName = YAMLUtil.getConfigFullName(kv);
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
      ((YAMLSequence) val).getItems().stream().flatMap(item -> item.getKeysValues().stream())
          .forEach(k -> indexYamlKey(index, metadataService, k));
    }
  }


  @Nullable
  private static String getCanonicalName(PsiElement element) {
    if (element instanceof PsiField) {
      PsiClass containingClass = ((PsiField) element).getContainingClass();
      if (containingClass == null) {
        //Not a standard java field, should not happen
        return null;
      }
      return containingClass.getQualifiedName() + "." + ((PsiField) element).getName();
    } else if (element instanceof PsiClass) {
      return ((PsiClass) element).getQualifiedName();
    } else {
      throw new UnsupportedOperationException();
    }
  }
}
