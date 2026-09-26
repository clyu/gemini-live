package io.github.clyu.geminilive.data

enum class Role { User, Model }

data class TranscriptEntry(
    val id: Long,
    val role: Role,
    val text: String,
    val interrupted: Boolean = false,
)
