package io.brule.tasking.kernel

import io.brule.tasking.core.*
import java.sql.Connection
import java.time.Clock
import javax.sql.DataSource

/** Bounded experiment: one locked row, the shared reducer, one atomic event. */
class PostgresTaskLedger(private val dataSource: DataSource, private val ledgerKey: String,
                         private val clock: Clock = Clock.systemUTC()) : TaskLedger {
    init { require(Regex("[a-z][a-z0-9-]{0,63}").matches(ledgerKey)) { "invalid experimental ledger key" } }
    companion object {
        const val MAX_EVENTS = 512
        /** Explicit setup only. Constructors, snapshot and HTTP reads never run DDL. */
        fun initializeSchema(dataSource: DataSource) {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    connection.createStatement().use { sql ->
                        sql.execute("CREATE TABLE IF NOT EXISTS taskctl_kernel_ledgers (ledger_key TEXT PRIMARY KEY, snapshot TEXT NOT NULL, origin TEXT NOT NULL, sequence BIGINT NOT NULL CHECK(sequence >= 0), event_head TEXT)")
                        sql.execute("CREATE TABLE IF NOT EXISTS taskctl_kernel_events (ledger_key TEXT NOT NULL REFERENCES taskctl_kernel_ledgers(ledger_key), sequence BIGINT NOT NULL CHECK(sequence > 0), event_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(ledger_key, sequence), UNIQUE(ledger_key, event_id))")
                    }
                    connection.commit()
                } catch (failure: Exception) { connection.rollback(); throw failure }
            }
        }
    }
    fun create(initial: LedgerSnapshot): LedgerSnapshot {
        val snapshot = initial.copy(revision = KernelCodec.revision(initial))
        val encoded = KernelCodec.text(KernelCodec.snapshot(snapshot))
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO taskctl_kernel_ledgers (ledger_key, snapshot, origin, sequence, event_head) VALUES (?, ?, ?, 0, NULL)").use { sql ->
                sql.setString(1, ledgerKey); sql.setString(2, encoded); sql.setString(3, encoded); sql.executeUpdate()
            }
        }
        return snapshot
    }
    private data class Row(val snapshot: LedgerSnapshot, val origin: LedgerSnapshot, val sequence: Long, val head: KernelEventId?)
    private fun row(connection: Connection, locking: Boolean = false): Row {
        connection.prepareStatement("SELECT snapshot, origin, sequence, event_head FROM taskctl_kernel_ledgers WHERE ledger_key = ?" + if (locking) " FOR UPDATE" else "").use { sql ->
            sql.queryTimeout = 10; sql.setString(1, ledgerKey)
            sql.executeQuery().use { rows ->
                if (!rows.next()) throw NoSuchElementException("experimental ledger absent")
                return Row(KernelCodec.decodeSnapshot(KernelCodec.parse(rows.getString(1))), KernelCodec.decodeSnapshot(KernelCodec.parse(rows.getString(2))),
                    rows.getLong(3), rows.getString(4)?.let(KernelEventId::parseOrThrow)).also {
                    require(it.sequence in 0..MAX_EVENTS.toLong() && (it.sequence == 0L) == (it.head == null)) { "invalid stored event head" }
                }
            }
        }
    }
    override fun snapshot(): LedgerSnapshot = dataSource.connection.use { connection ->
        connection.isReadOnly = true
        row(connection).snapshot
    }
    override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult {
        KernelCodec.text(KernelCodec.transition(transition)) // Reject unsupported/oversized requests before opening a writer.
        return dataSource.connection.use { connection ->
            connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            connection.autoCommit = false
            try {
                connection.createStatement().use { it.execute("SET LOCAL lock_timeout = '5s'") }
                val current = row(connection, locking = true)
                if (current.snapshot.revision != expectedRevision) throw RevisionConflict("stale whole-ledger revision; inspect before retrying")
                require(current.sequence < MAX_EVENTS) { "bounded kernel event capacity reached" }
                val evolved = LedgerTransitions.evolve(current.snapshot, transition)
                val after = evolved.copy(revision = KernelCodec.revision(evolved))
                val encoded = KernelCodec.text(KernelCodec.snapshot(after))
                val result = TransitionResult(after.revision, changed(transition, current.snapshot), AcceptedAt.fromInstant(clock.instant()))
                val event = KernelEvent(Math.addExact(current.sequence, 1), current.head, expectedRevision, transition, result)
                val eventText = KernelCodec.text(event.encode())
                connection.prepareStatement("UPDATE taskctl_kernel_ledgers SET snapshot = ?, sequence = ?, event_head = ? WHERE ledger_key = ?").use { sql ->
                    sql.setString(1, encoded); sql.setLong(2, event.sequence); sql.setString(3, event.id.value); sql.setString(4, ledgerKey)
                    check(sql.executeUpdate() == 1) { "locked ledger absent" }
                }
                connection.prepareStatement("INSERT INTO taskctl_kernel_events (ledger_key, sequence, event_id, payload) VALUES (?, ?, ?, ?)").use { sql ->
                    sql.setString(1, ledgerKey); sql.setLong(2, event.sequence); sql.setString(3, event.id.value); sql.setString(4, eventText); sql.executeUpdate()
                }
                connection.commit() // No response is issued before both records commit.
                result
            } catch (failure: Exception) { connection.rollback(); throw failure }
        }
    }
    private fun events(connection: Connection, after: Long, limit: Int): List<KernelEvent> {
        require(after >= 0 && limit in 1..MAX_EVENTS) { "invalid event range" }
        connection.prepareStatement("SELECT sequence, event_id, payload FROM taskctl_kernel_events WHERE ledger_key = ? AND sequence > ? ORDER BY sequence LIMIT ?").use { sql ->
            sql.queryTimeout = 10; sql.setString(1, ledgerKey); sql.setLong(2, after); sql.setInt(3, limit)
            sql.executeQuery().use { rows ->
                val events = mutableListOf<KernelEvent>()
                while (rows.next()) {
                    val event = KernelEvent.decode(KernelCodec.parse(rows.getString(3)))
                    require(event.sequence == rows.getLong(1) && event.id.value == rows.getString(2)) { "stored event identity mismatch" }
                    events += event
                }
                return events
            }
        }
    }
    fun events(after: Long = 0, limit: Int = 1): List<KernelEvent> = dataSource.connection.use { connection ->
        connection.isReadOnly = true; events(connection, after, limit)
    }
    /** Explicit full bounded audit; replay shares the lifecycle reducer too. */
    fun audit(): List<KernelEvent> = dataSource.connection.use { connection ->
        connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        connection.isReadOnly = true; connection.autoCommit = false
        try {
            val current = row(connection); val events = events(connection, 0, MAX_EVENTS)
            var snapshot = current.origin
            var parent: KernelEventId? = null
            events.forEachIndexed { index, event ->
                require(event.sequence == index.toLong() + 1 && event.parent == parent && event.before == snapshot.revision) { "event chain disagrees" }
                require(event.result.changed == changed(event.transition, snapshot)) { "event changed identities disagree" }
                val next = LedgerTransitions.evolve(snapshot, event.transition)
                snapshot = next.copy(revision = KernelCodec.revision(next))
                require(snapshot.revision == event.result.revision) { "event result disagrees with shared reducer" }
                parent = event.id
            }
            require(current.sequence == events.size.toLong() && current.head == parent && snapshot == current.snapshot) { "snapshot and event history disagree" }
            connection.commit(); events
        } catch (failure: Exception) { connection.rollback(); throw failure }
    }
}
