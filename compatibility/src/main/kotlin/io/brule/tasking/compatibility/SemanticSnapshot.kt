package io.brule.tasking.compatibility

import io.brule.tasking.core.*
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.task.TaskLedgerSnapshot
import io.brule.workflow.task.TaskDocument

object FantastiktAdapter {
    const val ID = "fantastikt-loom-agent-2026"
    const val VERSION = "0.1.0-dev.1"
    const val SOURCE_REVISION = "b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4"

    fun contract(task: TaskDocument): String = Canonical.digest("tasking/$ID/contract-preview/1", obj(
        "id" to StringValue(task.ref.value), "title" to SemanticMarkdown.value(task.title),
        "depends" to strings(task.depends.map { it.value }.sorted()), "roadmap" to StringValue(task.roadmap.value),
        "realizes" to optionalString(task.realizes?.value),
        "description" to SemanticMarkdown.value(task.markdown.section("Description")?.content.orEmpty()),
        "requirements" to SemanticMarkdown.value(task.markdown.section("Requirements")?.content.orEmpty()),
        "acceptance" to SemanticMarkdown.value(task.markdown.section("Deliverables")?.content.orEmpty(), acceptance = true),
    ))

    fun snapshot(layout: RepositoryLayout, ledger: TaskLedgerSnapshot): ObjectValue {
        val tasks = ledger.tasks.sortedBy { it.ref }.map { task -> obj(
            "id" to StringValue(task.ref.value), "state" to StringValue(task.state.name.lowercase()), "path" to StringValue(layout.display(task.path)),
            "roadmap" to StringValue(task.roadmap.value), "requires" to strings(task.depends.map { it.value }),
            "realizes" to optionalString(task.realizes?.value), "effort" to StringValue(task.effort.name), "impact" to StringValue(task.impact.name),
            "contract_digest" to StringValue(contract(task)),
        ) }
        val values = obj(
            "adapter" to StringValue(ID), "adapter_version" to StringValue(VERSION), "native_protocol" to NullValue,
            "tasks" to ArrayValue(tasks),
            "roadmaps" to ArrayValue(ledger.roadmaps.map { obj("id" to StringValue(it.ref.value), "state" to StringValue(it.state.name.lowercase()), "ordinal" to integer(it.ordinal), "epic" to optionalString(it.epic?.value)) }),
            "epics" to ArrayValue(ledger.epics.map { obj("id" to StringValue(it.ref.value), "state" to StringValue(it.state.name.lowercase())) }),
            "frontier" to strings(ledger.graph.frontier.map { it.ref.value }),
            "layers" to ArrayValue(ledger.graph.layers.map { layer -> strings(layer.map { it.ref.value }) }),
        )
        return ObjectValue(values.fields + ("semantic_snapshot_id" to StringValue(Canonical.digest("tasking/$ID/snapshot/1", values))))
    }
}
