/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.resolve;

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object JsObjectDeclarationNames {
    private const val BUILDER_INTERFACE = "kotlinx\$jso\$builder"
    private const val UNSAFE_BUILDER_FUNCTION = "__unsafeJso"

    val BUILDER_INTERFACE_NAME = Name.identifier(BUILDER_INTERFACE)
    val BUILDER_FUNCTION_FQN = FqName("kotlinx.jso.jso")
}