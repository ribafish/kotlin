/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.cli

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.extensions.internal.InternalNonStableExtensionPoints
import org.jetbrains.kotlin.extensions.internal.TypeResolutionInterceptor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlinx.jso.compiler.extensions.JsObjectCreationExtension
import org.jetbrains.kotlinx.jso.compiler.k1.diagnostics.JsObjectPropertiesChecker
import org.jetbrains.kotlinx.jso.compiler.k1.extensions.JsObjectResolveExtension

@OptIn(ExperimentalCompilerApi::class, InternalNonStableExtensionPoints::class)
class JsObjectComponentRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean get() = false


    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        Companion.registerExtensions(this)
    }

    companion object {
        fun registerExtensions(extensionStorage: ExtensionStorage) = with(extensionStorage) {
            StorageComponentContainerContributor.registerExtension(JsObjectPluginComponentContainerContributor())
            SyntheticResolveExtension.registerExtension(JsObjectResolveExtension())
            TypeResolutionInterceptor.registerExtension(JsObjectCreationExtension())
        }
    }
}

private class JsObjectPluginComponentContainerContributor : StorageComponentContainerContributor {
    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        container.useInstance(JsObjectPropertiesChecker())
    }
}
