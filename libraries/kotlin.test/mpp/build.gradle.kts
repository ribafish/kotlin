@file:Suppress("UNUSED_VARIABLE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import plugins.configureDefaultPublishing

plugins {
    id("kotlin-multiplatform")
    `maven-publish`
    signing
}

description = "Kotlin Test Library"
base.archivesName = "kotlin-test-mpp"

configureJvmToolchain(JdkMajorVersion.JDK_1_8)

val kotlinTestCapability = "$group:${base.archivesName.get()}:$version" // add to variants with explicit capabilities when the default one is needed, too
val baseCapability = "$group:kotlin-test-framework:$version"
val implCapability = "$group:kotlin-test-framework-impl:$version"


kotlin {
    lateinit var jvmMainCompilation: KotlinJvmCompilation
    jvm {
        compilations {
            val main by getting {
            }
            jvmMainCompilation = main
//            val jUnit4 by creating {
//                associateWith(main)
//            }
//            val jUnit5 by creating {
//                associateWith(main)
//            }
//            val testNg by creating {
//                associateWith(main)
//            }
        }
    }
    jvm("jvmJUnit") {
        compilations {
            val main by getting {
//                KotlinCompilationConfigurationsContainer::class.memberProperties.forEach { prop ->
//                    println("${prop.name}: ${prop.get(configurations)}")
//                }
//                associateWith(jvmMainCompilation)
            }
        }
    }
    js {
        if (!kotlinBuildProperties.isTeamcityBuild) {
            browser {}
        }
        nodejs {}
    }

    targets.all {
        compilations.all {
            compilerOptions.configure {
                optIn.add("kotlin.contracts.ExperimentalContracts")
                freeCompilerArgs.addAll(listOf(
                    "-Xallow-kotlin-package",
                    "-Xexpect-actual-classes",
                ))
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinStdlib())
            }
        }
        val annotationsCommonMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("../annotations-common/src/main/kotlin")
        }
        val assertionsCommonMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("../common/src/main/kotlin")
        }
        val jvmMain by getting {
            dependsOn(assertionsCommonMain)
            kotlin.srcDir("../jvm/src/main/kotlin")
        }
        val jvmJUnitMain by getting {
            dependsOn(annotationsCommonMain)
            kotlin.srcDir("../junit/src/main/kotlin")
            resources.srcDir("../junit/src/main/resources")
            dependencies {
                api(jvmMainCompilation.output.allOutputs)
                api("junit:junit:4.13.2")
            }
        }
        val jsMain by getting {
            dependsOn(assertionsCommonMain)
            dependsOn(annotationsCommonMain)
            kotlin.srcDir("../js/src/main/kotlin")
        }
    }
}

configurations {
    val metadataApiElements by getting {
        outgoing.capability(kotlinTestCapability)
    }
    for (framework in listOf("JUnit")) {
        val frameworkCapability = "$group:kotlin-test-framework-${framework.lowercase()}:$version"
        for (usage in listOf(KotlinUsages.KOTLIN_API, KotlinUsages.KOTLIN_RUNTIME, KotlinUsages.KOTLIN_SOURCES)) {
            val name = "jvm$framework${usage.substringAfter("kotlin-").replaceFirstChar { it.uppercase() }}Elements"
            getByName(name) {
                outgoing.capability(baseCapability)
                outgoing.capability(frameworkCapability)
            }
            if (usage != KotlinUsages.KOTLIN_SOURCES) {
                dependencies {
                    add(name, project)
                }
            }
        }
        metadataApiElements {
            outgoing.capability(frameworkCapability)
        }
    }
}

tasks {
    val jvmJar by existing(Jar::class) {
        archiveAppendix = null
        manifestAttributes(manifest, "Test")
    }
//    val junitJar by registering(Jar::class) {
//        archiveAppendix = "junit"
//        from(kotlin.jvm().compilations["jUnit4"].output.allOutputs)
//        manifestAttributes(manifest, "Test")
//    }
//    val assemble by existing {
//        dependsOn(junitJar)
//    }
    val jvmJUnitJar by existing(Jar::class) {
        archiveAppendix = "junit"
    }

    val generateProjectStructureMetadata by existing {
        val outputFile = file("build/kotlinProjectStructureMetadata/kotlin-project-structure-metadata.json")
        val outputTestFile = file("kotlin-project-structure-metadata.beforePatch.json")
        val patchedFile = file("kotlin-project-structure-metadata.json")

        inputs.file(patchedFile)
//        inputs.file(outputTestFile)

        doLast {
            /*
            Check that the generated 'outputFile' by default matches our expectations stored in the .beforePatch file
            This will fail if the kotlin-project-structure-metadata.json file would change unnoticed (w/o updating our patched file)
             */
            run {
                val outputFileText = outputFile.readText().trim()
//                val expectedFileContent = outputTestFile.readText().trim()
//                if (outputFileText != expectedFileContent)
//                    error(
//                        "${outputFile.path} file content does not match expected content\n\n" +
//                                "expected:\n\n$expectedFileContent\n\nactual:\n\n$outputFileText"
//                    )
            }

            patchedFile.copyTo(outputFile, overwrite = true)
        }
    }

    withType<GenerateModuleMetadata> {
        // temporary disable Gradle metadata in kotlin-test-junit artifact
        // until we find a solution for duplicated capabilities
        if (listOf("junit").any { it in (publication.get() as MavenPublication).artifactId }) {
            enabled = false
        }
    }
}


configureDefaultPublishing()
