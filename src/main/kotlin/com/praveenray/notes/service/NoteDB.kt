package com.praveenray.notes.service

import com.praveenray.notes.lib.JacksonMapper
import com.praveenray.notes.lib.SystemUtils
import com.praveenray.notes.models.Note
import com.praveenray.notes.models.NoteAttachment
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.streams.toList

@Singleton
class NoteDB @Inject constructor(
    val systemUtils: SystemUtils,
    val jackson: JacksonMapper,
    @Named("app.data.directory") private val dataDirectory: String,
) {
    private val logger = LoggerFactory.getLogger(NoteDB::class.java)

    val attachmentsDirName = "attachments"
    
    fun getNoteJsonFile(): Path {
        val dataDirectoryPath = Paths.get(dataDirectory)
        val dataDir = if (dataDirectoryPath.isAbsolute) dataDirectoryPath else systemUtils.currentDir().resolve(dataDirectory)
        return dataDir.resolve("notes.json")
    }
    
    fun getNotesFromDB(): List<Note> {
        val notesFile = getNoteJsonFile()
        val notes: List<Note> = if (Files.exists(notesFile)) {
            jackson.readValue(notesFile.toFile(), jackson.typeFactory.constructCollectionType(List::class.java, Note::class.java))
        } else emptyList()
        return notes
    }
    
    fun exportNotes(directory: Path, notes: List<Note>): String? {
        return if (Files.list(directory).toList().isNotEmpty()) {
            "Directory $directory must be empty"
        } else {
            val json = directory.resolve("notes.json")
            try {
                Files.write(json, jackson.writeValueAsBytes(notes), StandardOpenOption.CREATE_NEW)
                val attachmentsDir = createAttachmentsDir(directory)
                notes.forEach { writeAttachmentsForNote(it, attachmentsDir)}
                null
            } catch (e: IOException) {
                logger.warn("Error creating $json", e)
                "Error writing to $directory"
            }
        }
    }
    
    fun writeAttachmentsForNote(note: Note) {
        writeAttachmentsForNote(note, defaultAttachmentsDir())
    }
    
    fun createNoteAttachment(noteID: String, note: String? = null, attachmentName: String): NoteAttachment {
        val fullPath = defaultAttachmentsDir().resolve(noteID)
        return NoteAttachment(
            note = note,
            filePath = fullPath.resolve(attachmentName),
        )
    }
    
    fun defaultAttachmentsDir() = createAttachmentsDir(Paths.get(dataDirectory))

    private fun createAttachmentsDir(parent: Path): Path {
        val attachementsDir = parent.resolve(attachmentsDirName)
        if (!Files.exists(attachementsDir)) {
            Files.createDirectories(attachementsDir)
        }
        return attachementsDir
    }
    
    private fun writeAttachmentsForNote(note: Note, dir: Path) {
        // first copy all attachments to a temp directory
        val tempDir = systemUtils.tempDir()
        try {
            Files.createDirectories(tempDir)
            val tempAttachments = note.attachments.map { attachment ->
                val newPath = if (attachment.filePath != null) {
                    val dest = tempDir.resolve(attachment.filePath.fileName.toString())
                    Files.copy(attachment.filePath, dest, StandardCopyOption.REPLACE_EXISTING)
                    dest
                } else null
                attachment.copy(filePath = newPath)
            }
            val tempNote = note.copy(attachments = tempAttachments)
            logger.info("All attachments copied to temp folder: $tempDir for temp note $tempNote")
    
            val noteDir = dir.resolve(note.id)
            if (Files.exists(noteDir)) {
                noteDir.toFile().deleteRecursively()
            }
            Files.createDirectories(noteDir)
            tempNote.attachments.forEach { attachment ->
                if (attachment.filePath != null) {
                    val destFile = noteDir.resolve(attachment.filePath.fileName)
                    if (attachment.filePath != destFile) {
                        Files.copy(attachment.filePath, destFile, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        } finally {
            if (Files.exists(tempDir)) {
                tempDir.toFile().deleteRecursively()
            }
        }
    }
    
    fun fixIDs() {
        val notes = getNotesFromDB()
        val notesWithIds = notes.map { note ->
            note.copy(id = UUID.randomUUID().toString())
        }
        getNoteJsonFile().toFile().writeBytes(jackson.writeValueAsBytes(notesWithIds))
    }
    
    fun cleanExportFolder(dir: Path) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            Files.list(dir).forEach { it.toFile().deleteRecursively() }
        }
    }
}