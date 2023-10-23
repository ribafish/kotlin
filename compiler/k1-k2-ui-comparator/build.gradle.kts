plugins {
    application
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")

    testImplementation(projectTests(":compiler:fir:analysis-tests"))
    // Without this dependency there will be:
    // `java.lang.ClassNotFoundException: org.codehaus.stax2.typed.TypedXMLStreamException`
    testImplementation(projectTests(":generators:test-generator"))

    testImplementation(libs.junit4)
    testApi(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

application {
    mainClass.set("org.jetbrains.kotlin.k1k2uicomparator.MainKt")
}

tasks.register<JavaExec>("runTest") {
    workingDir = rootDir
    group = ApplicationPlugin.APPLICATION_GROUP
    classpath(sourceSets.test.get().runtimeClasspath)
    mainClass.set("org.jetbrains.kotlin.k1k2uicomparator.test.RunKt")
}

tasks.register<Jar>("jarTest") {
    group = BasePlugin.BUILD_GROUP

    from(
        sourceSets.main.get().output,
        sourceSets.test.get().output,
    )

    manifest {
        attributes["Class-Path"] = configurations.testRuntimeClasspath.get().joinToString(" ")
        attributes["Main-Class"] = "org.jetbrains.kotlin.k1k2uicomparator.test.RunKt"
    }
}

sourceSets {
    "main" {
        projectDefault()
        generatedTestDir()
    }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

projectTest(jUnitMode = JUnitMode.JUnit5, parallel = true) {
    workingDir = rootDir
    useJUnitPlatform()
}

kotlin {
    // Hopefully, setting versions explicitly prevents
    // `Internal Server Error: Please provide a valid jdkVersion`
    jvmToolchain(17)

    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
