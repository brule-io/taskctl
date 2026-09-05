plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(21) }
application { mainClass.set("io.brule.tasking.cli.MainKt"); applicationName = "taskctl" }
tasks.processResources { from(rootProject.file("VERSION")) }
dependencies {
    implementation(project(":repository"))
    implementation(project(":compatibility"))
    implementation(project(":core"))
}
