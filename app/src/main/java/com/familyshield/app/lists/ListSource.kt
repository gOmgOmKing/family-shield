package com.familyshield.app.lists

enum class ListKind { BLOCK, ALLOW }
enum class NetworkRule { ANY, UNMETERED_ONLY }

data class ListSource(
    val id: String,
    val label: String,
    val url: String,
    val kind: ListKind,
    val network: NetworkRule,
    val maxBytes: Long,
    val minEntries: Int,
)
