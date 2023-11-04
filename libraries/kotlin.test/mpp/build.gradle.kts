@file:Suppress("UNUSED_VARIABLE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import plugins.configureDefaultPublishing
import plugins.configureKotlinPomAttributes

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

enum class JvmTestFramework {
    JUnit,
    JUnit5,
    TestNG;

    fun lowercase() = name.lowercase()
}
val jvmTestFrameworks = JvmTestFramework.values().toList()

kotlin {
    jvm {
        compilations {
            val main by getting
            val test by getting
            jvmTestFrameworks.forEach { framework ->
                val frameworkMain = create("$framework") {
                    associateWith(main)
                }
                create("${framework}Test") {
                    associateWith(frameworkMain)
                }
            }
            test.associateWith(getByName("JUnit"))
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
        val commonTest by getting {
            kotlin.srcDir("../common/src/test/kotlin")
        }
        val jvmMain by getting {
            dependsOn(assertionsCommonMain)
            kotlin.srcDir("../jvm/src/main/kotlin")
        }
        val jvmTest by getting {
            kotlin.srcDir("../jvm/src/test/kotlin")
        }
        val jvmJUnit by getting {
            dependsOn(annotationsCommonMain)
            kotlin.srcDir("../junit/src/main/kotlin")
            resources.srcDir("../junit/src/main/resources")
            dependencies {
                api("junit:junit:4.13.2")
            }
        }
        val jvmJUnitTest by getting {
            kotlin.srcDir("../junit/src/test/kotlin")
        }
        val jvmJUnit5 by getting {
            dependsOn(annotationsCommonMain)
            kotlin.srcDir("../junit5/src/main/kotlin")
            resources.srcDir("../junit5/src/main/resources")
            dependencies {
                compileOnly("org.junit.jupiter:junit-jupiter-api:5.0.0")
            }
        }
        val jvmJUnit5Test by getting {
            kotlin.srcDir("../junit5/src/test/kotlin")
            dependencies {
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }
        val jvmTestNG by getting {
            dependsOn(annotationsCommonMain)
            kotlin.srcDir("../testng/src/main/kotlin")
            resources.srcDir("../testng/src/main/resources")
            dependencies {
                api("org.testng:testng:6.13.1")
            }
        }
        val jvmTestNGTest by getting {
            kotlin.srcDir("../testng/src/test/kotlin")
        }
        val jsMain by getting {
            dependsOn(assertionsCommonMain)
            dependsOn(annotationsCommonMain)
            kotlin.srcDir("../js/src/main/kotlin")
        }
        val jsTest by getting {
            kotlin.srcDir("../js/src/test/kotlin")
        }
    }
}


tasks {
    val allMetadataJar by existing(Jar::class) {
        archiveClassifier = "all"
    }
    val jvmJar by existing(Jar::class) {
        archiveAppendix = null
        manifestAttributes(manifest, "Test")
    }
    val jvmSourcesJar by existing(Jar::class) {
        kotlin.sourceSets["annotationsCommonMain"].let { sourceSet ->
            into(sourceSet.name) {
                from(sourceSet.kotlin)
            }
        }
    }
    val jvmJarTasks = jvmTestFrameworks.map { framework ->
        register("jvm${framework}Jar", Jar::class) {
            archiveAppendix = framework.lowercase()
            from(kotlin.jvm().compilations[framework.name].output.allOutputs)
            manifestAttributes(manifest, "Test")
        }
    }
    val jvmSourcesJarTasks = jvmTestFrameworks.map { framework ->
        register("jvm${framework}SourcesJar", Jar::class) {
            archiveAppendix = framework.lowercase()
            archiveClassifier = "sources"
            kotlin.jvm().compilations[framework.name].allKotlinSourceSets.forEach {
                from(it.kotlin.sourceDirectories) { into(it.name) }
                from(it.resources.sourceDirectories) { into(it.name) }
            }
        }
    }
    val assemble by existing {
        dependsOn(jvmJarTasks)
    }

    val jvmTestTasks = jvmTestFrameworks.map { framework ->
        register("jvm${framework}Test", Test::class) {
            group = "verification"
            val compilation = kotlin.jvm().compilations["${framework}Test"]
            classpath = compilation.runtimeDependencyFiles + compilation.output.allOutputs
            testClassesDirs = compilation.output.classesDirs
            when (framework) {
                JvmTestFramework.JUnit -> useJUnit()
                JvmTestFramework.JUnit5 -> useJUnitPlatform()
                JvmTestFramework.TestNG -> useTestNG()
            }
        }
    }
    val allTests by existing {
        dependsOn(jvmTestTasks)
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

}

configurations {
    val metadataApiElements by getting
    metadataApiElements.outgoing.capability(kotlinTestCapability)

    for (framework in jvmTestFrameworks) {
        val frameworkCapability = "$group:kotlin-test-framework-${framework.lowercase()}:$version"
        metadataApiElements.outgoing.capability(frameworkCapability)

        val (apiElements, runtimeElements, sourcesElements) = listOf(KotlinUsages.KOTLIN_API, KotlinUsages.KOTLIN_RUNTIME, KotlinUsages.KOTLIN_SOURCES).map { usage ->
            val name = "jvm$framework${usage.substringAfter("kotlin-").replaceFirstChar { it.uppercase() }}Elements"
            create(name) {
                isCanBeResolved = false
                isCanBeConsumed = true
                outgoing.capability(baseCapability)
                outgoing.capability(frameworkCapability)
                attributes {
                    attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
                    attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(when (usage) {
                        KotlinUsages.KOTLIN_API -> Usage.JAVA_API
                        KotlinUsages.KOTLIN_RUNTIME -> Usage.JAVA_RUNTIME
                        KotlinUsages.KOTLIN_SOURCES -> Usage.JAVA_RUNTIME
                        else -> error(usage)
                    }))
                    when (usage) {
                        KotlinUsages.KOTLIN_SOURCES -> {
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
                            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
                        }
                        KotlinUsages.KOTLIN_API -> {
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                            extendsFrom(getByName("jvm${framework}Api"))
                        }
                        KotlinUsages.KOTLIN_RUNTIME -> {
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                            extendsFrom(getByName("jvm${framework}Api"))
                            extendsFrom(getByName("jvm${framework}Implementation"))
                            extendsFrom(getByName("jvm${framework}RuntimeOnly"))
                        }
                        else -> error(usage)
                    }
                }
            }
        }
        dependencies {
            apiElements(project)
            runtimeElements(project)
            when (framework) {
                JvmTestFramework.JUnit -> {}
                JvmTestFramework.JUnit5 -> {
                    apiElements("org.junit.jupiter:junit-jupiter-api:5.6.3")
                    runtimeElements("org.junit.jupiter:junit-jupiter-engine:5.6.3")
                }
                JvmTestFramework.TestNG -> {}
            }
        }
        artifacts {
            add(apiElements.name, tasks.named<Jar>("jvm${framework}Jar"))
            add(runtimeElements.name, tasks.named<Jar>("jvm${framework}Jar"))
            add(sourcesElements.name, tasks.named<Jar>("jvm${framework}SourcesJar"))
        }
    }
    all {
        println(name)
    }
}


configureDefaultPublishing()

open class ComponentsFactoryAccess
@javax.inject.Inject
constructor(val factory: SoftwareComponentFactory)

val componentFactory = objects.newInstance<ComponentsFactoryAccess>().factory

val emptyJavadocJar by tasks.creating(org.gradle.api.tasks.bundling.Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    val artifactBaseName = base.archivesName.get()
    configureMultiModuleMavenPublishing {
        val rootModule = module("rootModule") {
            mavenPublication {
                artifactId = artifactBaseName
                configureKotlinPomAttributes(project, "Kotlin Test Library")
                artifact(emptyJavadocJar)
            }
            variant("metadataApiElements")
            variant("jvmApiElements")
            variant("jvmRuntimeElements")
            variant("jvmSourcesElements")
            variant("nativeApiElements") {
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                    attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named("non-jvm"))
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_API))
                    attribute(KotlinPlatformType.attribute, KotlinPlatformType.native)
                }
            }
        }

        val js = module("jsModule") {
            mavenPublication {
                artifactId = "$artifactBaseName-js"
                configureKotlinPomAttributes(project, "Kotlin Test Library for JS", packaging = "klib")
            }
            variant("jsApiElements")
            variant("jsRuntimeElements")
            variant("jsSourcesElements")
        }
        val frameworkModules = jvmTestFrameworks.map { framework ->
            module("${framework.lowercase()}Module") {
                mavenPublication {
                    artifactId = "$artifactBaseName-${framework.lowercase()}"
                    configureKotlinPomAttributes(project, "Kotlin Test Library for ${framework}")
                }
                variant("jvm${framework}ApiElements")
                variant("jvm${framework}RuntimeElements")
                variant("jvm${framework}SourcesElements")
            }
        }


        // Makes all variants from accompanying artifacts visible through `available-at`
        rootModule.include(js, *frameworkModules.toTypedArray())
    }

//    publications {
//    }
}

fun copyAttributes(from: AttributeContainer, to: AttributeContainer,) {
    // capture type argument T
    fun <T : Any> copyOneAttribute(from: AttributeContainer, to: AttributeContainer, key: Attribute<T>) {
        val value = checkNotNull(from.getAttribute(key))
        to.attribute(key, value)
    }
    for (key in from.keySet()) {
        copyOneAttribute(from, to, key)
    }
}

class MultiModuleMavenPublishingConfiguration() {
    val modules = mutableMapOf<String, Module>()

    class Module(val name: String) {
        val variants = mutableMapOf<String, Variant>()
        val includes = mutableSetOf<Module>()

        class Variant(
            val configurationName: String
        ) {
            var name: String = configurationName
            val attributesConfigurations = mutableListOf<AttributeContainer.() -> Unit>()
            fun attributes(code: AttributeContainer.() -> Unit) {
                attributesConfigurations += code
            }

            val artifactsWithConfigurations = mutableListOf<Pair<Any, ConfigurablePublishArtifact.() -> Unit>>()
            fun artifact(file: Any, code: ConfigurablePublishArtifact.() -> Unit = {}) {
                artifactsWithConfigurations += file to code
            }

            val configurationConfigurations = mutableListOf<Configuration.() -> Unit>()
            fun configuration(code: Configuration.() -> Unit) {
                configurationConfigurations += code
            }

            val variantDetailsConfigurations = mutableListOf<ConfigurationVariantDetails.() -> Unit>()
            fun configureVariantDetails(code: ConfigurationVariantDetails.() -> Unit) {
                variantDetailsConfigurations += code
            }
        }

        val mavenPublicationConfigurations = mutableListOf<MavenPublication.() -> Unit>()
        fun mavenPublication(code: MavenPublication.() -> Unit) {
            mavenPublicationConfigurations += code
        }

        fun variant(fromConfigurationName: String, code: Variant.() -> Unit = {}): Variant {
            val variant = variants.getOrPut(fromConfigurationName) { Variant(fromConfigurationName) }
            variant.code()
            return variant
        }

        fun include(vararg modules: Module) {
            includes.addAll(modules)
        }
    }

    fun module(name: String, code: Module.() -> Unit): Module {
        val module = modules.getOrPut(name) { Module(name) }
        module.code()
        return module
    }
}

fun configureMultiModuleMavenPublishing(code: MultiModuleMavenPublishingConfiguration.() -> Unit) {
    val publishingConfiguration = MultiModuleMavenPublishingConfiguration()
    publishingConfiguration.code()

    val components = publishingConfiguration
        .modules
        .mapValues { (_, module) -> project.createModulePublication(module) }

    val componentsWithExternals = publishingConfiguration
        .modules
        .filter { (_, module) -> module.includes.isNotEmpty() }
        .mapValues { (moduleName, module) ->
            val mainComponent = components[moduleName] ?: error("Component with name $moduleName wasn't created")
            val externalComponents = module.includes
                .map { components[it.name] ?: error("Component with name ${it.name} wasn't created") }
                .toSet()
            ComponentWithExternalVariants(mainComponent, externalComponents)
        }

    // override some components wih items from componentsWithExternals
    val mergedComponents = components + componentsWithExternals

    val publicationsContainer = publishing.publications
    for ((componentName, component) in mergedComponents) {
        publicationsContainer.create<MavenPublication>(componentName) {
            from(component)
            val module = publishingConfiguration.modules[componentName]!!
            module.mavenPublicationConfigurations.forEach { configure -> configure() }
        }
    }
}


fun Project.createModulePublication(module: MultiModuleMavenPublishingConfiguration.Module): SoftwareComponent {
    val component = componentFactory.adhoc(module.name)
    module.variants.values.forEach { addVariant(component, it) }

    val newNames = module.variants.map { it.key to it.value.name }.filter { it.first != it.second }.toMap()
    return if (newNames.isNotEmpty()) {
        ComponentWithRenamedVariants(newNames, component as SoftwareComponentInternal)
    } else {
        component
    }
}

fun Project.addVariant(component: AdhocComponentWithVariants, variant: MultiModuleMavenPublishingConfiguration.Module.Variant) {
    val configuration = configurations.getOrCreate(variant.configurationName)
    configuration.apply {
        isCanBeResolved = false
        isCanBeConsumed = true

        variant.attributesConfigurations.forEach { configure -> attributes.configure() }
    }

    for ((artifactNotation, configure) in variant.artifactsWithConfigurations) {
        artifacts.add(configuration.name, artifactNotation) {
            configure()
        }
    }

    for (configure in variant.configurationConfigurations) {
        configuration.apply(configure)
    }

    component.addVariantsFromConfiguration(configuration) {
        variant.variantDetailsConfigurations.forEach { configure -> configure() }
    }
}

private class RenamedVariant(val newName: String, context: UsageContext) : UsageContext by context {
    override fun getName(): String = newName
}

private class ComponentWithRenamedVariants(
    val newNames: Map<String, String>,
    private val base: SoftwareComponentInternal
): SoftwareComponentInternal by base {

    override fun getName(): String = base.name
    override fun getUsages(): Set<UsageContext> {
        return base.usages.map {
            val newName = newNames[it.name]
            if (newName != null) {
                RenamedVariant(newName, it)
            } else {
                it
            }
        }.toSet()
    }
}

private class ComponentWithExternalVariants(
    private val mainComponent: SoftwareComponent,
    private val externalComponents: Set<SoftwareComponent>
) : ComponentWithVariants, SoftwareComponentInternal {
    override fun getName(): String = mainComponent.name

    override fun getUsages(): Set<UsageContext> = (mainComponent as SoftwareComponentInternal).usages

    override fun getVariants(): Set<SoftwareComponent> = externalComponents
}

// endregion

tasks.withType<GenerateModuleMetadata> {
    val publication = publication.get() as MavenPublication
    // alter capabilities of leaf JVM framework artifacts published by "available-at" coordinates
    if (jvmTestFrameworks.map { it.lowercase() }.any { publication.artifactId.endsWith(it) }) {
        doLast {
            val output = outputFile.get().asFile
            val gson = GsonBuilder().setPrettyPrinting().create()
            val moduleJson = output.bufferedReader().use { gson.fromJson(it, JsonObject::class.java) }
            val variants = moduleJson.getAsJsonArray("variants")
            variants.forEach { variant ->
                variant as JsonObject
                variant.remove("capabilities")
            }
            output.bufferedWriter().use { writer -> gson.toJson(moduleJson, writer) }
        }
    }
}