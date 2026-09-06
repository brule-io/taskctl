plugins {
    base
    kotlin("jvm") version "2.4.10" apply false
    id("org.graalvm.buildtools.native") version "0.11.5" apply false
}
allprojects {
    group = "io.brule.tasking"
    version = rootProject.file("VERSION").readText().trim()
    description = "Apache-2.0 taskctl protocol and reference tooling"
}
subprojects {
    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) { into("META-INF") }
        from(rootProject.file("NOTICE.md")) { into("META-INF") }
        manifest.attributes("Implementation-Version" to project.version, "Bundle-License" to "Apache-2.0")
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    dependencyLocking { lockAllConfigurations() }
}
tasks.named("check") { dependsOn(":core:check", ":repository:check", ":compatibility:check", ":conformance:check", ":cli:check") }
