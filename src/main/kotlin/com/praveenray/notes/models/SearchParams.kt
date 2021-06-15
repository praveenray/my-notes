package com.praveenray.notes.models

data class SearchParams(
    val description: String = "",
    val phraseSearch: Boolean = true,
    val tags: List<String> = emptyList(),
)