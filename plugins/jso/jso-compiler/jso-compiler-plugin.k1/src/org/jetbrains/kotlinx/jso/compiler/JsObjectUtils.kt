/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.k1.utils

import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.js.patterns.PatternBuilder
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyExternal
import org.jetbrains.kotlinx.jso.compiler.resolve.JsObjectDeclarationNames

val JSO_PATTERN = PatternBuilder.pattern("kotlinx.jso.jso()")

fun <F : CallableDescriptor?> ResolvedCall<F>.isJSOCall(): Boolean {
    val descriptor = resultingDescriptor
    return descriptor is SimpleFunctionDescriptor && JSO_PATTERN.test(descriptor)
}

val ClassDescriptor.shouldHaveGeneratedJsObjectBuilder: Boolean
    get() = kind == ClassKind.INTERFACE && isEffectivelyExternal() && name != JsObjectDeclarationNames.BUILDER_INTERFACE_NAME

val ClassDescriptor.isJsObjectBuilderInterface: Boolean
    get() = name == JsObjectDeclarationNames.BUILDER_INTERFACE_NAME &&
            kind == ClassKind.INTERFACE &&
            isEffectivelyExternal() &&
            (containingDeclaration as? ClassDescriptor)?.shouldHaveGeneratedJsObjectBuilder == true
