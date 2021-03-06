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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.*;

/**
 * @author peter
 */
class CollectConversion {
 
  static void addCollectConversion(PsiReferenceExpression ref, Collection<ExpectedTypeInfo> expectedTypes, Consumer<LookupElement> consumer) {
    final PsiExpression qualifier = ref.getQualifierExpression();
    PsiType component = qualifier == null ? null : PsiUtil.substituteTypeParameter(qualifier.getType(), JAVA_UTIL_STREAM_STREAM, 0, true);
    if (component == null) return;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
    GlobalSearchScope scope = ref.getResolveScope();
    PsiClass list = facade.findClass(JAVA_UTIL_LIST, scope);
    PsiClass set = facade.findClass(JAVA_UTIL_SET, scope);
    if (facade.findClass(JAVA_UTIL_STREAM_COLLECTORS, scope) == null || list == null || set == null) return;

    boolean hasList = false;
    boolean hasSet = false;
    for (ExpectedTypeInfo info : expectedTypes) {
      PsiType type = info.getDefaultType();
      PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(type);
      PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(type, true);
      if (expectedClass == null || expectedComponent == null || !TypeConversionUtil.isAssignable(expectedComponent, component)) continue;

      if (!hasList && InheritanceUtil.isInheritorOrSelf(list, expectedClass, true)) {
        hasList = true;
        consumer.consume(new MyLookupElement("toList", type));
      }

      if (!hasSet && InheritanceUtil.isInheritorOrSelf(set, expectedClass, true)) {
        hasSet = true;
        consumer.consume(new MyLookupElement("toSet", type));
      }

    }
  }

  private static class MyLookupElement extends LookupElement implements TypedLookupItem {
    private final String myLookupString;
    private final String myTypeText;
    private final String myMethodName;
    @NotNull private final PsiType myExpectedType;

    MyLookupElement(String methodName, @NotNull PsiType expectedType) {
      myMethodName = methodName;
      myExpectedType = expectedType;
      myLookupString = "collect(Collectors." + myMethodName + "())";
      myTypeText = myExpectedType.getPresentableText();
    }

    @NotNull
    @Override
    public String getLookupString() {
      return myLookupString;
    }

    @Override
    public Set<String> getAllLookupStrings() {
      return ContainerUtil.newHashSet(myLookupString, myMethodName);
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setTypeText(myTypeText);
      presentation.setIcon(PlatformIcons.METHOD_ICON);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), getInsertString());
      context.commitDocument();
      
      PsiMethodCallExpression call =
        PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiMethodCallExpression.class, false);
      if (call == null) return;

      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1 || !(args[0] instanceof PsiMethodCallExpression)) return;

      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(args[0]);
    }

    @NotNull
    private String getInsertString() {
      return "collect(" + JAVA_UTIL_STREAM_COLLECTORS + "." + myMethodName + "())";
    }

    @Override
    public PsiType getType() {
      return myExpectedType;
    }
  }
}
