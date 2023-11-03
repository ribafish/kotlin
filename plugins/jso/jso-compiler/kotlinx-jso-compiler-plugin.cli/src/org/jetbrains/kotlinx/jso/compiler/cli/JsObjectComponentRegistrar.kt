/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.cli

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlinx.jso.compiler.backend.JsObjectLoweringExtension
import org.jetbrains.kotlinx.jso.compiler.fir.JsObjectExtensionRegistrar
import org.jetbrains.kotlinx.jso.compiler.psi.JsObjectFactoryFunctionGenerator

@OptIn(ExperimentalCompilerApi::class)
class JsObjectComponentRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        Companion.registerExtensions(this)
    }

    companion object {
        fun registerExtensions(extensionStorage: ExtensionStorage) = with(extensionStorage) {
            SyntheticResolveExtension.registerExtension(JsObjectFactoryFunctionGenerator())
            FirExtensionRegistrarAdapter.registerExtension(JsObjectExtensionRegistrar())
            IrGenerationExtension.registerExtension(JsObjectLoweringExtension())
        }
    }
}