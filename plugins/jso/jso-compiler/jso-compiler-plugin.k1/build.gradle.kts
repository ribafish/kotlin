description = "Kotlin JavaScript Object Compiler Plugin (K1)"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compileOnly(intellijCore())
    compileOnly(project(":compiler:frontend"))
    compileOnly(project(":compiler:cli-common"))
    compileOnly(project(":js:js.frontend"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

runtimeJar()
sourcesJar()
javadocJar()
