package io.brule.tasking.cli

import io.brule.tasking.core.*
import java.nio.file.Files
import java.nio.file.Path

/** Process and distribution identity stay outside repository/contract semantics. */
internal object ToolRuntime {
    val version: String get() = javaClass.getResourceAsStream("/VERSION")!!.bufferedReader().use { it.readText().trim() }
    val implementation: String get() = if (System.getProperty("org.graalvm.nativeimage.imagecode") == "runtime") "native" else "jvm"
    fun distribution(): Path? = (System.getProperty("taskctl.distribution") ?: System.getenv("TASKCTL_DISTRIBUTION"))?.let(Path::of)
        ?: if (implementation == "native") ProcessHandle.current().info().command().orElse(null)?.let { Path.of(it).parent } else null

    fun versionResult() = obj("tool" to StringValue("taskctl"), "version" to StringValue(version))

    fun info(): ObjectValue {
        val build = distribution()?.resolve("distribution.json")?.takeIf(Files::isRegularFile)?.let {
            YamlValues.parse(Files.readString(it)).value as? ObjectValue ?: error("invalid distribution identity")
        }
        return ObjectValue(versionResult().fields + mapOf(
            "implementation" to StringValue(implementation),
            "java_runtime" to StringValue(System.getProperty("java.runtime.version")),
            "vm" to StringValue(System.getProperty("java.vm.name")),
            "native_v1" to StringValue("not-frozen"),
            "build" to (build ?: NullValue),
        ))
    }

    /** Recognize before repository argument parsing, including malformed repositories. */
    fun versionCommand(args: List<String>): Boolean {
        if (args.firstOrNull() !in setOf("--version", "-V", "version")) return false
        val json = args == listOf("version", "--format", "json")
        require(args.size == 1 || json || args == listOf("version", "--format", "text")) { "usage: taskctl --version | -V | version [--format json|text]" }
        if (json) println(Json.encode(obj("api" to StringValue("taskctl.cli/alpha1"), "command" to StringValue("version"), "result" to versionResult())))
        else println("taskctl $version")
        return true
    }
}
