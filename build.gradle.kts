plugins {
    base
    kotlin("jvm") version "2.4.10" apply false
}
allprojects {
    group = "io.brule.tasking"
    version = "0.1.0-dev.1"
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
tasks.named("check") { dependsOn(":core:check", ":compatibility:check", ":conformance:check", ":cli:check") }
