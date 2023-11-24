/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.native

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@DisplayName("This test class contains different scenarios with KotlinNativeToolchain feature")
@NativeGradlePluginTests
class KotlinNativeToolchainIT : KGPBaseTest() {

    @OptIn(EnvironmentalVariablesOverride::class)
    @DisplayName("K/N Gradle project build (on Linux or Mac) with a dependency from a Maven")
    @GradleTest
    fun testSetupCommonOptionsForCaches(gradleVersion: GradleVersion, @TempDir tempDir: Path) {
        val anotherKonanDataDir = tempDir.resolve(".konan2")
        nativeProject(
            "native-with-maven-dependencies",
            gradleVersion = gradleVersion,
            environmentVariables = EnvironmentalVariables(Pair("KONAN_DATA_DIR", anotherKonanDataDir.absolutePathString()))
        ) {
            build(
                "linkDebugExecutableNative",
                buildOptions = defaultBuildOptions.copy(
                    nativeOptions = defaultBuildOptions.nativeOptions.copy(
                        cacheKind = null
                    )
                )
            ) {
                assertOutputDoesNotContain("w: Failed to build cache")
                assertTasksExecuted(":linkDebugExecutableNative")
                assertDirectoryDoesNotExist(anotherKonanDataDir)
            }
        }
    }
}