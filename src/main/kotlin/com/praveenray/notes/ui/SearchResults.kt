package com.praveenray.notes.ui

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTextArea
import com.praveenray.notes.lib.Failure
import com.praveenray.notes.lib.Success
import com.praveenray.notes.lib.SystemUtils
import com.praveenray.notes.models.AppEventBus
import com.praveenray.notes.models.ChangeScene
import com.praveenray.notes.models.Note
import com.praveenray.notes.models.NoteAttachment
import com.praveenray.notes.models.SearchParams
import com.praveenray.notes.service.LuceneSearch
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ToggleButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.web.WebView
import javafx.stage.FileChooser
import javafx.stage.Window
import javafx.util.Callback
import org.apache.lucene.index.IndexNotFoundException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchResults @Inject constructor(
    private val fxUtils: FXUtils,
    private val lucene: LuceneSearch,
    private val systemUtils: SystemUtils,
    private val eventBus: AppEventBus,
    private val uiUtils: UIUtils,

) : ControllerBase() {
    
    private val logger = LoggerFactory.getLogger(SearchResults::class.java)
    @FXML lateinit var root: BorderPane
    @FXML lateinit var countLabel: Label
    @FXML lateinit var descText: JFXTextArea
    @FXML lateinit var descTextHtml: WebView
    @FXML lateinit var textHtmlToggle: ToggleButton
    @FXML lateinit var noteIDLabel: Label
    @FXML lateinit var noteID: Label
    @FXML lateinit var noteTagLabel: Label
    @FXML lateinit var noteTags: Label
    @FXML lateinit var attachmentLabel: Label
    @FXML lateinit var attachmentListView: JFXListView<NoteAttachment>
    @FXML lateinit var previousButton: JFXButton
    @FXML lateinit var nextButton: JFXButton
    @FXML lateinit var backToSearchBtn: JFXButton
    @FXML lateinit var updateButton: JFXButton
    @FXML lateinit var deleteButton: JFXButton
    @FXML lateinit var attachmentPane: GridPane
    
    private var searchParams: SearchParams = SearchParams()
    private var searchResults: List<Note> = emptyList()
    private var currentNoteIndex: Int = 0
    private val attachmentList: ObservableList<NoteAttachment> = FXCollections.observableArrayList()
    
    @FXML
    fun initialize() {
        attachmentListView.cellFactory = Callback {
            LinkAttachmentCell(systemUtils, root.scene.window)
        }
    
        attachmentListView.items = attachmentList
        attachmentListView.prefWidthProperty().bind(attachmentPane.widthProperty())
    }
    
    override fun initData(params: Any?) {
        searchParams = params as SearchParams
        val note = when(val searched = lucene.searchForDescription(params.description, params.phraseSearch, searchParams.tags)) {
            is Success -> {
                searchResults = searched.value
                countLabel.text = "Found ${searchResults.size} Notes"
                if (searchResults.isNotEmpty()) {
                    currentNoteIndex = 0
                    searchResults[currentNoteIndex]
                } else {
                    currentNoteIndex = -1
                    listOf(updateButton, nextButton, deleteButton, previousButton).forEach {it.isDisable = true}
                    null
                }
            }
            is Failure -> {
                countLabel.text = "Found 0 Notes"
                if (searched.e !is IndexNotFoundException) {
                    uiUtils.showError(searched.e.message ?: "Error with Search", root)
                }
                null
            }
        }
        renderNote(note)
    }
    
    private fun renderNote(note: Note?) {
        logger.info("Note Description: ${note?.description}")
        descText.text = note?.description
        descTextHtml.engine.loadContent(note?.description ?: "")
        noteTags.text = note?.tags?.joinToString(",")
        noteID.text = note?.id
        val attachments = note?.attachments?.map { attachment ->
            val file = attachment.filePath?.fileName.toString()
            if (attachment.note.isNullOrBlank()) file else "$file(${attachment.note})"
        }
        attachmentList.setAll(note?.attachments ?: emptyList())
    }
    
    fun onPrevious(event: ActionEvent) {
        logger.info("onPrev: CURRENT INDEX: $currentNoteIndex")
        val index = currentNoteIndex - 1
        if (index >= 0) {
            renderNote(searchResults[index])
            nextButton.isDisable = false
            updateButton.isDisable = false
        } else {
            descText.text = "End Reached"
            previousButton.isDisable = true
            updateButton.isDisable = true
        }
        currentNoteIndex = index
    }
    
    fun onNext(event: ActionEvent) {
        logger.info("CURRENT INDEX: $currentNoteIndex")
        val index = currentNoteIndex + 1
        if (index < searchResults.size) {
            renderNote(searchResults[index])
            previousButton.isDisable = false
            updateButton.isDisable = false
        } else {
            descText.text= "End Reached"
            noteID.text = null
            noteTags.text = null
            attachmentList.clear()
            nextButton.isDisable = true
            updateButton.isDisable = true
            logger.info("CURRENT INDEX: $currentNoteIndex")
        }
        currentNoteIndex = index
    }
    
    fun backToSearch(event: ActionEvent) {
        eventBus.post(ChangeScene(SCENES.SEARCH, event, searchParams))
    }
    
    fun onUpdateNote(event: ActionEvent) {
        eventBus.post(ChangeScene(SCENES.CREATE_NEW_NOTE, event, Pair(searchParams, searchResults[currentNoteIndex])))
    }
    
    fun showAsHtml(event: ActionEvent) {
        val html = textHtmlToggle.isSelected
        descTextHtml.isVisible = html
        descTextHtml.isManaged = html

        descText.isVisible = !html
        descText.isManaged = !html
    }

    fun onDeleteNote(event: ActionEvent) {
        uiUtils.confirm("Delete Note?") {
            lucene.deleteNote(searchResults[currentNoteIndex])
            eventBus.post(ChangeScene(SCENES.SEARCH, event, searchParams))
        }
    }
}

class LinkAttachmentCell(
    val systemUtils: SystemUtils,
    val window: Window
): ListCell<NoteAttachment>() {
    override fun updateItem(item: NoteAttachment?, empty: Boolean) {
        super.updateItem(item, empty)
        graphic = null
        text = null
        
        if (!empty && item != null && item.filePath != null) {
            val filename = item.filePath.fileName.toString()
            val linkText = if (item.note.isNullOrBlank()) {
                filename
            } else {
                "$filename (${item.note})"
            }
            val link = Hyperlink(linkText)
            link.onAction = EventHandler { e ->
                
                val fileChooser = FileChooser()
                fileChooser.title = "Save Attachment"
                fileChooser.initialFileName = filename
                fileChooser.initialDirectory = systemUtils.currentDir().toFile()
                val saveFile = fileChooser.showSaveDialog(window)
                if (saveFile != null) {
                    Files.copy(item.filePath, saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            graphic = link
        }
    }
}