plugins { kotlin("jvm"); application; id("org.graalvm.buildtools.native") }
kotlin { jvmToolchain(21) }
application { mainClass.set("io.brule.tasking.cli.MainKt"); applicationName = "taskctl" }
tasks.processResources { from(rootProject.file("VERSION")) }

graalvmNative {
    toolchainDetection.set(false)
    metadataRepository { enabled.set(false) }
    binaries.named("main") {
        imageName.set("taskctl")
        fallback.set(false)
        buildArgs.addAll("-march=compatibility", "-J-Xmx5g")
        resources.includedPatterns.add("VERSION")
    }
    testSupport.set(false)
}
dependencies {
    implementation(project(":repository"))
    implementation(project(":compatibility"))
    implementation(project(":core"))
}
