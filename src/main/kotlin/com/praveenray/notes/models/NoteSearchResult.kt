package com.praveenray.notes.models

data class NoteSearchResult(
    val notes: List<Note>,
    val count: Int,
    val millisTaken: Long
)