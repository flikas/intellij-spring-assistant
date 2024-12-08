package dev.flikas.spring.boot.assistant.idea.plugin.metadata.source;

import dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.ElementType;
import org.junit.jupiter.api.Test;

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.ElementType.INDEXED;
import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.ElementType.NUMERICALLY_INDEXED;
import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form.DASHED;
import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form.ORIGINAL;
import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.Form.UNIFORM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyNameTest {

  @Test
  void of() {
    PropertyName name = PropertyName.of("spring.application.name");
    assertEquals(3, name.getNumberOfElements());
    assertEquals("spring", name.getElement(0, UNIFORM));
    assertEquals("application", name.getElement(1, UNIFORM));
    assertEquals("name", name.getElement(2, UNIFORM));

    name = PropertyName.of("spring.logging.level[org.springframework]");
    assertEquals(4, name.getNumberOfElements());
    assertEquals("spring", name.getElement(0, UNIFORM));
    assertEquals("logging", name.getElement(1, UNIFORM));
    assertEquals("level", name.getElement(2, UNIFORM));
    assertEquals("org.springframework", name.getElement(3, UNIFORM));

    name = PropertyName.of("spring.instance[0].name");
    assertEquals(4, name.getNumberOfElements());
    assertEquals("spring", name.getElement(0, UNIFORM));
    assertEquals("instance", name.getElement(1, UNIFORM));
    assertEquals("0", name.getElement(2, UNIFORM));
    assertEquals("name", name.getElement(3, UNIFORM));
  }


  @Test
  void ofIfValid() {
    PropertyName name = PropertyName.ofIfValid("spring.application.name");
    assertNotNull(name);
    name = PropertyName.ofIfValid("spring.application.NAME");
    assertNull(name);
  }


  @Test
  void ofCamelCase() {
    PropertyName name = PropertyName.ofCamelCase("spring.tomcat.trustAll_123");
    assertEquals(3, name.getNumberOfElements());
    assertEquals("spring", name.getElement(0, DASHED));
    assertEquals("tomcat", name.getElement(1, DASHED));
    assertEquals("trust-all-123", name.getElement(2, ORIGINAL));
  }


  @Test
  void toCamelCase() {
    assertEquals("aaXxYy22Aa", PropertyName.toCamelCase("aa-xx-yy22-aa"));
  }


  @Test
  void appendAnyMapKey() {
    PropertyName name = PropertyName.of("spring.tomcat.trust-all").appendAnyMapKey().append("name");
    assertEquals("spring.tomcat.trust-all[*].name", name.toString());
  }


  @Test
  void appendAnyNumericalIndex() {
    PropertyName name = PropertyName.of("spring.instances").appendAnyNumericalIndex().append("name");
    assertEquals("spring.instances[#].name", name.toString());
  }


  @Test
  void append() {
    PropertyName name = PropertyName.of("spring.tomcat.trust-all").append("");
    assertEquals("spring.tomcat.trust-all", name.toString());
  }


  @Test
  void getParent() {
    PropertyName name = PropertyName.of("spring.tomcat.trust-all[*].name");
    assertEquals("spring.tomcat.trust-all[*]", name.getParent().toString());

    name = PropertyName.of("spring");
    assertEquals("", name.getParent().toString());
  }


  @Test
  void chop() {
    PropertyName name = PropertyName.of("spring.tomcat.trust-all[*].name");
    assertEquals("spring.tomcat.trust-all[*].name", name.chop(5).toString());
  }


  @Test
  void subName() {
    PropertyName name = PropertyName.of("spring.tomcat.trust-all[*].name");
    assertEquals("name", name.subName(4).toString());

    assertEquals("spring.tomcat.trust-all[*].name", name.subName(0).toString());
    assertEquals("", name.subName(5).toString());

    name = PropertyName.of("discovery.instances[*][#]");
    PropertyName sub = name.subName(2);
    assertEquals("[*][#]", sub.toString());
    assertTrue(sub.isIndexed(0));
    assertTrue(sub.isAnyNonNumericIndex(0));
  }


  @Test
  void isParentOf() {
    PropertyName child = PropertyName.of("spring.tomcat.trust-all[*].name");
    PropertyName parent = PropertyName.of("spring.tomcat.trust-all[*]");
    assertTrue(parent.isParentOf(child));
  }


  @Test
  void isAncestorOf() {
    PropertyName child = PropertyName.of("spring.tomcat.trust-all[*].name");
    PropertyName parent = PropertyName.of("spring");
    assertTrue(parent.isAncestorOf(child));
  }


  @Test
  void compare() {
    PropertyName name = PropertyName.of("spring");
    assertEquals(0, name.compare("abc", INDEXED, "*", INDEXED));
    assertEquals(0, name.compare("*", INDEXED, "org.springframework", INDEXED));
    assertEquals(0, name.compare("11", NUMERICALLY_INDEXED, "#", INDEXED));
    assertEquals(0, name.compare("#", INDEXED, "999", NUMERICALLY_INDEXED));
    assertNotEquals(0, name.compare("#", INDEXED, "*", INDEXED));
    assertEquals(-1, name.compare(null, null, "trust-all", ElementType.DASHED));
    assertEquals(1, name.compare("spring", ElementType.UNIFORM, null, null));
    assertEquals(-1, name.compare("spring", ElementType.UNIFORM, "tomcat", ElementType.UNIFORM));
  }


  @Test
  void defaultElementEquals() {
    PropertyName n1 = PropertyName.of("spring.instance[2].name");
    PropertyName n2 = PropertyName.of("spring.instance[#].value");
    PropertyName n3 = PropertyName.of("spring.instance[*].type");

    assertTrue(n1.defaultElementEquals(n1.elements, n2.elements, 2));
    assertTrue(n1.defaultElementEquals(n2.elements, n1.elements, 2));
    assertFalse(n1.defaultElementEquals(n1.elements, n2.elements, 3));
    assertFalse(n1.defaultElementEquals(n2.elements, n1.elements, 3));

    assertFalse(n1.defaultElementEquals(n1.elements, n3.elements, 2));
    assertFalse(n1.defaultElementEquals(n3.elements, n1.elements, 2));
    assertFalse(n1.defaultElementEquals(n1.elements, n3.elements, 3));
    assertFalse(n1.defaultElementEquals(n3.elements, n1.elements, 3));

    assertFalse(n1.defaultElementEquals(n2.elements, n3.elements, 2));
    assertFalse(n1.defaultElementEquals(n3.elements, n2.elements, 2));
    assertFalse(n1.defaultElementEquals(n2.elements, n3.elements, 3));
    assertFalse(n1.defaultElementEquals(n3.elements, n2.elements, 3));
  }


  @Test
  void testEquals() {
    assertEquals(PropertyName.of("spring.kafka.admin.ssl.key-store-location"),
        PropertyName.of("spring.kafka.admin.ssl.keystore-location"));

    assertEquals(PropertyName.of("spring.instance[2].name"), PropertyName.of("spring.instance[#].name"));
    assertEquals(PropertyName.of("spring.instance[#].name"), PropertyName.of("spring.instance[15].name"));
    assertEquals(PropertyName.of("spring.instance[*].name"), PropertyName.of("spring.instance[spring.tomcat].name"));
    assertEquals(PropertyName.of("spring.instance[a].name"), PropertyName.of("spring.instance[*].name"));
    assertEquals(PropertyName.of("spring.instance.a.name"), PropertyName.of("spring.instance[*].name"));

    assertNotEquals(PropertyName.of("spring.instance[2].name"), PropertyName.of("spring.instance[0].name"));
    assertNotEquals(PropertyName.of("spring.instance[#].name"), PropertyName.of("spring.instance[*].name"));
    assertNotEquals(PropertyName.of("spring.instance[*].name"), PropertyName.of("spring.instance[1].name"));
    assertNotEquals(PropertyName.of("spring.instance[#].name"), PropertyName.of("spring.instance[a].name"));

    assertEquals(PropertyName.of("[*]"), PropertyName.of("any-string"));
    assertEquals(PropertyName.of("[#]"), PropertyName.of("[7]"));
  }


  @Test
  void testHashCode() {
    PropertyName n1 = PropertyName.of("spring.instance[2].name");
    PropertyName n2 = PropertyName.of("spring.instance[#].name");
    PropertyName n3 = PropertyName.of("spring.instance[*].name");

    assertEquals(n1.hashCode(), n2.hashCode());
    assertEquals(n1.hashCode(), n3.hashCode());
  }
}