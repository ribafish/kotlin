/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.swiftexport

import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.fir.extensions.FirAnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.swiftexport.Kotlin2SwiftExportPluginNames.DEMO_OPTION_KEY
import org.jetbrains.kotlin.swiftexport.Kotlin2SwiftExportPluginNames.PLUGIN_ID

object Kotlin2SwiftExportConfigurationKeys {
    val DEMO_OPTION: CompilerConfigurationKey<Boolean> = CompilerConfigurationKey.create(
        "dummy option for K2S export compiler plugin"
    )
}

class Kotlin2SwiftExportCommandLineProcessor : CommandLineProcessor {
    companion object {
        val DEMO_OPTION = CliOption(
            // options that we need:
            // 1/ output dirs for
            //      .swift and .h sources
            //      klib with bridges
            DEMO_OPTION_KEY, "true/false",
            "Invoke instance initializers in a no-arg constructor",
            required = false, allowMultipleOccurrences = false
        )
    }

    override val pluginId = PLUGIN_ID
    override val pluginOptions = listOf(DEMO_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) = when (option) {
        DEMO_OPTION -> configuration.put(Kotlin2SwiftExportConfigurationKeys.DEMO_OPTION, value == "true")
        else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
    }
}

class Kotlin2SwiftExportComponentRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirAnalysisHandlerExtension.registerExtension(Kotlin2SwiftExportExtension())
    }
}
