package dev.flikas.spring.boot.assistant.idea.plugin.metadata.index;

import com.intellij.openapi.diagnostic.Logger;
import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.PropertyName;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.commons.collections4.trie.UnmodifiableTrie;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form.UNIFORM;

@Data
public class NameTreeNode {
  private static final Logger LOG = Logger.getInstance(NameTreeNode.class);
  private final PatriciaTrie<NameTreeNode> children = new PatriciaTrie<>();
  private final List<MetadataItem> data = new LinkedList<>();
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private IndexedType indexedType = IndexedType.NONE;


  public static NameTreeNode merge(NameTreeNode n1, NameTreeNode n2) {
    NameTreeNode dst = new NameTreeNode();
    if (!n1.children.isEmpty() && !n2.children.isEmpty()) {
      assert n1.indexedType == n2.indexedType;
      dst.indexedType = n1.indexedType;
    } else if (!n1.children.isEmpty()) {
      dst.indexedType = n1.indexedType;
    } else if (!n2.children.isEmpty()) {
      dst.indexedType = n2.indexedType;
    }
    dst.data.addAll(n1.data);
    dst.data.addAll(n2.data);
    n1.children.forEach((k, v) -> dst.children.merge(k, v, NameTreeNode::merge));
    n2.children.forEach((k, v) -> dst.children.merge(k, v, NameTreeNode::merge));
    return dst;
  }


  public Trie<String, NameTreeNode> getChildren() {
    return UnmodifiableTrie.unmodifiableTrie(children);
  }


  public boolean isIndexed() {
    return this.indexedType != IndexedType.NONE;
  }


  @Nullable
  public NameTreeNode findChild(PropertyName name) {
    if (name.isEmpty()) return this;
    NameTreeNode child;
    if (this.indexedType == IndexedType.NON_NUMERIC) {
      assert this.children.size() == 1;
      child = this.children.values().iterator().next();
    } else if (this.indexedType == IndexedType.NUMERIC) {
      assert this.children.size() == 1;
      if (name.isNumericIndex(0)) {
        child = this.children.values().iterator().next();
      } else {
        return null;
      }
    } else {
      child = this.children.get(name.getElement(0, UNIFORM));
    }
    if (child == null) {
      return null;
    } else {
      return child.findChild(name.subName(1));
    }
  }


  public void addChild(PropertyName name, MetadataItem value) {
    if (name.isEmpty()) {
      this.data.add(value);
      return;
    }
    String key;
    if (name.isAnyNonNumericIndex(0)) {
      key = "*";
      this.indexedType = IndexedType.NON_NUMERIC;
      ensureAtMostOneChild(key);
    } else if (name.isAnyNumericIndex(0)) {
      key = "#";
      this.indexedType = IndexedType.NUMERIC;
      ensureAtMostOneChild(key);
    } else {
      key = name.getElement(0, UNIFORM);
    }
    NameTreeNode child = this.children.computeIfAbsent(key, k -> new NameTreeNode());
    child.addChild(name.subName(1), value);
  }


  private void ensureAtMostOneChild(String key) {
    if (!this.children.isEmpty() && !this.children.keySet().equals(Collections.singleton(key))) {
      LOG.warn("There should be at most one child of key \"" + key + "\", but children are: " + this.children);
      this.children.clear();
    }
  }


  enum IndexedType {NUMERIC, NON_NUMERIC, NONE}
}
