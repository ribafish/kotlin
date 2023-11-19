/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl

/**
 * Marker annotation to specify Kotlin Gradle plugin public DSL.
 *
 * Annotated APIs will be binary validated.
 */
@DslMarker
annotation class KotlinGradlePluginDsl
