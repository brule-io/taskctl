package io.brule.workflow.document

import io.brule.workflow.diagnostics.Diagnostic

public class TypedFields(
    private val document: FrontMatterDocument,
    private val diagnostics: MutableList<Diagnostic>,
) {
    public fun requiredScalar(name: String): String? {
        val value = document.fields[name]
        return when (value) {
            null -> {
                diagnostics += Diagnostic("DOC_FIELD_REQUIRED", "missing required field '$name'", document.path, 1)
                null
            }

            is FrontMatterValue.Scalar -> {
                value.value
            }

            is FrontMatterValue.Sequence -> {
                diagnostics += Diagnostic("DOC_FIELD_TYPE", "field '$name' must be a scalar", document.path, 1)
                null
            }
        }
    }

    public fun optionalScalar(name: String): String? {
        val value = document.fields[name] ?: return null
        return when (value) {
            is FrontMatterValue.Scalar -> {
                value.value
            }

            is FrontMatterValue.Sequence -> {
                diagnostics += Diagnostic("DOC_FIELD_TYPE", "field '$name' must be a scalar", document.path, 1)
                null
            }
        }
    }

    public fun sequence(name: String): List<String> {
        val value = document.fields[name] ?: return emptyList()
        return when (value) {
            is FrontMatterValue.Sequence -> {
                value.values
            }

            is FrontMatterValue.Scalar -> {
                diagnostics += Diagnostic("DOC_FIELD_TYPE", "field '$name' must be a sequence", document.path, 1)
                emptyList()
            }
        }
    }

    public fun rejectUnknown(allowed: Set<String>) {
        (document.fields.keys - allowed).sorted().forEach { name ->
            diagnostics += Diagnostic("DOC_FIELD_UNKNOWN", "unknown front-matter field '$name'", document.path, 1)
        }
    }

    public fun requireOrder(
        expected: List<String>,
        optional: Set<String> = emptySet(),
    ) {
        val actual = document.fields.keys.toList()
        val canonical = expected.filter { field -> field !in optional || field in document.fields }
        if (actual != canonical) {
            diagnostics +=
                Diagnostic(
                    "DOC_FIELD_ORDER",
                    "front-matter keys must be ordered: ${canonical.joinToString()}",
                    document.path,
                    1,
                )
        }
    }
}
