import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Kotlin JVM metadata manipulation library"

plugins {
    kotlin("jvm")
    id("jps-compatible")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("org.jetbrains.dokka")
}

/*
 * To publish this library use `:kotlinx-metadata-jvm:publish` task and specify the following parameters
 *
 *      - `-PdeployVersion=1.2.nn`: the version of the standard library dependency to put into .pom
 *      - `-PkotlinxMetadataDeployVersion=0.0.n`: the version of the library itself
 *      - `-PdeployRepoUrl=repository_url`: (optional) the url of repository to deploy to;
 *          if not specified, the local directory repository `build/repo` will be used
 *      - `-PdeployRepoUsername=username`: (optional) the username to authenticate in the deployment repository
 *      - `-PdeployRepoPassword=password`: (optional) the password to authenticate in the deployment repository
 */
group = "org.jetbrains.kotlin"
val deployVersion = findProperty("kotlinxMetadataDeployVersion") as String?
version = deployVersion ?: "0.1-SNAPSHOT"

//kotlin {
//    explicitApiWarning()
//}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

val embedded by configurations
embedded.isTransitive = false
configurations.getByName("compileOnly").extendsFrom(embedded)
configurations.getByName("testApi").extendsFrom(embedded)

dependencies {
    api(kotlinStdlib())
    embedded(project(":kotlinx-metadata"))
    embedded(project(":core:metadata"))
    embedded(project(":core:metadata.jvm"))
    embedded(protobufLite())
    testImplementation(project(":kotlin-test:kotlin-test-junit"))
    testImplementation(libs.junit4)
    testImplementation(commonDependency("org.jetbrains.intellij.deps:asm-all"))
    testImplementation(commonDependency("org.jetbrains.kotlin:kotlin-reflect")) { isTransitive = false }
}

kotlin {
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xallow-kotlin-package")
    }
}

if (deployVersion != null) {
    publish()
}

val runtimeJar = runtimeJarWithRelocation {
    from(mainSourceSet.output)
    exclude("**/*.proto")
    relocate("org.jetbrains.kotlin", "kotlin.metadata.internal")
}

tasks.apiBuild {
    inputJar.value(runtimeJar.flatMap { it.archiveFile })
}

apiValidation {
    ignoredPackages.add("kotlin.metadata.internal")
    nonPublicMarkers.addAll(
        listOf(
            "kotlin.metadata.internal.IgnoreInApiDump",
            "kotlin.metadata.jvm.internal.IgnoreInApiDump"
        )
    )
}

tasks.dokkaHtml.configure {
    outputDirectory.set(buildDir.resolve("dokka"))
    pluginsMapConfiguration.set(
        mapOf(
            "org.jetbrains.dokka.base.DokkaBase"
                    to """{ "templatesDir": "${projectDir.toString().replace('\\', '/')}/dokka-templates" }"""
        )
    )

    dokkaSourceSets.configureEach {
        includes.from(project.file("dokka/moduledoc.md").path)

        sourceRoots.from(project(":kotlinx-metadata").getSources())

        skipDeprecated.set(true)
        reportUndocumented.set(true)
        failOnWarning.set(true)

        perPackageOption {
            matchingRegex.set("kotlin\\.metadata\\.internal(\$|\\.).*")
            suppress.set(true)
            reportUndocumented.set(false)
        }
    }
}

sourcesJar()

javadocJar()
