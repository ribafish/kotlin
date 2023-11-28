description = "Kotlin 2 Swift Export Compiler Plugin"

plugins {
    kotlin("jvm")
}

dependencies {
    embedded(project(":kotlin-swift-export-compiler-plugin.backend")) { isTransitive = false }
    embedded(project(":kotlin-swift-export-compiler-plugin.cli")) { isTransitive = false }

    testApi(project(":kotlin-swift-export-compiler-plugin.cli"))

    testApi(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

    testImplementation(projectTests(":compiler:tests-common"))
    testImplementation(projectTests(":compiler:tests-common-new"))


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

optInToExperimentalCompilerApi()

publish()

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest(parallel = true) {
    workingDir = rootDir
    useJUnitPlatform()
}
