plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
    testImplementation(project(":compatibility"))
    testImplementation(project(":core"))
    testImplementation(project(":repository"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    systemProperty("tasking.root", rootProject.projectDir.absolutePath)
    inputs.files(rootProject.fileTree(".") {
        include("core/src/**/*.kt", "repository/src/**/*.kt", "compatibility/src/**/*.kt", "cli/src/**/*.kt", "conformance/src/**/*.kt")
    }).withPropertyName("architecturalPolicySources")
}

tasks.register<Jar>("proofJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("proof")
    from(sourceSets.test.get().output.classesDirs)
}
