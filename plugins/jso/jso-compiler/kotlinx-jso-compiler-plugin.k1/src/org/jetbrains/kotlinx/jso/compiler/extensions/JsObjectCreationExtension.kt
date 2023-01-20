/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.extensions

import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.ReceiverParameterDescriptorImpl
import org.jetbrains.kotlin.extensions.internal.CallResolutionInterceptorExtension
import org.jetbrains.kotlin.extensions.internal.InternalNonStableExtensionPoints
import org.jetbrains.kotlin.extensions.internal.TypeResolutionInterceptorExtension
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.TransientReceiver
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingContext
import org.jetbrains.kotlin.types.replace
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI
import org.jetbrains.kotlinx.jso.compiler.k1.utils.isJSOCall
import org.jetbrains.kotlinx.jso.compiler.k1.utils.shouldHaveGeneratedJsObjectBuilder
import org.jetbrains.kotlinx.jso.compiler.resolve.JsObjectDeclarationNames

@OptIn(InternalNonStableExtensionPoints::class)
class JsObjectCreationExtension : TypeResolutionInterceptorExtension {
    @OptIn(IDEAPluginsCompatibilityAPI::class)
    override fun interceptFunctionLiteralDescriptor(
        expression: KtLambdaExpression,
        context: ExpressionTypingContext,
        descriptor: AnonymousFunctionDescriptor
    ): AnonymousFunctionDescriptor {
        val callExpression = expression.parent.parent as? KtCallExpression
        val maybeJsoCall = callExpression.getResolvedCall(context.trace.bindingContext)

        if (maybeJsoCall?.isJSOCall() != true) {
            return super.interceptFunctionLiteralDescriptor(expression, context, descriptor)
        }

        val typeToBuild = maybeJsoCall.call.typeArguments.singleOrNull()
            ?.typeReference
            ?.let { context.trace.get(BindingContext.TYPE, it) }

        val builderDeclaration = typeToBuild
            ?.constructor
            ?.let { it.declarationDescriptor as ClassDescriptor }
            ?.jsoBuilder ?: return super.interceptFunctionLiteralDescriptor(expression, context, descriptor)

        return AnonymousFunctionDescriptorWithConstantExtensionReceiver(
            descriptor,
            ReceiverParameterDescriptorImpl(
                descriptor,
                ExtensionReceiver(
                    descriptor,
                    builderDeclaration.defaultType.replace(typeToBuild.arguments),
                    null
                ),
                Annotations.EMPTY
            )
        )
    }

    private val ClassDescriptor.jsoBuilder: ClassDescriptor?
        get() {
            if (shouldHaveGeneratedJsObjectBuilder) {
                return unsubstitutedMemberScope
                    .getDescriptorsFiltered(nameFilter = { it == JsObjectDeclarationNames.BUILDER_INTERFACE_NAME })
                    .filterIsInstance<ClassDescriptor>().singleOrNull()
            }
            return null
        }

    class AnonymousFunctionDescriptorWithConstantExtensionReceiver(
        descriptor: AnonymousFunctionDescriptor,
        private val extensionReceiverParameter: ReceiverParameterDescriptor
    ) : AnonymousFunctionDescriptor(
        descriptor.containingDeclaration,
        descriptor.annotations,
        descriptor.kind,
        descriptor.source,
        descriptor.isSuspend
    ) {
        init {
            descriptor.setExtensionReceiverParameter(extensionReceiverParameter)
        }

        override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? {
            return extensionReceiverParameter
        }
    }
}