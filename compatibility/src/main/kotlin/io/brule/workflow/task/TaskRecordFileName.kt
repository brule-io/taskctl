package io.brule.workflow.task

import io.brule.workflow.model.TaskRef

/** Canonical, lossless binding between one task identity and its ledger basename. */
internal data class TaskRecordFileName(
    val ref: TaskRef,
    val slug: String,
) {
    internal companion object {
        fun parse(fileName: String): TaskRecordFileName? {
            if (!fileName.endsWith(MARKDOWN_SUFFIX)) return null
            val stem = fileName.removeSuffix(MARKDOWN_SUFFIX)
            val separator = stem.lastIndexOf('.')
            if (separator <= 0 || separator == stem.lastIndex) return null
            val ref = TaskRef.parse(stem.substring(0, separator)) ?: return null
            val slug = stem.substring(separator + 1)
            return if (SLUG.matches(slug)) TaskRecordFileName(ref, slug) else null
        }

        private const val MARKDOWN_SUFFIX: String = ".md"
        private val SLUG: Regex = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}
