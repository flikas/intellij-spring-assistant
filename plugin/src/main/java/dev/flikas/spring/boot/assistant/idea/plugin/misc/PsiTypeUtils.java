package dev.flikas.spring.boot.assistant.idea.plugin.misc;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import jakarta.validation.constraints.Size;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

@UtilityClass
public class PsiTypeUtils {
  private static final Logger log = Logger.getInstance(PsiTypeUtils.class);


  public static PsiClassType getJavaLangString(Project project) {
    return PsiType.getJavaLangString(PsiManager.getInstance(project), ProjectScope.getLibrariesScope(project));
  }


  public static PsiClassType getJavaTypeByName(Project project, String typeName) {
    return PsiType.getTypeByName(typeName, project, ProjectScope.getLibrariesScope(project));
  }


  @Nullable
  public static PsiClass findClass(Project project, String sourceType) {
    JavaPsiFacade jpf = JavaPsiFacade.getInstance(project);
    return ReadAction.compute(() ->
        jpf.findClass(sourceType.trim().replace('$', '.'), GlobalSearchScope.allScope(project)));
  }


  @Nullable
  public static PsiClass resolveClassInType(@Nullable PsiType type) {
    return ReadAction.compute(() -> PsiUtil.resolveClassInType(type));
  }


  public static String getCanonicalTextOfType(PsiType type) {
    return ReadAction.compute(type::getCanonicalText);
  }


  /**
   * @return true if type can be converted from a single String.
   */
  public static boolean isValueType(@Nullable PsiType type) {
    if (type == null) return false;
    // From Spring 'org.springframework.boot.convert.ApplicationConversionService'
    return ReadAction.compute(() -> isPhysical(type)
        && (TypeConversionUtil.isAssignableFromPrimitiveWrapper(type)
                || TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(type)
                || TypeConversionUtil.isEnumType(type)
                || isClassNameEquals(type, CommonClassNames.JAVA_LANG_STRING)
                || isClassNameEquals(type, CommonClassNames.JAVA_LANG_CLASS)
                || isClassNameEquals(type, CommonClassNames.JAVA_NIO_CHARSET_CHARSET)
                || isClassNameEquals(type, "java.util.Locale")
                || isClassNameEquals(type, "java.nio.charset.Charset")
                || isClassNameEquals(type, "java.util.Currency")
                || isClassNameEquals(type, "java.util.UUID")
                || isClassNameEquals(type, "java.util.regex.Pattern")
                || isClassNameEquals(type, "kotlin.text.Regex")
                || isClassNameEquals(type, "java.util.TimeZone")
                || isClassNameEquals(type, "java.time.ZoneId")
                || isClassNameEquals(type, "java.time.ZonedDateTime")
                || isClassNameEquals(type, "java.util.Calendar")
                || isClassNameEquals(type, "java.time.Duration")
                || isClassNameEquals(type, "java.time.Period")
                || isClassNameEquals(type, "org.springframework.util.unit.DataSize")
                || isClassNameEquals(type, "java.io.File")
                || isClassNameEquals(type, CommonClassNames.JAVA_NET_URI)
                || isClassNameEquals(type, CommonClassNames.JAVA_NET_URL)
                || isClassNameEquals(type, "java.net.InetAddress")
                || isClassNameEquals(type, "org.springframework.core.io.Resource")
                || isClassNameEquals(type, "org.springframework.http.MediaType")
                || canConvertFromString(type)));
  }


  public static boolean isPhysical(PsiType type) {
    return ReadAction.compute(() -> {
      PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
      if (psiClass == null) return false;
      return type.isValid() && psiClass.isPhysical();
    });
  }


  public static boolean isCollectionOrMap(Project project, @Nullable PsiType type) {
    return isCollection(project, type) || isMap(project, type);
  }


  public static boolean isCollection(Project project, @Nullable PsiType type) {
    if (type == null) return false;
    if (type instanceof PsiArrayType) return true;
    PsiClassType collectionType = getJavaTypeByName(project, CommonClassNames.JAVA_UTIL_COLLECTION);
    return ReadAction.compute(() -> collectionType.isAssignableFrom(type));
  }


  public static boolean isMap(Project project, @Nullable PsiType type) {
    if (type == null) return false;
    PsiClassType mapType = getJavaTypeByName(project, CommonClassNames.JAVA_UTIL_MAP);
    return ReadAction.compute(() -> mapType.isAssignableFrom(type));
  }


  public static boolean isClassNameEquals(PsiType type, String className) {
    return ReadAction.compute(() -> PsiTypesUtil.classNameEquals(type, className));
  }


  /**
   * @return element type of specified collectionOrArrayType, or null if specified type is not a collection or array, or is null.
   */
  @Nullable
  public static PsiType getElementType(Project project, PsiType collectionOrArrayType) {
    if (collectionOrArrayType instanceof PsiClassType type && isCollection(project, type)) {
      if (isClassNameEquals(type, CommonClassNames.JAVA_UTIL_LIST)
          || isClassNameEquals(type, CommonClassNames.JAVA_UTIL_ARRAY_LIST)
          || isClassNameEquals(type, CommonClassNames.JAVA_UTIL_LINKED_LIST)
          || isClassNameEquals(type, CommonClassNames.JAVA_UTIL_SET)
          || isClassNameEquals(type, CommonClassNames.JAVA_UTIL_HASH_SET)
          || isClassNameEquals(type, CommonClassNames.JAVA_UTIL_LINKED_HASH_SET)
          || isClassNameEquals(type, CommonClassNames.JAVA_UTIL_SORTED_SET)) {
        return ReadAction.compute(type::getParameters)[0];
      } else {
        PsiType[] parameters = ReadAction.compute(type::getParameters);
        if (parameters.length == 1) {
          log.warn("Try to retrieve element type from sub-classes of Collection \""
              + type + "\", this may be a wrong result");
          return parameters[0];
        } else {
          //TODO Support of sub-classes of Collection
          log.warn("(Unsupported)Cannot retrieve element type from sub-class of Collection: " + type);
          return null;
        }
      }
    } else if (collectionOrArrayType instanceof PsiArrayType arrayType) {
      return ReadAction.compute(arrayType::getComponentType);
    } else {
      throw new IllegalArgumentException("Unsupported type: " + collectionOrArrayType);
    }
  }


  /**
   * @return the key and value types of this property if it is or can be converted to a java.util.Map, or else null.
   */
  @Nullable
  @Size(min = 2, max = 2)
  public static PsiType[] getKeyValueType(Project project, @Nullable PsiType mapType) {
    if (mapType == null) return null;
    if (isClassNameEquals(mapType, CommonClassNames.JAVA_UTIL_MAP)
        || isClassNameEquals(mapType, CommonClassNames.JAVA_UTIL_HASH_MAP)
        || isClassNameEquals(mapType, CommonClassNames.JAVA_UTIL_CONCURRENT_HASH_MAP)
        || isClassNameEquals(mapType, CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)) {
      return mapType instanceof PsiClassType classType ? ReadAction.compute(classType::getParameters) : null;
    } else if (isClassNameEquals(mapType, CommonClassNames.JAVA_UTIL_PROPERTIES)) {
      // java.util.Properties implements Map<Object,Object>, we should manually force it to string.
      PsiType stringType = getJavaLangString(project);
      return new PsiType[]{stringType, stringType};
    } else if (isMap(project, mapType)) {
      //TODO Support sub-classes of Map, with generics.
      if (mapType instanceof PsiClassType classType) {
        PsiType[] parameters = ReadAction.compute(classType::getParameters);
        if (parameters.length == 2) {
          log.warn("Try to retrieve key & value types from sub-classes of Map \""
              + mapType + "\", this may be a wrong result");
          return parameters;
        }
      }
      log.warn("(Unsupported)Cannot retrieve key & value types from sub-class of Map: " + mapType);
      return null;
    } else {
      throw new IllegalArgumentException("Unsupported type: " + mapType);
    }
  }


  /**
   * @return true if {@code type} is Map and the key and value type is both {@linkplain #isValueType(PsiType) value type}
   */
  public static boolean isValueMap(Project project, @Nullable PsiType type) {
    if (!isMap(project, type)) return false;
    PsiType[] typeArgs = getKeyValueType(project, type);
    if (typeArgs == null || typeArgs.length != 2) return false;
    return isValueType(typeArgs[0]) && isValueType(typeArgs[1]);
  }


  private static boolean canConvertFromString(PsiType type) {
    if (type instanceof PsiClassType classType) {
      PsiClass psiClass = classType.resolve();
      if (psiClass == null) return false;
      return Stream.concat(
              Arrays.stream(psiClass.getConstructors()),
              Arrays.stream(psiClass.getMethods())
                  .filter(m -> m.hasModifierProperty(PsiModifier.STATIC))
                  .filter(m -> PsiTypesUtil.compareTypes(m.getReturnType(), type, true))
          ).map(PsiMethod::getParameterList)
          .filter(list -> list.getParametersCount() == 1)
          .map(list -> list.getParameter(0))
          .filter(Objects::nonNull)
          .map(PsiParameter::getType)
          .anyMatch(t -> isClassNameEquals(t, CommonClassNames.JAVA_LANG_STRING));
    }
    return false;
  }
}
