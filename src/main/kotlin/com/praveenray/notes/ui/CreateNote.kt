package com.praveenray.notes.ui

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextArea
import com.jfoenix.controls.JFXTextField
import com.praveenray.notes.models.AppEventBus
import com.praveenray.notes.models.ChangeScene
import com.praveenray.notes.models.Note
import com.praveenray.notes.models.NoteAttachment
import com.praveenray.notes.models.SearchParams
import com.praveenray.notes.service.LuceneSearch
import com.praveenray.notes.service.NoteDB
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.BorderPane
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.util.Callback
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class CreateNote @Inject constructor(
    private val lucene: LuceneSearch,
    private val fxUtils: FXUtils,
    private val noteDB: NoteDB,
    private val uiUtils: UIUtils,
    private val eventBus: AppEventBus,
    @Named("app.max_attachments_count") private val maxAttachmentsCount: Int = 5,
) : ControllerBase() {
    private val logger = LoggerFactory.getLogger(CreateNote::class.java)
    @FXML lateinit var root: BorderPane
    @FXML lateinit var header: Label
    @FXML lateinit var descriptionText: JFXTextArea
    @FXML lateinit var addNewAttachmentBtn: JFXButton
    @FXML lateinit var attachmentsTable: ListView<Attachment>
    @FXML lateinit var tagsLabel: Label
    @FXML lateinit var tagsDropdown: TagsDropdown
    @FXML lateinit var newTagsLabel: Label
    @FXML lateinit var newTags: JFXTextField
    @FXML lateinit var saveNoteButton: JFXButton
    @FXML lateinit var backToSearchButton: JFXButton
    
    private val attachmentList: ObservableList<Attachment> = FXCollections.observableArrayList()
    private var noteID: String = ""
    private var searchParams: SearchParams? = null
    private var noteToEdit: Note? = null
    
    @FXML
    fun initialize() {
        attachmentList.clear()
        attachmentsTable.items = attachmentList
        attachmentsTable.cellFactory = Callback { _ ->
            AttachmentTableCell(fxUtils) { index, note, isDeleted ->
                logger.info("Got back new note: $note for index $index. Deleted? $isDeleted")
                if (isDeleted) {
                    if (!attachmentList.remove(attachmentList[index])) {
                        logger.warn("Note at Index $index not present in the Notes list so it wasn't removed. This should not be possible!")
                    }
                } else {
                    attachmentList[index] = attachmentList[index].copy(note = note ?: "")
                }
            }
        }
        tagsDropdown.initialize(lucene)
    }
    
    override fun initData(params: Any?) {
        if (params is Pair<*,*>) {
            searchParams = params.first as SearchParams?
            noteToEdit = params.second as Note?
            setHeaderText()
            descriptionText.text = noteToEdit?.description ?: ""
            tagsDropdown.setSelections(noteToEdit?.tags ?: emptyList())
            attachmentList.setAll((noteToEdit?.attachments ?: emptyList()).map {
                Attachment(
                    file = it.filePath ?: Paths.get("/"),
                    note = it.note ?: ""
                )
            })
        }
    }
    
    private fun setHeaderText() {
        header.text = if (noteToEdit == null) "Create New Note" else "Update Note"
    }

    fun backToSearch(event: ActionEvent) {
        eventBus.post(ChangeScene(SCENES.SEARCH, event, searchParams))
    }
    
    fun saveNote(event: ActionEvent) {
        val newTagsList = newTags.text.trim().split(",").map(String::trim)
        val tags = tagsDropdown.getSelection().plus(newTagsList).toSet().toList()
        val desc = descriptionText.text.trim()
        if (desc.isNullOrBlank()) {
            uiUtils.showError("Text must be provided", root)
        } else {
            val noteAttachments = attachmentList.map { attachment ->
                NoteAttachment(
                    filePath = attachment.file.toAbsolutePath(),
                    note = attachment.note
                )
            }
            val note = Note(
                description = desc,
                tags = tags,
                attachments = noteAttachments,
            )
            if (noteToEdit == null) {
                val noteToSave = note.copy(id = Note.createID())
                lucene.writeNoteToIndex(noteToSave, false)
                noteToEdit = noteToSave
            } else {
                val noteToSave = note.copy(id = noteToEdit?.id)
                lucene.writeNoteToIndex(noteToSave, true)
                saveNoteButton.text = "Update"
                noteToEdit = noteToSave
            }
            setHeaderText()
            uiUtils.showSuccess("Note Saved", root)
        }
    }
    
    fun addNewAttachment(event: ActionEvent) {
        val stage = fxUtils.stageFromEvent(event)
        val fileChooser = FileChooser()
        fileChooser.title = "Pick Files to Attach (max 5)"
        val selections = fileChooser.showOpenMultipleDialog(stage)
        if (selections != null) {
            if (selections.size > maxAttachmentsCount) {
                uiUtils.showError("Only ($maxAttachmentsCount) attachments allowed", root)
            } else {
                val attachments = selections.map { file ->
                    Attachment(file.toPath())
                }
    
                val uniques = findUniqueAttachments(attachmentList.toList(), attachments)
    
                if (uniques.isNotEmpty()) {
                    attachmentList.setAll(uniques)
                }
            }
        }
    }
    
    private fun findUniqueAttachments(existing: List<Attachment>, attachments: List<Attachment>): List<Attachment> {
        val setOfExisting = existing.toSet()
        val newAttachments = attachments.filter { !setOfExisting.contains(it) }
        return existing.plus(newAttachments)
    }
    
}

@Singleton
class AttachmentNoteDialog(val fxUtils: FXUtils, var savedNote: String? = null): ControllerBase() {
    private val logger = LoggerFactory.getLogger(AttachmentNoteDialog::class.java)
    @FXML lateinit var noteText: JFXTextField
    @FXML lateinit var filePath: Label
    
    private var updated: Boolean = false
    private var deleted: Boolean = false
    
    @FXML
    fun initialize() {
    
    }
    
    fun deleteAttachment(event: ActionEvent) {
        logger.warn("Deleting this Note")
        savedNote = null
        deleted = true
        close(event)
    }
    
    fun isUpdated() = updated
    fun isDeleted() = deleted
    
    fun updateNote(event: ActionEvent) {
        savedNote = noteText.text.trim()
        updated = true
        close(event)
    }
    
    private fun close(event: ActionEvent) = fxUtils.stageFromEvent(event).close()
    
    override fun initData(params: Any?) {
        val attachment = params as Attachment
        Platform.runLater {
            noteText.text = attachment.note
            filePath.text = attachment.file.toAbsolutePath().toString()
        }
    }
}

class AttachmentTableCell(private val fxUtils: FXUtils, private val cellChangeCallback: ((index: Int, newNote: String?, isDeleted: Boolean) -> Unit)): ListCell<Attachment>() {
    private val logger = LoggerFactory.getLogger(AttachmentTableCell::class.java)
    
    override fun updateItem(attachment: Attachment?, empty: Boolean) {
        super.updateItem(attachment, empty)
        if (attachment == null || empty) {
            text = null
            graphic = null
        } else {
            val link = Hyperlink(attachment.file.fileName.toString())
            link.onAction = EventHandler(this::linkClicked)
            graphic = link
            text = if (!attachment.note.isNullOrBlank()) "(${attachment.note})" else null
        }
    }
    
    fun linkClicked(event: ActionEvent) {
        if (item != null) {
            val primaryStage = fxUtils.stageFromEvent(event)
            val stage = Stage()
            stage.initModality(Modality.WINDOW_MODAL)
            stage.initOwner(primaryStage)
            stage.title = "Update Note"
            val controller = AttachmentNoteDialog(fxUtils)
            fxUtils.createNewScene(SCENES.ATTACHMENT_NOTE, stage, item, controller)
            stage.isResizable = false
            fxUtils.centerToParent(primaryStage, stage)
            stage.showAndWait()
            if (controller.isUpdated()) {
                logger.info("Updated Note: ${controller.savedNote}, isUpdated: ${controller.isUpdated()}")
                cellChangeCallback.invoke(this.index, controller.savedNote, false)
            } else if (controller.isDeleted()) {
                logger.info("User Deleted note for index: ${this.index}")
                cellChangeCallback.invoke(this.index, null, true)
            }
        }
    }
}

data class Attachment(
    val file: Path,
    val note: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Attachment
        
        if (!file.equals(other.file)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        return file.hashCode()
    }
}

