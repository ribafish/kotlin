/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.utils

val KOTLIN_TO_JAVA_ANNOTATION_TARGETS: Map<String, String> = mapOf(
    "CLASS" to "TYPE",
    "ANNOTATION_CLASS" to "ANNOTATION_TYPE",
    "FIELD" to "FIELD",
    "LOCAL_VARIABLE" to "LOCAL_VARIABLE",
    "VALUE_PARAMETER" to "PARAMETER",
    "CONSTRUCTOR" to "CONSTRUCTOR",
    "FUNCTION" to "METHOD",
    "PROPERTY_GETTER" to "METHOD",
    "PROPERTY_SETTER" to "METHOD",
    "TYPE_PARAMETER" to "TYPE_PARAMETER",
    "TYPE" to "TYPE_USE",
)