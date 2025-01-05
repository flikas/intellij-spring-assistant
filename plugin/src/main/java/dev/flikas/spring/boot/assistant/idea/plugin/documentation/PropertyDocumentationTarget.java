package dev.flikas.spring.boot.assistant.idea.plugin.documentation;

import com.intellij.icons.AllIcons;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.index.MetadataProperty;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationMetadata.Property.Deprecation;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.PsiElementUtils;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.intellij.lang.documentation.DocumentationMarkup.BOTTOM_ELEMENT;
import static com.intellij.lang.documentation.DocumentationMarkup.CONTENT_ELEMENT;
import static com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_ELEMENT;
import static com.intellij.lang.documentation.DocumentationMarkup.GRAYED_ELEMENT;
import static com.intellij.lang.documentation.DocumentationMarkup.SECTIONS_TABLE;
import static com.intellij.lang.documentation.DocumentationMarkup.SECTION_CONTENT_CELL;
import static com.intellij.lang.documentation.DocumentationMarkup.SECTION_HEADER_CELL;

@SuppressWarnings("UnstableApiUsage")
public class PropertyDocumentationTarget implements ProjectDocumentationTarget {
  private final MetadataProperty property;
  private final Project project;


  public PropertyDocumentationTarget(MetadataProperty property) {
    this.property = property;
    this.project = property.getIndex().project();
  }


  @Override
  public Project getProject() {
    return this.project;
  }


  @Override
  public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
    return Pointer.delegatingPointer(Pointer.hardPointer(property), PropertyDocumentationTarget::new);
  }


  @Override
  public @NotNull TargetPresentation computePresentation() {
    String locationText = property.getSourceType().map(PsiElement::getContainingFile).map(PsiFile::getVirtualFile)
        .map(f -> ProjectFileIndex.getInstance(project).getOrderEntriesForFile(f))
        .map(l -> l.stream().map(OrderEntry::getPresentableName).distinct().collect(Collectors.joining(", ")))
        .orElse(null);

    return TargetPresentation.builder(property.getNameStr())
        .icon(property.getIcon().getSecond())
        .containerText(property.getMetadata().getSourceType())
        .locationText(locationText, AllIcons.Nodes.Library)
        .presentation();
  }


  @Override
  public @Nullable DocumentationResult computeDocumentation() {
    // Format for the documentation is as follows
    /*
     * <p><b>a.b.c</b> ({@link com.acme.Generic}<{@link com.acme.Class1}, {@link com.acme.Class2}>)</p>
     * <p><em>Default Value</em> default value</p>
     * <p>Long description</p>
     * or of this type
     * <p><b>Type</b> {@link com.acme.Array}[]</p>
     * <p><b>Declared at</b>{@link com.acme.GenericRemovedClass#method}></p> <-- only for groups with method info
     * <b>WARNING:</b>
     * @deprecated Due to something something. Replaced by <b>c.d.e</b>
     */
    HtmlBuilder doc = new HtmlBuilder();
    HtmlChunk.Element def = DEFINITION_ELEMENT;
    Optional<PsiType> propertyType = property.getFullType();
    if (propertyType.isPresent()) {
      StringBuilder typeHtml = new StringBuilder();
      GenericUtil.updateClassNameAsJavadocHtml(typeHtml, propertyType.get().getCanonicalText());
      def = def.addRaw(typeHtml.toString()).child(HtmlChunk.br());
    }
    Pair<String, Icon> icon = property.getIcon();
    def = def.child(HtmlChunk.icon(icon.getFirst(), icon.getSecond()))
        .child(HtmlChunk.nbsp())
        .addText(property.getNameStr());
    Object defaultValue = property.getMetadata().getDefaultValue();
    if (defaultValue != null) {
      def = def.addText(" = ").addText(String.valueOf(defaultValue));
    }
    doc.append(def);

    HtmlChunk.Element body = CONTENT_ELEMENT;
    Deprecation deprecation = property.getMetadata().getDeprecation();
    if (deprecation != null) {
      HtmlChunk.Element dpc = CONTENT_ELEMENT;
      dpc = dpc.child(HtmlChunk.text(deprecation.getLevel() == Deprecation.Level.ERROR
          ? "ERROR: DO NOT USE THIS PROPERTY AS IT IS COMPLETELY UNSUPPORTED"
          : "WARNING: PROPERTY IS DEPRECATED").bold());
      HtmlChunk.Element table = SECTIONS_TABLE;
      if (deprecation.getReason() != null) {
        table = table.child(HtmlChunk.tag("tr")
            .children(SECTION_HEADER_CELL.addText("Reason:"), SECTION_CONTENT_CELL.addText(deprecation.getReason())));
      }
      if (deprecation.getReplacement() != null) {
        table = table.children(HtmlChunk.tag("tr").children(SECTION_HEADER_CELL.addText("Replaced by:"),
            SECTION_CONTENT_CELL.addText(deprecation.getReplacement())));
      }
      if (!table.isEmpty()) {
        dpc = dpc.child(table);
      }
      dpc = dpc.child(HtmlChunk.hr());
      body = body.child(dpc);
    }

    body = body.addRaw(property.getRenderedDescription());

    if (defaultValue != null) {
      body = body.child(SECTIONS_TABLE.child(SECTION_HEADER_CELL.addText("Default value:"))
          .child(SECTION_CONTENT_CELL.addText(String.valueOf(defaultValue))));
    }
    doc.append(body);

    property.getSourceField().ifPresent(field -> doc.hr().append(
        BOTTOM_ELEMENT.child(GRAYED_ELEMENT.addText("Declared at: "))
            .addRaw(PsiElementUtils.createLinkForDoc(field)))
    );

    return DocumentationResult.documentation(doc.toString());
  }
}
