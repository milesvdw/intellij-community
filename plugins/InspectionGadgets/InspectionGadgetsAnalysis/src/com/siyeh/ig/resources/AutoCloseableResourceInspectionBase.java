/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.resources;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class AutoCloseableResourceInspectionBase extends ResourceInspection {

  private static final List<String> DEFAULT_IGNORED_TYPES =
    Arrays.asList("java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.LongStream", "java.util.stream.DoubleStream");
  @SuppressWarnings("PublicField")
  public boolean ignoreFromMethodCall = false;

  final List<String> ignoredTypes = new ArrayList<String>(DEFAULT_IGNORED_TYPES);

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("auto.closeable.resource.display.name");
  }

  @NotNull
  @Override
  public String getID() {
    return "resource"; // matches Eclipse inspection
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message("auto.closeable.resource.problem.descriptor", text);
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element option : node.getChildren("option")) {
      final String name = option.getAttributeValue("name");
      if ("ignoredTypes".equals(name)) {
        final String ignoredTypesString = option.getAttributeValue("value");
        if (ignoredTypesString != null) {
          ignoredTypes.clear();
          parseString(ignoredTypesString, ignoredTypes);
        }
      }
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    writeBooleanOption(node, "insideTryAllowed", false);
    writeBooleanOption(node, "anyMethodMayClose", true);
    if (!DEFAULT_IGNORED_TYPES.equals(ignoredTypes)) {
      final String ignoredTypesString = formatString(ignoredTypes);
      node.addContent(new Element("option").setAttribute("name", "ignoredTypes").setAttribute("value", ignoredTypesString));
    }
  }

  @Override
  protected boolean isResourceCreation(PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE) &&
           !TypeUtils.expressionHasTypeOrSubtype(expression, ignoredTypes);
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoCloseableResourceVisitor();
  }

  private class AutoCloseableResourceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression.getType());
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (ignoreFromMethodCall) {
        return;
      }
      if (MethodCallUtils.isCallToMethod(expression, "java.util.Formatter", null, "format", null) ||
          MethodCallUtils.isCallToMethod(expression, "java.io.Writer", null, "append", null) ||
          MethodCallUtils.isCallToMethod(expression, "com.google.common.base.Preconditions", null, "checkNotNull", null) ||
          MethodCallUtils.isCallToMethod(expression, "org.hibernate.Session", null, "close")) {
        return;
      }
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerMethodCallError(expression, expression.getType());
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (!expression.isConstructor()) {
        return;
      }
      final PsiType type = PsiMethodReferenceUtil.getQualifierType(expression);
      if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE)) {
        return;
      }
      for (String ignoredType : ignoredTypes) {
        if (InheritanceUtil.isInheritor(type, ignoredType)) {
          return;
        }
      }
      registerError(expression, type);
    }

    private boolean isNotSafelyClosedResource(PsiExpression expression) {
      if (!isResourceCreation(expression)) {
        return false;
      }
      final PsiVariable variable = ResourceInspection.getVariable(expression);
      return !(variable instanceof PsiResourceVariable) &&
             !isResourceEscapingFromMethod(variable, expression);
    }
  }
}
