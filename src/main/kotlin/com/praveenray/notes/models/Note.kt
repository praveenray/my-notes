package com.praveenray.notes.models

import com.fasterxml.jackson.annotation.JsonFormat
import java.net.URL
import java.nio.file.Path
import java.time.LocalDate
import java.util.*

data class Note(
    val id: String? = null,
    val description: String,
    val tags: List<String> = emptyList(),
    val attachments: List<NoteAttachment> = emptyList(),
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val createDate: LocalDate = LocalDate.now(),
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val modifyDate: LocalDate = LocalDate.now()
) {
    companion object {
        fun createID() = UUID.randomUUID().toString()
    }
}

data class NoteAttachment(
    val httpUrl: URL? = null,
    val filePath: Path? = null,
    val note: String? = null,
)