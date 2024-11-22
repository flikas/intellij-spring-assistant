package dev.flikas.spring.boot.assistant.idea.plugin.misc;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class PsiMethodUtils {
  private static final Pattern METHOD_SIGNATURE = Pattern.compile("([\\w$]+)\\s*\\((.*)\\)");


  public static Optional<PsiMethod> findMethodBySignature(PsiClass containingClass, String methodSignature) {
    Matcher matcher = METHOD_SIGNATURE.matcher(methodSignature);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    String name = matcher.group(1);
    String[] params = matcher.group(2).split(",");
    LightMethodBuilder patternMethod = new LightMethodBuilder(containingClass.getManager(), name);
    for (int i = 0; i < params.length; i++) {
      if (StringUtils.isBlank(params[i])) continue;
      patternMethod.addParameter("param" + i, params[i]);
    }
    return Optional.ofNullable(containingClass.findMethodBySignature(patternMethod, true));
  }
}
