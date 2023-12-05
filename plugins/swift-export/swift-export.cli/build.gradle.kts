description = "Kotlin 2 Swift Compiler Plugin (CLI)"

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":kotlin-swift-export-compiler-plugin.backend"))
    compileOnly(project(":compiler:plugin-api"))
    compileOnly(project(":compiler:fir:entrypoint"))
}

optInToExperimentalCompilerApi()

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

runtimeJar()
sourcesJar()
javadocJar()
