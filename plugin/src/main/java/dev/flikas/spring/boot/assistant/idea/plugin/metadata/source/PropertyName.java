package dev.flikas.spring.boot.assistant.idea.plugin.metadata.source;

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import static dev.flikas.spring.boot.assistant.idea.plugin.metadata.source.ConfigurationPropertyName.ElementType.NUMERICALLY_INDEXED;

/**
 * A {@linkplain ConfigurationPropertyName} that accepts wildcard index.
 */
@Immutable
public class PropertyName extends ConfigurationPropertyName {
  public static final PropertyName EMPTY = new PropertyName(Elements.EMPTY);

  private static final Set<Character> SEPARATORS = Set.of('-', '_');

  private int hashCode;


  private PropertyName(Elements elements) {
    super(elements);
  }


  /**
   * @see ConfigurationPropertyName#of(CharSequence)
   */
  public static PropertyName of(String propertyName) {
    return new PropertyName(elementsOf(propertyName.trim(), false));
  }


  /**
   * @see ConfigurationPropertyName#ofIfValid(CharSequence)
   */
  public static PropertyName ofIfValid(String propertyName) {
    Elements elements = elementsOf(propertyName.trim(), true);
    return elements != null ? new PropertyName(elements) : null;
  }


  public static PropertyName ofCamelCase(String camelCase) {
    return of(toKebabCase(camelCase));
  }


  /**
   * @see ConfigurationPropertyName#adapt(CharSequence, char, Function)
   */
  public static PropertyName adapt(String propertyName) {
    return new PropertyName(adapt(propertyName.trim(), '.').elements);
  }


  public static String toCamelCase(String kebabCase) {
    char[] dashedName = kebabCase.toCharArray();
    char[] fieldName = new char[dashedName.length];
    int j = 0;
    boolean upperNeeded = false;
    for (char c : dashedName) {
      if (Character.isJavaIdentifierPart(c)) {
        if (upperNeeded) {
          c = Character.toUpperCase(c);
          upperNeeded = false;
        }
        fieldName[j++] = c;
      } else {
        upperNeeded = true;
      }
    }
    return new String(fieldName, 0, j);
  }


  public static String toKebabCase(String camelCase) {
    StringBuilder dashed = new StringBuilder();
    Character previous = null;
    for (int i = 0; i < camelCase.length(); i++) {
      Character current = camelCase.charAt(i);
      if (SEPARATORS.contains(current)) {
        dashed.append("-");
      } else if (Character.isUpperCase(current) && previous != null && !SEPARATORS.contains(previous)) {
        dashed.append("-").append(current);
      } else {
        dashed.append(current);
      }
      previous = current;

    }
    return dashed.toString().toLowerCase(Locale.ENGLISH);
  }


  /**
   * @return a new PropertyName that appends a map key wildcard after this.
   */
  public PropertyName appendAnyMapKey() {
    return append("[*]");
  }


  /**
   * @return a new PropertyName that appends a numeric index wildcard after this.
   */
  public PropertyName appendAnyNumericalIndex() {
    return append("[#]");
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public PropertyName append(String suffix) {
    if (StringUtils.isBlank(suffix)) {
      return this;
    } else {
      Elements additionalElements = probablySingleElementOf(suffix);
      return new PropertyName(this.elements.append(additionalElements));
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public PropertyName getParent() {
    int numberOfElements = getNumberOfElements();
    return (numberOfElements <= 1) ? EMPTY : chop(numberOfElements - 1);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public PropertyName chop(int size) {
    if (size >= getNumberOfElements()) {
      return this;
    } else {
      return new PropertyName(this.elements.chop(size));
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public PropertyName subName(int offset) {
    return new PropertyName(super.subName(offset).elements);
  }


  public boolean isAnyNumericIndex(int elementIndex) {
    return isIndexed(elementIndex) && elements.get(elementIndex).equals("#");
  }


  public boolean isAnyNonNumericIndex(int elementIndex) {
    return isIndexed(elementIndex) && elements.get(elementIndex).equals("*");
  }


  @Override
  protected int compare(String e1, ElementType type1, String e2, ElementType type2) {
    if (e1 != null && e2 != null
        && (e1.equals("*") && !e2.equals("#")
                || e2.equals("*") && !e1.equals("#")
                || e1.equals("#") && type2 == NUMERICALLY_INDEXED
                || e2.equals("#") && type1 == NUMERICALLY_INDEXED)) {
      return 0;
    }
    return super.compare(e1, type1, e2, type2);
  }


  @Override
  boolean defaultElementEquals(Elements e1, Elements e2, int i) {
    ElementType type1 = e1.getType(i);
    ElementType type2 = e2.getType(i);
    if (e1.get(i).equals("*") && type2 != NUMERICALLY_INDEXED && !e2.get(i).equals("#")
        || e2.get(i).equals("*") && type1 != NUMERICALLY_INDEXED && !e1.get(i).equals("#")
        || type1 == NUMERICALLY_INDEXED && e2.get(i).equals("#")
        || type2 == NUMERICALLY_INDEXED && e1.get(i).equals("#")) {
      return true;
    }
    return super.defaultElementEquals(e1, e2, i);
  }


  /**
   * Because we support wildcard indexes, this method has been overwritten to ignore any element within '[]'.
   */
  @Override
  public int hashCode() {
    //Because we support map key wildcards(a[b] == a.b), we can do nothing but this.
    Elements elements = this.elements;
    if (this.hashCode == 0 && elements.getSize() != 0) {
      this.hashCode = elements.getSize();
    }
    return this.hashCode;
  }
}
