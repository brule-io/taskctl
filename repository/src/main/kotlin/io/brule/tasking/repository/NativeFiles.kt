package io.brule.tasking.repository

import io.brule.tasking.core.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*

internal object NativeFiles {
    fun objectValue(source: String): ObjectValue = YamlValues.parse(source).value as? ObjectValue ?: error("object required")
    fun locate(root: Path, relative: String): Path {
        require(!relative.contains('\\') && relative.split('/').none { it == ".." || it.isEmpty() }) { "unsafe ledger path" }
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && path != root) { "path escapes repository" }
        var current: Path? = path
        while (current != null) {
            require(!Files.isSymbolicLink(current)) { "symbolic links are not supported in the native ledger: $current" }
            current = current.parent
        }
        return path
    }
    fun atomicWrite(root: Path, relative: String, content: String) {
        val target = locate(root, relative)
        Files.createDirectories(target.parent)
        val staging = locate(root, ".agents/runtime")
        Files.createDirectories(staging)
        val temporary = Files.createTempFile(staging, ".taskctl-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    fun flatToml(source: String, keys: Set<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        source.lineSequence().filter { it.isNotBlank() && !it.trimStart().startsWith('#') }.forEach { line ->
            val match = Regex("^([a-z_]+)\\s*=\\s*(\".*\")\\s*$").matchEntire(line.trim()) ?: error("expected a quoted scalar TOML field")
            val key = match.groupValues[1]
            require(key in keys && key !in result) { "unknown or duplicate config field: $key" }
            result[key] = (YamlValues.parse(match.groupValues[2]).value as? StringValue)?.value ?: error("config string required")
        }
        require(result.keys == keys) { "missing config fields: ${keys - result.keys}" }
        return result
    }
    fun fileName(id: String): String {
        val readable = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
        return readable + "-" + Canonical.sha256(id.toByteArray()).take(16) + ".yaml"
    }
    fun regularFiles(root: Path, relative: String): Map<String, String> {
        val directory = locate(root, relative)
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return emptyMap()
        return Files.walk(directory).use { paths -> paths.sorted().filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.toList().associate { path ->
            val name = root.relativize(path).toString().replace('\\', '/')
            locate(root, name)
            require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "unsupported ledger entry: $name" }
            require(name.endsWith(".yaml") || name.endsWith(".json") || name.endsWith(".md")) { "unexpected ledger file: $name" }
            name to Files.readString(path)
        } }
    }
}
