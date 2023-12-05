description = "Kotlin 2 Swift Compiler Plugin (Backend)"

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":native:swift:sir"))
    implementation(project(":native:swift:sir-analysis-api"))

    implementation(project(":analysis:analysis-api-standalone"))
}

optInToUnsafeDuringIrConstructionAPI()

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

runtimeJar()
sourcesJar()
javadocJar()
