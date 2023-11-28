/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.swiftexport

import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.standalone.KtAlwaysAccessibleLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirAnalysisHandlerExtension
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.sir.analysisapi.SirGenerator
import org.jetbrains.kotlin.sir.builder.buildModule

@OptIn(KtAnalysisApiInternals::class)
class Kotlin2SwiftExportExtension : FirAnalysisHandlerExtension() {
    override fun isApplicable(configuration: CompilerConfiguration): Boolean {
        return true
    }

    override fun doAnalysis(configuration: CompilerConfiguration): Boolean {
        val standaloneAnalysisAPISession =
            buildStandaloneAnalysisAPISession(classLoader = Kotlin2SwiftExportExtension::class.java.classLoader) {
                @Suppress("DEPRECATION") // TODO: KT-61319 Kapt: remove usages of deprecated buildKtModuleProviderByCompilerConfiguration
                buildKtModuleProviderByCompilerConfiguration(configuration)

                registerProjectService(KtLifetimeTokenProvider::class.java, KtAlwaysAccessibleLifetimeTokenProvider())
            }

        val (sourceModule, rawFiles) = standaloneAnalysisAPISession.modulesWithFiles.entries.single()

        val ktFiles = rawFiles.mapNotNull { it as? KtFile }

        val module = buildModule {
            val sirFactory = SirGenerator()
            ktFiles.forEach { file ->
                name = sourceModule.moduleName
                declarations += sirFactory.build(file)
            }
        }
        // todo: passes
        // todo: printer

        return true
    }

}
