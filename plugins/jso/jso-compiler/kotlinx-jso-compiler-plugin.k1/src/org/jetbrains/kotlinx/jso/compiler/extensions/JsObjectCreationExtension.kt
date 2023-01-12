/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.extensions

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.ReceiverParameterDescriptorImpl
import org.jetbrains.kotlin.extensions.internal.InternalNonStableExtensionPoints
import org.jetbrains.kotlin.extensions.internal.TypeResolutionInterceptorExtension
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.scopes.receivers.TransientReceiver
import org.jetbrains.kotlin.types.asSimpleType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingContext
import org.jetbrains.kotlin.types.replace
import org.jetbrains.kotlinx.jso.compiler.k1.utils.isJSOCall
import org.jetbrains.kotlinx.jso.compiler.resolve.JsObjectDeclarationNames

@OptIn(InternalNonStableExtensionPoints::class)
class JsObjectCreationExtension : TypeResolutionInterceptorExtension {
    override fun interceptFunctionLiteralDescriptor(
        expression: KtLambdaExpression,
        context: ExpressionTypingContext,
        descriptor: AnonymousFunctionDescriptor
    ): AnonymousFunctionDescriptor {
        val maybeJsoCall = (expression.context as? KtCallExpression).getResolvedCall(context)

        if (maybeJsoCall?.isJSOCall() != true) {
            return super.interceptFunctionLiteralDescriptor(expression, context, descriptor)
        }

        val typeToBuild = maybeJsoCall.typeArguments.values.single().asSimpleType()

        val builderDeclaration = typeToBuild
            .constructor
            .let { it as ClassDescriptor }
            .staticScope
            .getContributedClassifier(JsObjectDeclarationNames.BUILDER_INTERFACE_NAME, NoLookupLocation.FROM_BACKEND) as ClassDescriptor

        val newLambda = AnonymousFunctionDescriptor(
            descriptor.containingDeclaration,
            descriptor.annotations,
            descriptor.kind,
            descriptor.source,
            descriptor.isSuspend
        )

        newLambda.initialize(
            descriptor.extensionReceiverParameter,
            ReceiverParameterDescriptorImpl(
                newLambda,
                TransientReceiver(builderDeclaration.defaultType.replace(typeToBuild.arguments)),
                Annotations.EMPTY
            ),
            descriptor.contextReceiverParameters,
            descriptor.typeParameters,
            descriptor.valueParameters,
            descriptor.returnType,
            descriptor.modality,
            descriptor.visibility,
        )

        return newLambda
    }
}