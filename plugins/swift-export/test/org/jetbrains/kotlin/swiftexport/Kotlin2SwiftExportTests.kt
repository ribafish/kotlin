/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.swiftexport

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.cli.common.CLITool
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.CompilerTestUtil
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.runners.*
import org.jetbrains.kotlin.test.services.*
import org.jetbrains.kotlin.test.services.configuration.CommonEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.configuration.JvmEnvironmentConfigurator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.reflect.KClass
import kotlin.test.assertEquals

abstract class AbstractKotlin2SwiftExportContextTestBase(
    targetBackend: TargetBackend
) : AbstractKotlinCompilerWithTargetBackendTest(targetBackend) {

    override fun TestConfigurationBuilder.configuration() {
        globalDefaults {
            frontend = FrontendKinds.FIR
            targetPlatform = JvmPlatforms.defaultJvmPlatform
            dependencyKind = DependencyKind.Binary
        }

        useConfigurators(
            ::CommonEnvironmentConfigurator,
            ::JvmEnvironmentConfigurator,
        )

        facadeStep(::JvmCompilerWithSwiftExportPluginFacade)
    }
}

open class AbstractKotlin2SwiftExportContextTest : AbstractKotlin2SwiftExportContextTestBase(TargetBackend.JVM) {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.apply {
            globalDefaults {
                targetBackend = TargetBackend.JVM
            }
        }
    }
}

class JvmCompilerWithSwiftExportPluginFacade(
    private val testServices: TestServices,
) :
    AbstractTestFacade<ResultingArtifact.Source, ResultingArtifact.Binary.Empty>() {
    override val inputKind: TestArtifactKind<ResultingArtifact.Source>
        get() = SourcesKind
    override val outputKind: TestArtifactKind<ResultingArtifact.Binary.Empty>
        get() = ResultingArtifact.Binary.Empty.kind

    private val tmpDir = FileUtil.createTempDirectory("JvmCompilerWithKaptFacade", null, false)

    override fun transform(module: TestModule, inputArtifact: ResultingArtifact.Source): ResultingArtifact.Binary.Empty {
        val configurationProvider = testServices.compilerConfigurationProvider
        val project = configurationProvider.getProject(module)
        val ktFiles = testServices.sourceFileProvider.getKtFilesForSourceFiles(module.files, project, findViaVfs = true).values.toList()

        runTest(
            K2JVMCompiler(),
            ktFiles
        )

        return ResultingArtifact.Binary.Empty
    }

    private fun runTest(
        compiler: CLITool<*>,
        src: List<KtFile>,
        klass: KClass<out CompilerPluginRegistrar> = Kotlin2SwiftExportComponentRegistrar::class,
        expectedExitCode: ExitCode = ExitCode.OK,
        extras: List<String> = emptyList(),
    ) {
        val sources = src.map {
            tmpDir.resolve(it.name).apply {
                writeText(it.text)
            }
        }

        val plugin = writePlugin(klass)
        val args = listOf("-Xplugin=$plugin") + sources.map { it.absolutePath }

        val outputPath = listOf(
            "-language-version", "2.0",
            "-d", tmpDir.resolve("out").absolutePath
        )

        val (output, exitCode) = CompilerTestUtil.executeCompiler(
            compiler,
            args + outputPath + extras
        )
        assertEquals(expectedExitCode, exitCode, output)
    }

    private fun writePlugin(klass: KClass<out CompilerPluginRegistrar>): String {
        additionalServices
        val jarFile = tmpDir.resolve("plugin.jar")
        ZipOutputStream(jarFile.outputStream()).use {
            val entry = ZipEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar")
            it.putNextEntry(entry)
            it.write(klass.java.name.toByteArray())
        }
        return jarFile.absolutePath
    }

    override fun shouldRunAnalysis(module: TestModule): Boolean {
        return true
    }
}
