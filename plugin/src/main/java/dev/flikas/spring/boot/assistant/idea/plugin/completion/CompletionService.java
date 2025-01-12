package dev.flikas.spring.boot.assistant.idea.plugin.completion;

import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import dev.flikas.spring.boot.assistant.idea.plugin.documentation.HintDocumentationVirtualElement;
import dev.flikas.spring.boot.assistant.idea.plugin.documentation.MetadataItemVirtualElement;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataIndex;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataItem;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.NameTreeNode;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.PropertyHintValue;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.service.ModuleMetadataService;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiTypeUtils;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form.UNIFORM;

@Service(Service.Level.PROJECT)
public final class CompletionService {
  private final Project project;


  public CompletionService(Project project) {
    this.project = project;
  }


  public static CompletionService getInstance(Project project) {
    return project.getService(CompletionService.class);
  }


  /**
   * Retrieve candidates for configuration key completion.
   *
   * @param parentName  The context property name for querying, must be existed, such as 'spring.security', can be null or empty
   * @param queryString The user input for completion.
   * @return Collection of {@link LookupElement} that matches the query.
   */
  @NotNull
  public Collection<LookupElement> findSuggestionForKey(
      @NotNull Module module, @Nullable String parentName, String queryString) {
    Collection<MetadataItem> candidates = findProperty(module, parentName, queryString);
    if (!candidates.isEmpty()) {
      return candidates.stream().map(metaItem -> switch (metaItem) {
        case MetadataProperty property -> createLookupElement(parentName, property);
        case MetadataGroup group -> createLookupElement(parentName, group);
        default -> throw new IllegalStateException("Unexpected value: " + metaItem);
      }).filter(Objects::nonNull).toList();
    }
    // Maybe user is asking suggestion for a Map key
    MetadataProperty property = ModuleMetadataService.getInstance(module).getIndex().getProperty(parentName);
    if (property != null && property.isMapType()) {
      return filterHint(property.getKeyHintValues(), queryString).stream()
          .map(this::createLookupElement).toList();
    }
    return Collections.emptySet();
  }


  /**
   * Retrieve candidates for a property's value completion.
   *
   * @param propertyName The context property name for querying value, must be existed.
   * @param queryString  The user input for completion.
   * @return Collection of {@link LookupElement} that matches the query.
   */
  @NotNull
  public Collection<LookupElement> findSuggestionForValue(
      @NotNull Module module, @NotNull String propertyName, String queryString) {
    Collection<PropertyHintValue> hints = findHintForValue(module, propertyName, queryString);
    if (!hints.isEmpty()) {
      return hints.stream().map(this::createLookupElement).toList();
    }
    // Maybe we are looking for a Map's value suggestion
    String parentKey = PropertyName.adapt(propertyName).getParent().toString();
    if (StringUtils.isNotBlank(parentKey)) {
      MetadataIndex index = ModuleMetadataService.getInstance(module).getIndex();
      MetadataProperty parent = index.getProperty(parentKey);
      if (parent != null && parent.isMapType()) {
        hints = findHintForValue(module, parentKey, queryString);
        if (!hints.isEmpty()) {
          return hints.stream().map(this::createLookupElement).toList();
        }
      }
      // If we have an ancestor whose type is Map<String,?>, it can map to any depth of key,
      // so let's find to the ancestors till find it, and use its value's hint.
      parent = index.getNearestParentProperty(parentKey);
      if (parent != null && parent.getFullType().filter(t -> PsiTypeUtils.isValueMap(project, t)).isPresent()) {
        hints = findHintForValue(module, parent.getNameStr(), queryString);
        if (!hints.isEmpty()) {
          return hints.stream().map(this::createLookupElement).toList();
        }
      }
    }
    return Collections.emptySet();
  }


  private Collection<MetadataItem> findProperty(
      @NotNull Module module, @Nullable String parentName, String queryString) {
    if (parentName == null) parentName = "";
    NameTreeNode searchRoot = ModuleMetadataService.getInstance(module).getIndex().findInNameTrie(parentName.trim());
    if (searchRoot == null || searchRoot.isIndexed()) {
      // we can't provide suggestion for an indexed key, user has to create the sub element then ask for suggestion.
      return Collections.emptySet();
    }
    Collection<NameTreeNode> candidates = Collections.singleton(searchRoot);
    if (StringUtils.isNotBlank(queryString)) {
      PropertyName query = PropertyName.adapt(queryString);
      for (int i = 0; !candidates.isEmpty() && i < query.getNumberOfElements(); i++) {
        String qp = query.getElement(i, UNIFORM);
        candidates = candidates.parallelStream().filter(tn -> !tn.isIndexed()).map(NameTreeNode::getChildren)
            .map(trie -> trie.prefixMap(qp)).flatMap(m -> m.values().parallelStream()).collect(Collectors.toSet());
      }
    }
    // get all properties in candidates;
    Collection<NameTreeNode> nodes = candidates;
    Set<MetadataItem> result = new HashSet<>();
    while (!nodes.isEmpty()) {
      Set<NameTreeNode> nextNodes = new HashSet<>();
      for (NameTreeNode n : nodes) {
        if (n != searchRoot) {
          result.addAll(n.getData());
        }
        if (!n.isIndexed()) {
          // Suggestion should not contain indexes(Map or List), because it is hard to insert this suggestion to code.
          nextNodes.add(n);
        }
      }
      nodes = nextNodes.parallelStream().flatMap(tn -> tn.getChildren().values().stream()).collect(Collectors.toSet());
    }
    return result;
  }


  private @NotNull Collection<PropertyHintValue> findHintForValue(
      @NotNull Module module, @NotNull String propertyName, String queryString) {
    MetadataProperty property = ModuleMetadataService.getInstance(module).getIndex().getProperty(propertyName);
    if (property == null) return Collections.emptySet();
    return filterHint(property.getHintValues(), queryString);
  }


  private LookupElement createLookupElement(PropertyHintValue hintValue) {
    PsiElement psiElement = hintValue.getPsiElement();
    if (psiElement instanceof PsiVariable psiVariable) {
      return new VariableLookupItem(psiVariable).setInsertHandler(YamlValueInsertHandler.INSTANCE);
    } else if (psiElement instanceof PsiClass psiClass) {
      return new JavaPsiClassReferenceElement(psiClass).setInsertHandler(YamlValueInsertHandler.INSTANCE);
    } else if (psiElement instanceof PsiMethod psiMethod) {
      return new JavaMethodCallElement(psiMethod).setInsertHandler(YamlValueInsertHandler.INSTANCE);
    }
    LookupElementBuilder le = LookupElementBuilder.create(hintValue.getValue()).withIcon(hintValue.getIcon())
        .withPsiElement(new HintDocumentationVirtualElement(hintValue, PsiManager.getInstance(project)))
        .withInsertHandler(YamlValueInsertHandler.INSTANCE);
    if (StringUtils.isNotBlank(hintValue.getOneLineDescription())) {
      le = le.withTailText("(" + hintValue.getOneLineDescription() + ")", true);
    }
    return le;
  }


  private LookupElement createLookupElement(String propertyNameAncestors, MetadataProperty property) {
    ConfigurationMetadata.Property.Deprecation deprecation = property.getMetadata().getDeprecation();
    if (deprecation != null && deprecation.getLevel() == ConfigurationMetadata.Property.Deprecation.Level.ERROR) {
      // Fully unsupported property should not be included in suggestions
      return null;
    }
    LookupElementBuilder leb = LookupElementBuilder.create(removeParent(propertyNameAncestors, property.getNameStr()))
        .withIcon(property.getIcon().getSecond()).withPsiElement(new MetadataItemVirtualElement(property))
        .withStrikeoutness(deprecation != null).withInsertHandler(YamlKeyInsertHandler.INSTANCE);
    if (StringUtils.isNotBlank(property.getMetadata().getDescription())) {
      leb = leb.withTailText("(" + property.getMetadata().getDescription() + ")", true);
    }
    if (StringUtils.isNotBlank(property.getMetadata().getType())) {
      leb = leb.withTypeText(GenericUtil.shortenJavaType(property.getMetadata().getType()), true);
    }
    return leb;
  }


  private LookupElement createLookupElement(String propertyNameAncestors, MetadataGroup group) {
    return LookupElementBuilder.create(removeParent(propertyNameAncestors, group.getNameStr()))
        .withIcon(group.getIcon().getSecond()).withPsiElement(new MetadataItemVirtualElement(group))
        .withInsertHandler(YamlKeyInsertHandler.INSTANCE);
  }


  private String removeParent(String parent, String name) {
    PropertyName parentKey = PropertyName.adapt(parent);
    PropertyName key = PropertyName.adapt(name);
    assert parentKey.isAncestorOf(key) : "Invalid parent and child:" + parentKey + "," + key;
    return key.subName(parentKey.getNumberOfElements()).toString();
  }


  private Collection<PropertyHintValue> filterHint(Collection<PropertyHintValue> source, String queryString) {
    return source.stream()
        .filter(h -> h.getValue().toLowerCase().startsWith(queryString.toLowerCase()))
        .toList();
  }
}
