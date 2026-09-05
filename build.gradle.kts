plugins {
    base
    kotlin("jvm") version "2.4.10" apply false
}
allprojects {
    group = "io.brule.tasking"
    version = rootProject.file("VERSION").readText().trim()
}
subprojects {
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
