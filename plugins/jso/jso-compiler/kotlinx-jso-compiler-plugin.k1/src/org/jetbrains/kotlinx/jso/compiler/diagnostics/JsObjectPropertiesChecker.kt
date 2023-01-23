/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.k1.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.cfg.ControlFlowInformationProvider
import org.jetbrains.kotlin.cfg.ControlFlowInformationProviderImpl
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.js.analyze.JsNativeDiagnosticSuppressor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.stubs.impl.KotlinClassStubImpl
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlinx.jso.compiler.k1.utils.isJSOCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyExternal
import org.jetbrains.kotlinx.jso.compiler.diagnostics.JsObjectErrors

class JsObjectPropertiesChecker : CallChecker {
    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        if (!resolvedCall.isJSOCall()) return

        val providedExternalInterfaceTypeArgument =
            resolvedCall.typeArguments.values.singleOrNull()
                ?: return context.trace.report(JsObjectErrors.TYPE_TO_CREATE_WAS_NOT_PROVIDED.on(reportOn))

        if (!providedExternalInterfaceTypeArgument.isExternalInterface()) {
            context.trace.report(JsObjectErrors.ONLY_EXTERNAL_INTERFACES_SUPPORTED.on(reportOn))
        }

        val providedLambda = resolvedCall.valueArgumentsByIndex?.singleOrNull()?.arguments?.singleOrNull()?.getArgumentExpression()
            ?: return context.trace.report(JsObjectErrors.LAMBDA_WAS_NOT_PROVIDED.on(reportOn))

        if (providedLambda is KtLambdaExpression) {
            return context.trace.report(JsObjectErrors.FUNCTION_ACCEPT_ONLY_LAMBDA_FUNCTIONS.on(reportOn))
        }
    }

    private fun KtLambdaExpression.checkAllPropertiesWereInitialized(context: CallCheckerContext, typeToCreate: KotlinType) {
        val pseudoClassWithPseudoConstructor = KtClass(
            KotlinClassStubImpl(typeToCreate)
        )

        val controlFlowInformationProvider: ControlFlowInformationProvider = ControlFlowInformationProviderImpl(
            pseudoClassWithPseudoConstructor,
            context.trace,
            context.languageVersionSettings,
            JsNativeDiagnosticSuppressor,
            null
        )
    }

    private fun KotlinType.isExternalInterface(): Boolean {
        val descriptor = constructor.declarationDescriptor as? ClassDescriptor ?: return false
        return descriptor.kind == ClassKind.INTERFACE && descriptor.isEffectivelyExternal()
    }
}
