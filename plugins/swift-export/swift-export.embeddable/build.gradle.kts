plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    embedded(project(":kotlin-swift-export-compiler-plugin")) { isTransitive = false }
}

publish {
    artifactId = artifactId.replace(".", "-")
}

runtimeJar(rewriteDefaultJarDepsToShadedCompiler())
sourcesJarWithSourcesFromEmbedded(
    project(":kotlin-swift-export-compiler-plugin").tasks.named<Jar>("sourcesJar")
)
javadocJarWithJavadocFromEmbedded(
    project(":kotlin-swift-export-compiler-plugin").tasks.named<Jar>("javadocJar")
)
