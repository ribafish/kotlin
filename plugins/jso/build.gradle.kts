import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsCompilerAttribute
import org.jetbrains.kotlin.gradle.targets.js.d8.D8RootPlugin

description = "Kotlin JavaScript Object Compiler Plugin"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

val jsoIrRuntimeForTests by configurations.creating {
    attributes {
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
        attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
    }
}

dependencies {
//    embedded(project(":kotlinx-jso-compiler-plugin.common")) { isTransitive = false }
//    embedded(project(":kotlinx-jso-compiler-plugin.k1")) { isTransitive = false }
//    embedded(project(":kotlinx-jso-compiler-plugin.k2")) { isTransitive = false }
//    embedded(project(":kotlinx-jso-compiler-plugin.cli")) { isTransitive = false }

//    testApi(project(":compiler:backend"))
//    testApi(project(":compiler:cli"))
//    testApi(project(":kotlinx-serialization-compiler-plugin.cli"))
//
//    testApi(projectTests(":compiler:test-infrastructure"))
//    testApi(projectTests(":compiler:test-infrastructure-utils"))
//    testApi(projectTests(":compiler:tests-compiler-utils"))
//    testApi(projectTests(":compiler:tests-common-new"))
//    testImplementation(projectTests(":generators:test-generator"))
//
//    testApiJUnit5()
//
//    jsoIrRuntimeForTests(project(":kotlinx-jso-runtime")) { isTransitive = false }
//
//    testRuntimeOnly(project(":core:descriptors.runtime"))
}

optInToExperimentalCompilerApi()

sourceSets {
    "main" { none() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

optInToExperimentalCompilerApi()

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest(parallel = true, jUnitMode = JUnitMode.JUnit5) {
    workingDir = rootDir
    useJUnitPlatform()
}

val generateTests by generator("org.jetbrains.kotlinx.jso.TestGeneratorKt")
