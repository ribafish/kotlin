description = "Kotlin JavaScript Object Compiler Plugin (K1)"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compileOnly(intellijCore())
    compileOnly(project(":core:compiler.common.jvm"))
    compileOnly(project(":compiler:frontend"))
    compileOnly(project(":js:js.frontend"))
    compileOnly(project(":compiler:cli-common"))
    compileOnly(project(":compiler:ir.backend.common")) // needed for CompilationException

    implementation(project(":kotlinx-jso-compiler-plugin.common"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

runtimeJar()
sourcesJar()
javadocJar()
