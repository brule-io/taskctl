plugins { kotlin("jvm"); id("org.graalvm.buildtools.native") }
kotlin { jvmToolchain(21) }

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":repository"))
    testImplementation(project(":compatibility"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

// Reuse the compiled original tests, including their module-internal access.
// Compiler/PSI policy tests remain a JVM source gate, not runtime behavior.
val modules = listOf("core", "repository", "compatibility", "conformance")
val corpusClasses = files(modules.map { rootProject.file("$it/build/classes/kotlin/test") })
val corpusResources = files(modules.map { rootProject.file("$it/build/resources/test") })
tasks.test {
    dependsOn(modules.map { ":$it:testClasses" })
    testClassesDirs = corpusClasses
    classpath += corpusClasses + corpusResources
    exclude("**/ArchitecturePolicyTest*")
    systemProperty("tasking.root", rootProject.projectDir.absolutePath)
}
graalvmNative {
    toolchainDetection.set(false)
    metadataRepository { enabled.set(false) }
    binaries.named("test") {
        classpath.from(corpusClasses, corpusResources)
        fallback.set(false)
        buildArgs.addAll("-march=compatibility", "-O0", "-J-Xmx5g")
        // Historical resources are needed by the existing provenance tests only.
        resources.includedPatterns.addAll("daemon/.*", "planning-history/.*", "fantastikt-import/.*")
    }
}
