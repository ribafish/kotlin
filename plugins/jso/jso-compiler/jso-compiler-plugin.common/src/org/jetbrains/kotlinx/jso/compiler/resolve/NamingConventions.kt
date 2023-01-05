/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.resolve;

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object JsObjectDeclarationNames {
    private const val BUILDER_INTERFACE = "kotlinx\$jso\$builder"

    val BUILDER_INTERFACE_NAME = Name.identifier(BUILDER_INTERFACE)
}

fun String.withPackage(fqName: FqName): FqName {
    return fqName.child(Name.identifier(this))
}