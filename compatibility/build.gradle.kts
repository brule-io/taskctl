plugins { kotlin("jvm"); `java-library` }
kotlin { jvmToolchain(21) }
dependencies {
    api(project(":core"))
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
