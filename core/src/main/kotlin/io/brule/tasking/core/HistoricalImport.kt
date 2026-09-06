package io.brule.tasking.core

data class SourceProvenance(val repository: String, val revision: String, val path: String,
                            val sha256: String, val adapter: String, val adapterVersion: String)
data class HistoricalRecord(val id: String, val state: String, val contractDigest: String,
                            val evidenceClass: String, val narrative: String, val source: SourceProvenance) {
    val nativeProtocol: String? = null
    fun manifestEntry(): ObjectValue = obj(
        "id" to StringValue(id), "state" to StringValue(state), "contract_digest" to StringValue(contractDigest),
        "evidence_class" to StringValue(evidenceClass), "native_protocol" to optionalString(nativeProtocol),
        "source_repository" to StringValue(source.repository), "source_revision" to StringValue(source.revision),
        "source_path" to StringValue(source.path), "source_sha256" to StringValue(source.sha256),
        "adapter" to StringValue(source.adapter), "adapter_version" to StringValue(source.adapterVersion),
    )
}
