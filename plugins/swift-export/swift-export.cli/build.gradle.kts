description = "Kotlin 2 Swift Compiler Plugin (CLI)"

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":kotlin-swift-export-compiler-plugin.backend"))
    compileOnly(project(":compiler:plugin-api"))
//    compileOnly(project(":compiler:util"))
    compileOnly(project(":compiler:fir:entrypoint"))
//    compileOnly(project(":compiler:backend"))
//    compileOnly(project(":compiler:ir.backend.common"))
//    compileOnly(intellijCore())
}

optInToExperimentalCompilerApi()

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

runtimeJar()
sourcesJar()
javadocJar()
