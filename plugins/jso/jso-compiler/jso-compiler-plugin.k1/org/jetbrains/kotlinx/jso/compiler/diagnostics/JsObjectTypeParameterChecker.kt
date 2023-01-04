/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType

val JSO_PATTERN: DescriptorPredicate = PatternBuilder.pattern("kotlinx.jso.jso()")

class JsObjectTypeParameterChecker : CallChecker {
    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        if (!resolvedCall.isJSOCall()) return

        val providedExternalInterfaceTypeArgument = resolvedCall.typeArguments.values.singleOrNull() ?: return context.trace.report(TODO().on(expression))

        if (!providedExternalInterfaceTypeArgument.isExternalInterface()) {
            return context.trace.report(TODO().on(expression))
        }
    }

    private fun KotlinType.isExternalInterface(): Boolean {
        val descriptor = constructor.declarationDescriptor as? ClassDescriptor ?: return false
        return descriptor.kind == ClassKind.INTERFACE && descriptor.isEffectivelyExternal()
    }
}

private fun <F : CallableDescriptor?> ResolvedCall<F>.isJSOCall(): Boolean {
    val descriptor = resultingDescriptor
    return descriptor is SimpleFunctionDescriptor && JSO_PATTERN.test(descriptor)
}
