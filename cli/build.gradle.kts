plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(21) }
application { mainClass.set("io.brule.tasking.cli.MainKt"); applicationName = "taskctl" }
dependencies {
    implementation(project(":compatibility"))
    implementation(project(":core"))
}
