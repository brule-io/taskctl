plugins { kotlin("jvm"); `java-library` }
kotlin { jvmToolchain(21) }
dependencies {
    implementation("org.yaml:snakeyaml:2.5")
    implementation("org.commonmark:commonmark:0.24.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
