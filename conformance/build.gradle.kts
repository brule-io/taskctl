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

// Opt-in research laboratory; not part of the released JVM/native parity corpus.
val abstractMachine = sourceSets.create("abstractMachine")
configurations[abstractMachine.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[abstractMachine.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
tasks.register<Test>("abstractMachineTest") {
    description = "Falsification specimens for the fixed two-counter execution unit"
    group = "verification"
    testClassesDirs = abstractMachine.output.classesDirs
    classpath = abstractMachine.runtimeClasspath
    useJUnitPlatform()
    systemProperty("tasking.root", rootProject.projectDir.absolutePath)
    systemProperty("tasking.machine.classpath", abstractMachine.runtimeClasspath.asPath)
    systemProperty("tasking.machine.output", layout.buildDirectory.dir("abstract-machine-evidence").get().asFile.absolutePath)
    outputs.dir(layout.buildDirectory.dir("abstract-machine-evidence"))
}
