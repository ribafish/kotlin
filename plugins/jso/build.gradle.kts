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
    embedded(project(":kotlinx-jso-compiler-plugin.common")) { isTransitive = false }
    embedded(project(":kotlinx-jso-compiler-plugin.k1")) { isTransitive = false }
    embedded(project(":kotlinx-jso-compiler-plugin.k2")) { isTransitive = false }
    embedded(project(":kotlinx-jso-compiler-plugin.cli")) { isTransitive = false }

    testApi(project(":compiler:backend"))
    testApi(project(":compiler:cli"))
    testApi(project(":kotlinx-jso-compiler-plugin.cli"))

    testApi(projectTests(":compiler:test-infrastructure"))
    testApi(projectTests(":compiler:test-infrastructure-utils"))
    testApi(projectTests(":compiler:tests-compiler-utils"))
    testApi(projectTests(":compiler:tests-common-new"))

    testImplementation(projectTests(":js:js.tests"))
    testImplementation(projectTests(":generators:test-generator"))

    testApiJUnit5()

    jsoIrRuntimeForTests(project(":kotlinx-jso-runtime")) { isTransitive = false }

    embedded(project(":kotlinx-jso-runtime")) {
        attributes {
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
            attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
        }
        isTransitive = false
    }

    testRuntimeOnly(project(":core:descriptors.runtime"))
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
    useJUnitPlatform()
    workingDir = rootDir
    dependsOn(jsoIrRuntimeForTests)
    doFirst {
        systemProperty("jso.runtime.path", jsoIrRuntimeForTests.asPath)
    }
    setUpJsIrBoxTests()
}

val generateTests by generator("org.jetbrains.kotlinx.jso.TestGeneratorKt")

val d8Plugin = D8RootPlugin.apply(rootProject)
d8Plugin.version = v8Version

fun Test.setupV8() {
    dependsOn(d8Plugin.setupTaskProvider)
    val v8ExecutablePath = d8Plugin.requireConfigured().executablePath.absolutePath
    doFirst {
        systemProperty("javascript.engine.path.V8", v8ExecutablePath)
    }
}

fun Test.setUpJsIrBoxTests() {
    setupV8()

    dependsOn(":dist")
    dependsOn(":kotlin-stdlib-js-ir:compileKotlinJs")
    systemProperty("kotlin.js.full.stdlib.path", "libraries/stdlib/js-ir/build/classes/kotlin/js/main")
    dependsOn(":kotlin-stdlib-js-ir-minimal-for-test:compileKotlinJs")
    systemProperty("kotlin.js.reduced.stdlib.path", "libraries/stdlib/js-ir-minimal-for-test/build/classes/kotlin/js/main")
    dependsOn(":kotlin-test:kotlin-test-js-ir:compileKotlinJs")
    systemProperty("kotlin.js.kotlin.test.path", "libraries/kotlin.test/js-ir/build/classes/kotlin/js/main")
    systemProperty("kotlin.js.kotlin.test.path", "libraries/kotlin.test/js-ir/build/classes/kotlin/js/main")
    systemProperty("kotlin.js.test.root.out.dir", "$buildDir/")

}
