apply plugin: 'kotlin'

sourceSets {
    main {
        kotlin.srcDir 'src'
        resources.srcDir "$buildDir/copyright"
    }
}

dependencies {
    api "org.jetbrains.kotlin:kotlin-stdlib:$bootstrapKotlinVersion"
    api "org.jetbrains.kotlin:kotlin-reflect:$bootstrapKotlinVersion"
}

compileKotlin {
    kotlinOptions {
        freeCompilerArgs = ["-version", "-Xdont-warn-on-error-suppression"]
    }
}

tasks.register("copyCopyrightProfile", Copy) {
    from "$rootDir/.idea/copyright"
    into "$buildDir/copyright"
    include 'apache.xml'
}

processResources {
    dependsOn(copyCopyrightProfile)
}

tasks.register("run", JavaExec) {
    group 'application'
    mainClass = 'generators.GenerateStandardLibKt'
    classpath sourceSets.main.runtimeClasspath
    args = ["${rootDir}"]
    systemProperty 'line.separator', '\n'
}

tasks.register("generateStdlibTests", JavaExec) {
    group 'application'
    mainClass = 'generators.GenerateStandardLibTestsKt'
    classpath sourceSets.main.runtimeClasspath
    args = ["${rootDir}"]
    systemProperty 'line.separator', '\n'
}

tasks.register("generateUnicodeData", JavaExec) {
    group 'application'
    mainClass = 'generators.unicode.GenerateUnicodeDataKt'
    classpath sourceSets.main.runtimeClasspath
    args = ["${rootDir}"]
}
