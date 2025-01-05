package dev.flikas.spring.boot.assistant.idea.plugin.documentation;

import com.intellij.icons.AllIcons;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJvmMember;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataGroup;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiElementUtils;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.intellij.lang.documentation.DocumentationMarkup.CONTENT_ELEMENT;
import static com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_ELEMENT;

@SuppressWarnings("UnstableApiUsage")
public class GroupDocumentationTarget implements ProjectDocumentationTarget {
  private final MetadataGroup group;
  private final @NotNull Project project;


  public GroupDocumentationTarget(MetadataGroup group) {
    this.group = group;
    this.project = group.getIndex().project();
  }


  @Override
  public @NotNull Project getProject() {
    return this.project;
  }


  @Override
  public @NotNull TargetPresentation computePresentation() {
    String locationText = group
        .getSourceType()
        .map(PsiElement::getContainingFile)
        .map(PsiFile::getVirtualFile)
        .map(f -> ProjectFileIndex.getInstance(project).getOrderEntriesForFile(f))
        .map(l -> l.stream().map(OrderEntry::getPresentableName).distinct().collect(Collectors.joining(", ")))
        .orElse(null);
    OrderEntry a;
    return TargetPresentation.builder(group.getNameStr())
        .icon(group.getIcon().getSecond())
        .containerText(group.getMetadata().getSourceType())
        .locationText(locationText, AllIcons.Nodes.Library)
        .presentation();
  }


  @Override
  public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
    return Pointer.delegatingPointer(Pointer.hardPointer(group), GroupDocumentationTarget::new);
  }


  @Override
  public @Nullable DocumentationResult computeDocumentation() {
    ConfigurationMetadata.Group meta = group.getMetadata();
    HtmlBuilder doc = new HtmlBuilder();
    // Otherwise, format for the documentation is as follows
    /*
     * {@link com.acme.Generic}<{@link com.acme.Class1}, {@link com.acme.Class2}>
     * a.b.c
     * ---
     * Long description
     */
    HtmlChunk.Element def = DEFINITION_ELEMENT;
    Optional<PsiClass> type = group.getType();
    if (type.isPresent()) {
      def = def.addRaw(PsiElementUtils.createLinkForDoc(type.get())).addText("\n");
    }
    Pair<String, Icon> icon = group.getIcon();
    def = def.children(
        HtmlChunk.icon(icon.getFirst(), icon.getSecond()),
        HtmlChunk.nbsp(),
        HtmlChunk.text(group.getNameStr()));
    doc.append(def);

    doc.append(CONTENT_ELEMENT.addRaw(group.getRenderedDescription()));

    // Append "Declared at" section as follows:
    // Declared at: {@link com.acme.GenericRemovedClass#method}> <-- only for groups with method info
    group.getSourceMethod()
        .map(m -> (PsiJvmMember) m)
        .or(group::getSourceType)
        .map(PsiElementUtils::createLinkForDoc)
        .filter(StringUtils::isNotBlank)
        .ifPresent(link -> doc.hr().append(DocumentationMarkup.BOTTOM_ELEMENT
            .child(DocumentationMarkup.GRAYED_ELEMENT.addText("Declared at: "))
            .addRaw(link)));
    return DocumentationResult.documentation(doc.toString());
  }
}
