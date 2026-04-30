package io.hrns_now.core

data class Projection<T>(
    val data: T?,
    val meta: ProjectionMeta
)
