/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.native

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.testbase.TestVersions.Kotlin.STABLE_RELEASE
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path


@DisplayName("This test class contains different scenarios with downloading Kotlin Native Compiler during build.")
@NativeGradlePluginTests
class KotlinNativeCompilerDownloadIT : KGPBaseTest() {

    private val currentPlatform = HostManager.platformName()

    private val UNPUCK_KONAN_FINISHED_LOG =
        "Moving Kotlin/Native compiler from tmp directory"

    private val DOWNLOAD_KONAN_FINISHED_LOG =
        "Download kotlin-native-prebuilt-$currentPlatform-$STABLE_RELEASE.${if (HostManager.hostIsMingw) "zip" else "tar.gz"} finished,"

    @DisplayName("KT-58303: Kotlin Native must not be downloaded during configuration phase")
    @GradleTest
    fun shouldNotDownloadKotlinNativeOnConfigurationPhase(gradleVersion: GradleVersion, @TempDir konanTemp: Path) {
        nativeProject(
            "native-simple-project",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(
                konanDataDir = konanTemp,
                nativeOptions = defaultBuildOptions.nativeOptions.copy(
                    version = STABLE_RELEASE,
                    distributionDownloadFromMaven = true
                ),
            ),
        ) {
            build("help") {
                assertOutputDoesNotContain(DOWNLOAD_KONAN_FINISHED_LOG)
                assertOutputDoesNotContain("Please wait while Kotlin/Native")
            }
        }
    }

    @DisplayName("KT-58303: Kotlin Native must be downloaded during execution phase")
    @GradleTest
    fun shouldDownloadKotlinNativeOnExecutionPhase(gradleVersion: GradleVersion, @TempDir konanTemp: Path) {
        nativeProject(
            "native-simple-project",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(
                konanDataDir = konanTemp,
                nativeOptions = defaultBuildOptions.nativeOptions.copy(
                    version = STABLE_RELEASE,
                    distributionDownloadFromMaven = true
                ),
            ),
        ) {
            build("assemble") {
                assertOutputDoesNotContain(DOWNLOAD_KONAN_FINISHED_LOG)
                assertOutputContains(UNPUCK_KONAN_FINISHED_LOG)
                assertOutputDoesNotContain("Please wait while Kotlin/Native")
            }
        }
    }

    @DisplayName("KT-58303: Downloading Kotlin Native on configuration phase(deprecated version)")
    @GradleTest
    fun shouldDownloadKotlinNativeOnConfigurationPhaseWithToolchainDisabled(gradleVersion: GradleVersion, @TempDir konanTemp: Path) {
        nativeProject(
            "native-simple-project",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(
                konanDataDir = konanTemp,
                freeArgs = listOf("-Pkotlin.native.toolchain.enabled=false"),
                nativeOptions = defaultBuildOptions.nativeOptions.copy(
                    version = STABLE_RELEASE,
                    distributionDownloadFromMaven = true
                ),
            ),
        ) {
            build("assemble") {
                assertOutputContains(DOWNLOAD_KONAN_FINISHED_LOG)
                assertOutputContains("Please wait while Kotlin/Native")
            }
        }
    }
}