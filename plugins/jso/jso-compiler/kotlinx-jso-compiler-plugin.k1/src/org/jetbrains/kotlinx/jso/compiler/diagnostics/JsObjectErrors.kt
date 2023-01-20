/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.diagnostics;

import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.diagnostics.*;

object JsObjectErrors {
    val ONLY_EXTERNAL_INTERFACES_SUPPORTED = DiagnosticFactory0.create<PsiElement>(Severity.ERROR);
    val TYPE_TO_CREATE_WAS_NOT_PROVIDED = DiagnosticFactory0.create<PsiElement>(Severity.ERROR);
    val LAMBDA_WAS_NOT_PROVIDED = DiagnosticFactory0.create<PsiElement>(Severity.ERROR);
    val FUNCTION_ACCEPT_ONLY_LAMBDA_FUNCTIONS = DiagnosticFactory0.create<PsiElement>(Severity.ERROR);
}
