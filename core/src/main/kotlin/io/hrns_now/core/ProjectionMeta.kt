package io.hrns_now.core

data class ProjectionMeta(
    val source: String,
    val exists: Boolean,
    val malformed: Boolean = false,
    val stale: Boolean = false,
    val message: String? = null
)
