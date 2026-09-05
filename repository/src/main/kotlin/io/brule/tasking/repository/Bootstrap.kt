package io.brule.tasking.repository

import io.brule.tasking.core.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

data class InitializationPlan(val root: Path, val repositoryId: String, val files: Map<String, String>) {
    val digest: String get() = Canonical.digest("taskctl.init-plan/alpha1", obj("repository_id" to StringValue(repositoryId), "files" to stringMap(files)))
    fun result(): ObjectValue = obj("contract" to StringValue(Bootstrap.CONTRACT), "repository_id" to StringValue(repositoryId),
        "plan_digest" to StringValue(digest), "files" to strings(files.keys.sorted()))
}

/** Generator composition boundary: explicit seed inputs, one authoritative set
 * of templates shipped with the distribution, no Git or network operations. */
object Bootstrap {
    const val CONTRACT = "taskctl.init/alpha1"
    private const val JOURNAL = ".taskctl/bootstrap-journal.json"

    fun plan(repository: Path, repositoryId: String, version: String, distribution: Path, lock: String,
             seed: Transition.AddRecords = Transition.AddRecords()): InitializationPlan {
        val root = NativeFiles.repositoryRoot(repository)
        require(repositoryId.isNotBlank()) { "repository identity is required" }
        if (Files.exists(root)) {
            require(Files.isDirectory(root) && !Files.isSymbolicLink(root)) { "destination must be a real directory" }
            Files.list(root).use { children ->
                require(children.allMatch { it.fileName.toString() == ".git" }) { "init requires an empty directory (an existing .git is allowed); existing-code adoption is a separate operation" }
            }
        }
        validateLock(lock, version)
        val universe = LedgerTransitions.reduce(LedgerSnapshot(repositoryId, Revision.initial(), DraftUniverse(emptyList())), seed)
        val files = linkedMapOf(
            ".taskctl/toolchain.lock" to lock.replace("\r\n", "\n"),
            ".taskctl/.gitignore" to "bootstrap-journal.json\n",
            ".agents/config.toml" to "contract = \"taskctl.repository/alpha1\"\nprotocol = \"taskctl.native/alpha1\"\nrepository_id = ${Json.encode(StringValue(repositoryId))}\nprofile = \"minimal/alpha1\"\n",
            ".agents/policy.toml" to "contract = \"taskctl.policy/alpha1\"\nprofile = \"minimal/alpha1\"\n",
            ".agents/.gitignore" to "runtime/\n.taskctl-*.tmp\n",
            "AGENTS.md" to agentInstructions(),
        )
        for (name in listOf("taskctl", "taskctl.ps1", "taskctl.bat")) files[name] = Files.readString(distribution.resolve("bootstrap/$name"))
        universe.tasks.forEach { files[".agents/tasks/" + NativeFiles.fileName(it.id.value)] = Json.encode(NativeCodec.task(it)) + "\n" }
        universe.roadmaps.forEach { files[".agents/roadmaps/" + NativeFiles.fileName(it.id.value)] = Json.encode(PlanningRecordCodec.encode(it)) + "\n" }
        universe.epics.forEach { files[".agents/epics/" + NativeFiles.fileName(it.id.value)] = Json.encode(PlanningRecordCodec.encode(it)) + "\n" }
        files.keys.forEach { NativeFiles.locate(root, it) }
        return InitializationPlan(root, repositoryId, files)
    }

    fun apply(plan: InitializationPlan): ObjectValue {
        require(plan.files.keys.none { Files.exists(NativeFiles.locate(plan.root, it)) }) { "initialization destination changed; replan" }
        Files.createDirectories(plan.root)
        val journal = NativeFiles.locate(plan.root, JOURNAL)
        Files.createDirectories(journal.parent)
        val value = obj("contract" to StringValue(CONTRACT), "repository_id" to StringValue(plan.repositoryId),
            "plan_digest" to StringValue(plan.digest), "files" to stringMap(plan.files))
        // CREATE_NEW reserves the bootstrap operation; retries use recover.
        Files.writeString(journal, Json.encode(value), java.nio.file.StandardOpenOption.CREATE_NEW)
        finish(plan.root, value)
        return plan.result()
    }

    fun recover(repository: Path): Boolean {
        val root = NativeFiles.repositoryRoot(repository)
        val path = NativeFiles.locate(root, JOURNAL)
        if (!Files.exists(path)) return false
        finish(root, NativeFiles.objectValue(Files.readString(path)))
        return true
    }

    private fun finish(root: Path, journal: ObjectValue) {
        require(journal.fields.keys == setOf("contract", "repository_id", "plan_digest", "files") && journal.requiredString("contract") == CONTRACT) { "invalid bootstrap journal" }
        val files = (journal.fields["files"] as? ObjectValue ?: error("invalid bootstrap files")).fields.mapValues {
            (it.value as? StringValue)?.value ?: error("invalid bootstrap content")
        }
        val plan = InitializationPlan(root, journal.requiredString("repository_id"), files)
        require(plan.digest == journal.requiredString("plan_digest")) { "bootstrap journal digest mismatch" }
        val fixed = setOf(".taskctl/toolchain.lock", ".taskctl/.gitignore", ".agents/config.toml", ".agents/policy.toml", ".agents/.gitignore", "AGENTS.md", "taskctl", "taskctl.ps1", "taskctl.bat")
        for ((relative, content) in files) {
            require(relative in fixed || Regex("^\\.agents/(tasks|roadmaps|epics)/[A-Za-z0-9._-]+\\.yaml$").matches(relative)) { "bootstrap path outside declared scope" }
            val path = NativeFiles.locate(root, relative)
            require(!Files.exists(path) || Files.readString(path) == content) { "external change conflicts with bootstrap: $relative" }
        }
        files.forEach { (path, content) -> NativeFiles.atomicWrite(root, path, content) }
        // Empty directories are optional in a checkout and have no graph meaning.
        for (name in listOf("tasks", "roadmaps", "epics", "receipts")) Files.createDirectories(NativeFiles.locate(root, ".agents/$name"))
        val launcher = NativeFiles.locate(root, "taskctl")
        if (Files.getFileStore(launcher).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(launcher, Files.getPosixFilePermissions(launcher) + setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE))
        }
        NativeFiles.atomicWrite(root, ".taskctl/init-receipt.json", Json.encode(plan.result()) + "\n")
        Files.delete(NativeFiles.locate(root, JOURNAL))
    }

    fun validateLock(source: String, version: String) {
        val pin = linkedMapOf<String, String>()
        val platforms = setOf("windows-x86_64", "linux-x86_64", "macos-aarch64")
        val allowed = setOf("lockFormat", "wrapperVersion", "toolVersion") + platforms.flatMap { listOf("$it.url", "$it.sha256") }
        source.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
            require('=' in line) { "malformed toolchain lock" }
            val key = line.substringBefore('='); val value = line.substringAfter('=')
            require(key in allowed && key !in pin) { "unknown or duplicate toolchain field: $key" }; pin[key] = value
        }
        require(pin["lockFormat"] == "2" && pin["wrapperVersion"] == "2" && pin["toolVersion"] == version) { "toolchain lock must select this exact tool/wrapper version" }
        val selected = platforms.filter { "$it.url" in pin || "$it.sha256" in pin }
        require(selected.isNotEmpty()) { "toolchain has no platform artifacts" }
        selected.forEach {
            val url = java.net.URI(pin.getValue("$it.url"))
            require(url.scheme in setOf("https", "file") && url.userInfo == null && url.fragment == null &&
                (url.scheme != "https" || !url.host.isNullOrBlank()) && (url.scheme != "file" || url.path.startsWith('/'))) { "unsupported artifact URL" }
            require(pin.getValue("$it.sha256").matches(Regex("[a-f0-9]{64}"))) { "invalid artifact digest" }
        }
    }

    private fun agentInstructions(): String = """
        # Repository tasking contract

        Start with `./taskctl doctor`, `./taskctl context`, and `./taskctl frontier`.
        Windows: use `./taskctl.bat` or `./taskctl.ps1`. Add `--format json` for
        machine results. The launcher targets its own repository even from another
        working directory; `--repo PATH` overrides it explicitly.

        Tasks are atomic executable transitions. Prerequisites determine readiness.
        Roadmaps are durable named lines of advance; epics are capability scopes.
        Their memberships are independent and may be empty or overlapping.
        Read `.agents/config.toml` and `.agents/policy.toml` for protocol/profile.

        Use `taskctl seed --file PLAN --expect-revision REVISION` to admit an explicit
        plan through normal validation. `taskctl show ID` reports the current task
        contract. `taskctl verify ID --receipt FILE` validates supplied evidence;
        it never runs a command or promotes an assertion into an independent proof.
        `taskctl close ID --receipt FILE --expect-revision REVISION` binds closure
        to that contract and the inspected ledger revision. Receipts are immutable.

        Read commands do not write the project. Writes are bounded to tasking state.
        No command implicitly commits, stages, merges, deploys, or contacts services.
        Unknown required capabilities block execution. Never infer dependencies from
        roadmap order or claim epic completion just because its tasks are closed.
        Use `taskctl recover` for an interrupted tasking transaction; inspect any
        reported external-edit conflict instead of deleting recovery data.

        The `.taskctl/toolchain.lock` pins the exact release and platform digest.
        Commit the generated launchers and state (preserve the POSIX executable bit).
        Set `TASKCTL_OFFLINE=1` to require cached operation. Private GitHub release
        acquisition accepts `TASKCTL_GITHUB_TOKEN` or `GH_TOKEN` with contents read.
        Native alpha contracts are not v1; upgrades never silently rewrite records.
    """.trimIndent() + "\n"
}
