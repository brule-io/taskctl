plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation(project(":kernel"))
    implementation("software.amazon.smithy:smithy-model:1.73.0")
    testImplementation("org.postgresql:postgresql:42.7.13")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
tasks.test { systemProperty("taskctl.root", rootProject.projectDir.absolutePath) }
tasks.register<JavaExec>("generate") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.brule.tasking.idl.GenerateKt")
    args(rootProject.projectDir.absolutePath)
}
val integration = sourceSets.create("integration")
configurations[integration.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integration.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
integration.compileClasspath += sourceSets.main.get().output
integration.runtimeClasspath += sourceSets.main.get().output
tasks.register<Test>("integrationTest") {
    testClassesDirs = integration.output.classesDirs
    classpath = integration.runtimeClasspath
    useJUnitPlatform()
    systemProperty("taskctl.root", rootProject.projectDir.absolutePath)
    outputs.upToDateWhen { false }
}
