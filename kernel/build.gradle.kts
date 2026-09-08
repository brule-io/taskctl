plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation("org.postgresql:postgresql:42.7.13")
    testImplementation(project(":repository"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

// Explicit experiment, isolated from the released CLI and its native corpus.
val integration = sourceSets.create("integration")
configurations[integration.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integration.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
integration.compileClasspath += sourceSets.main.get().output
integration.runtimeClasspath += sourceSets.main.get().output
tasks.register<Test>("integrationTest") {
    description = "Bounded real PostgreSQL/HTTP ledger conformance (explicit disposable database required)"
    group = "verification"
    testClassesDirs = integration.output.classesDirs
    classpath = integration.runtimeClasspath
    useJUnitPlatform()
    systemProperty("kernel.proof", rootProject.layout.buildDirectory.file("proof/kernel.json").get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}
