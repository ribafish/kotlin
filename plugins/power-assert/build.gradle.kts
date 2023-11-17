description = "Kotlin Power-Assert Compiler Plugin"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    embedded(project(":kotlin-power-assert-compiler-plugin.backend")) { isTransitive = false }
    embedded(project(":kotlin-power-assert-compiler-plugin.cli")) { isTransitive = false }

    testApi(project(":compiler:backend"))
    testApi(project(":compiler:cli"))
    testApi(project(":kotlin-power-assert-compiler-plugin.cli"))
    testCompileOnly(project(":kotlin-compiler"))
    testImplementation(project(":kotlin-scripting-jvm-host-unshaded"))

    testApi(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)

    testApi(projectTests(":compiler:tests-common-new"))
    testApi(projectTests(":compiler:test-infrastructure"))
    testApi(projectTests(":compiler:test-infrastructure-utils"))

    testImplementation(projectTests(":compiler:tests-common"))
    testImplementation(libs.junit4)

    testRuntimeOnly(project(":core:descriptors.runtime"))
    testRuntimeOnly(project(":compiler:fir:fir-serialization"))


    testApi(intellijCore())
}

optInToExperimentalCompilerApi()

sourceSets {
    "main" { none() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

publish()

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest(parallel = true) {
    dependsOn(":dist")
    workingDir = rootDir
    useJUnitPlatform()
}
