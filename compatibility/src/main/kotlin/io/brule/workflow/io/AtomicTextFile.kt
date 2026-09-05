package io.brule.workflow.io

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.LinkOption
import io.brule.workflow.diagnostics.WorkflowOperationException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public fun interface TextFileWriter {
    public fun write(
        path: Path,
        content: String,
    )
}

public fun interface PathMover {
    public fun move(
        source: Path,
        destination: Path,
    )
}

public object AtomicTextFileWriter : TextFileWriter {
    override fun write(
        path: Path,
        content: String,
    ) {
        val parent = requireNotNull(path.parent) { "path must have a parent: $path" }
        Files.createDirectories(parent)
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n').trimEnd() + "\n"
        val temporary = parent.resolve(".${path.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(
                temporary,
                normalized,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel -> channel.force(true) }
            moveReplacing(temporary, path)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    public fun move(
        source: Path,
        destination: Path,
    ) {
        Files.createDirectories(requireNotNull(destination.parent))
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination)
        }
    }

    private fun moveReplacing(
        source: Path,
        destination: Path,
    ) {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

public object AtomicPathMover : PathMover {
    override fun move(
        source: Path,
        destination: Path,
    ): Unit = AtomicTextFileWriter.move(source, destination)
}

public fun <T> withWorkflowLock(
    layout: RepositoryLayout,
    operation: () -> T,
): T {
    val normalizedLock = layout.workflowLock.toAbsolutePath().normalize()
    return processLocks.computeIfAbsent(normalizedLock) { ReentrantLock() }.withLock {
        requireConfinedLedger(layout)
        Files.createDirectories(layout.agentsDirectory)
        FileChannel
            .open(
                normalizedLock,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.lock().use { operation() }
            }
    }
}

/** Reads never create a lock or any other consumer file. Writers retain the
 * persistent lock inode. If the first writer appears during an unlocked read,
 * discard that result and repeat under a shared, read-only file lock. */
public fun <T> withWorkflowRead(layout: RepositoryLayout, operation: () -> T): T {
    val lock = layout.workflowLock.toAbsolutePath().normalize()
    return processLocks.computeIfAbsent(lock) { ReentrantLock() }.withLock {
        requireConfinedLedger(layout)
        if (!Files.exists(lock, LinkOption.NOFOLLOW_LINKS)) {
            val result = runCatching(operation)
            if (!Files.exists(lock, LinkOption.NOFOLLOW_LINKS)) return@withLock result.getOrThrow()
        }
        FileChannel.open(lock, StandardOpenOption.READ).use { channel ->
            channel.lock(0L, Long.MAX_VALUE, true).use {
                requireConfinedLedger(layout)
                operation()
            }
        }
    }
}

/** The adapter owns ledger paths only, and does not follow ledger links into
 * source trees or other repositories. Assumes cooperating local writers. */
private fun requireConfinedLedger(layout: RepositoryLayout) {
    val agents = layout.agentsDirectory
    if (!Files.exists(agents, LinkOption.NOFOLLOW_LINKS)) return
    val physicalRoot = layout.root.toRealPath()
    Files.walk(agents).use { paths ->
        paths.forEach { path ->
            val expected = physicalRoot.resolve(layout.root.relativize(path))
            if (Files.isSymbolicLink(path) || path.toRealPath() != expected) {
                throw WorkflowOperationException("ledger link is outside the adapter write contract: ${layout.display(path)}")
            }
        }
    }
}

private val processLocks: ConcurrentHashMap<Path, ReentrantLock> = ConcurrentHashMap()
