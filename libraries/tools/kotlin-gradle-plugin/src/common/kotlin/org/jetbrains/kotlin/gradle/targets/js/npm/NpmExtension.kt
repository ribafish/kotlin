/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.internal.ConfigurationPhaseAware
import org.jetbrains.kotlin.gradle.logging.kotlinInfo
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnv
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NpmApiExtension
import java.io.File

open class NpmExtension(
    val project: Project,
) : ConfigurationPhaseAware<NpmEnv>(), NpmApiExtension<NpmEnvironment, Npm> {
    init {
        check(project == project.rootProject)
    }

    private val gradleHome = project.gradle.gradleUserHomeDir.also {
        project.logger.kotlinInfo("Storing cached files in $it")
    }

    override val packageManager: Npm by lazy {
        Npm()
    }

    override val environment: NpmEnvironment by lazy {
        requireConfigured().asNpmEnvironment
    }

    override val additionalInstallOutput: FileCollection = project.objects.fileCollection().from(
        {
            nodeJsEnvironment.get().rootPackageDir.resolve("package-lock.json")
        }
    )

    override val preInstallTasks: ListProperty<TaskProvider<*>> = project.objects.listProperty(TaskProvider::class.java)

    override val postInstallTasks: ListProperty<TaskProvider<*>> = project.objects.listProperty(TaskProvider::class.java)

    var command by Property("npm")

    var lockFileName by Property(LockCopyTask.PACKAGE_LOCK)
    var lockFileDirectory: File by Property(project.rootDir.resolve(LockCopyTask.KOTLIN_JS_STORE))

    var ignoreScripts by Property(true)

    var packageLockMismatchReport: LockFileMismatchReport by Property(LockFileMismatchReport.FAIL)

    var reportNewPackageLock: Boolean by Property(false)

    var packageLockAutoReplace: Boolean by Property(false)

    var overrides: MutableList<NpmOverride> by Property(mutableListOf())

    fun override(path: String, configure: Action<NpmOverride>) {
        overrides.add(
            NpmOverride(path)
                .apply { configure.execute(this) }
        )
    }

    fun override(path: String, version: String) {
        override(path, Action {
            it.include(version)
        })
    }

    internal val nodeJsEnvironment: org.gradle.api.provider.Property<NodeJsEnv> = project.objects.property(NodeJsEnv::class.java)

    override fun finalizeConfiguration(): NpmEnv {
        val nodeJsEnvironmentValue = nodeJsEnvironment.get()
        val isWindows = nodeJsEnvironmentValue.isWindows

        fun getExecutable(command: String, customCommand: String, windowsExtension: String): String {
            val finalCommand = if (isWindows && customCommand == command) "$command.$windowsExtension" else customCommand
            return if (nodeJsEnvironmentValue.download)
                nodeJsEnvironmentValue.nodeBinDir
                    .resolve(finalCommand).absolutePath
            else
                finalCommand
        }
        return NpmEnv(
            executable = getExecutable("npm", command, "cmd"),
            ignoreScripts = ignoreScripts,
            standalone = !nodeJsEnvironmentValue.download,
            packageLockMismatchReport = packageLockMismatchReport,
            reportNewPackageLock = reportNewPackageLock,
            packageLockAutoReplace = packageLockAutoReplace,
            overrides = overrides
        )
    }

    val restorePackageLockTaskProvider: TaskProvider<out Task>
        get() = project.tasks.named(LockCopyTask.RESTORE_PACKAGE_LOCK_NAME)

    val storePackageLockTaskProvider: TaskProvider<out Task>
        get() = project.tasks.named(LockCopyTask.STORE_PACKAGE_LOCK_NAME)

    companion object {
        const val EXTENSION_NAME: String = "kotlinNpm"

        operator fun get(project: Project): NpmExtension {
            val rootProject = project.rootProject
            rootProject.plugins.apply(NodeJsRootPlugin::class.java)
            return rootProject.extensions.getByName(EXTENSION_NAME) as NpmExtension
        }
    }
}