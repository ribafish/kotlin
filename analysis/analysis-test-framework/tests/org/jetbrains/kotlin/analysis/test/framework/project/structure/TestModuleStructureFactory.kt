/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.test.framework.project.structure

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.standalone.base.project.structure.KtModuleProjectStructure
import org.jetbrains.kotlin.analysis.api.standalone.base.project.structure.KtModuleWithFiles
import org.jetbrains.kotlin.analysis.api.standalone.base.project.structure.StandaloneProjectFactory
import org.jetbrains.kotlin.analysis.project.structure.KtBinaryModule
import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.test.framework.services.environmentManager
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.test.model.DependencyRelation
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.*
import org.jetbrains.kotlin.test.services.configuration.JvmEnvironmentConfigurator
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object TestModuleStructureFactory {
    fun createProjectStructureByTestStructure(
        moduleStructure: TestModuleStructure,
        testServices: TestServices,
        project: Project
    ): KtModuleProjectStructure {
        val moduleEntries = moduleStructure.modules
            .map { testModule -> testServices.ktModuleFactory.createModule(testModule, testServices, project) }

        val moduleEntriesByName = moduleEntries.associateByName()

        val libraryCache = mutableMapOf<Set<Path>, KtBinaryModule>()

        for (testModule in moduleStructure.modules) {
            val moduleWithFiles = moduleEntriesByName[testModule.name] ?: moduleEntriesByName.getValue(testModule.files.single().name)
            when (val ktModule = moduleWithFiles.ktModule) {
                is KtNotUnderContentRootModule -> {
                    // Not-under-content-root modules have no external dependencies on purpose
                }
                is KtModuleWithModifiableDependencies -> {
                    addModuleDependencies(testModule, moduleEntriesByName, ktModule)
                    addLibraryDependencies(testModule, testServices, project, ktModule, libraryCache::getOrPut)
                }
                else -> error("Unexpected module type: " + ktModule.javaClass.name)
            }
        }

        return KtModuleProjectStructure(moduleEntries, libraryCache.values)
    }

    private fun addModuleDependencies(
        testModule: TestModule,
        moduleByName: Map<String, KtModuleWithFiles>,
        ktModule: KtModuleWithModifiableDependencies
    ) {
        testModule.allDependencies.forEach { dependency ->
            val dependencyKtModule = moduleByName.getValue(dependency.moduleName).ktModule
            when (dependency.relation) {
                DependencyRelation.RegularDependency -> ktModule.directRegularDependencies.add(dependencyKtModule)
                DependencyRelation.FriendDependency -> ktModule.directFriendDependencies.add(dependencyKtModule)
                DependencyRelation.DependsOnDependency -> ktModule.directDependsOnDependencies.add(dependencyKtModule)
            }
        }
    }

    private fun addLibraryDependencies(
        testModule: TestModule,
        testServices: TestServices,
        project: Project,
        ktModule: KtModuleWithModifiableDependencies,
        libraryCache: (paths: Set<Path>, factory: () -> KtBinaryModule) -> KtBinaryModule
    ) {
        val compilerConfiguration = testServices.compilerConfigurationProvider.getCompilerConfiguration(testModule)
        val classpathRoots = compilerConfiguration[CLIConfigurationKeys.CONTENT_ROOTS, emptyList()]
            .mapNotNull { (it as? JvmClasspathRoot)?.file?.toPath() }

        val jdkKind = JvmEnvironmentConfigurator.extractJdkKind(testModule.directives)
        val jdkHome = JvmEnvironmentConfigurator.getJdkHome(jdkKind)?.toPath()
            ?: JvmEnvironmentConfigurator.getJdkClasspathRoot(jdkKind)?.toPath()
            ?: Paths.get(System.getProperty("java.home"))

        val (jdkRoots, libraryRoots) = classpathRoots.partition { jdkHome != null && it.startsWith(jdkHome) }

        if (testModule.targetPlatform.isJvm() && jdkRoots.isNotEmpty()) {
            val jdkModule = libraryCache(jdkRoots.toSet()) {
                val jdkScope = getScopeForLibraryByRoots(jdkRoots, testServices)
                KtJdkModuleImpl("jdk", JvmPlatforms.defaultJvmPlatform, jdkScope, project, jdkRoots)
            }
            ktModule.directRegularDependencies.add(jdkModule)
        }

        for (root in libraryRoots) {
            val libraryModule = libraryCache(setOf(root)) { createKtLibraryModuleByJar(root, testServices, project) }
            ktModule.directRegularDependencies.add(libraryModule)
        }
    }

    private fun createKtLibraryModuleByJar(
        jar: Path,
        testServices: TestServices,
        project: Project,
        libraryName: String = jar.nameWithoutExtension,
    ): KtLibraryModuleImpl {
        check(jar.extension == "jar")
        check(jar.exists()) {
            "library $jar does not exist"
        }
        return KtLibraryModuleImpl(
            libraryName,
            JvmPlatforms.defaultJvmPlatform,
            getScopeForLibraryByRoots(listOf(jar), testServices),
            project,
            listOf(jar),
            librarySources = null,
        )
    }

    fun getScopeForLibraryByRoots(roots: Collection<Path>, testServices: TestServices): GlobalSearchScope {
        return StandaloneProjectFactory.createSearchScopeByLibraryRoots(
            roots,
            testServices.environmentManager.getProjectEnvironment()
        )
    }

    fun createSourcePsiFiles(
        testModule: TestModule,
        testServices: TestServices,
        project: Project,
    ): List<PsiFile> {
        return testModule.files.map { testFile ->
            when {
                testFile.isKtFile -> {
                    val fileText = testServices.sourceFileProvider.getContentOfSourceFile(testFile)
                    KtTestUtil.createFile(testFile.name, fileText, project)
                }

                testFile.isJavaFile || testFile.isExternalAnnotation -> {
                    val filePath = testServices.sourceFileProvider.getRealFileForSourceFile(testFile)
                    val virtualFile =
                        testServices.environmentManager.getApplicationEnvironment().localFileSystem.findFileByIoFile(filePath)
                            ?: error("Virtual file not found for $filePath")
                    PsiManager.getInstance(project).findFile(virtualFile)
                        ?: error("PsiFile file not found for $filePath")
                }

                else -> error("Unexpected file ${testFile.name}")
            }
        }
    }
}

