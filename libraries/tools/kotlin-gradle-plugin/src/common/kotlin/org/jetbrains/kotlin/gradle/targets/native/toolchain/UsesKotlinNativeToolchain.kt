/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.toolchain

import org.gradle.api.Task
import org.gradle.api.provider.Provider

/**
 * This is a marker interface for all tasks which are dependent on the downloaded Kotlin Native compiler and its dependencies if needed.
 */
internal interface UsesKotlinNativeToolchain : Task {

    val kotlinNativeProvider: Provider<KotlinNativeProvider>
}
