package com.praveenray.notes.ui

import com.jfoenix.controls.JFXButton
import com.praveenray.notes.lib.Failure
import com.praveenray.notes.lib.Success
import com.praveenray.notes.models.AppEventBus
import com.praveenray.notes.models.ChangeScene
import com.praveenray.notes.models.SearchParams
import com.praveenray.notes.service.LuceneSearch
import com.praveenray.notes.service.NoteDB
import javafx.application.Platform
import javafx.concurrent.Service
import javafx.concurrent.Task
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.text.Text
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import org.slf4j.LoggerFactory
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchController @Inject constructor(
    private val fxUtils: FXUtils,
    private val lucene: LuceneSearch,
    private val noteDB: NoteDB,
    private val uiUtils: UIUtils,
    private val eventBus: AppEventBus,
) : ControllerBase() {
    private val logger = LoggerFactory.getLogger(SearchController::class.java)
    
    @FXML lateinit var panel: BorderPane
    @FXML lateinit var header: Text
    @FXML lateinit var descriptionBox: TextField
    @FXML lateinit var exportBtn: JFXButton
    @FXML lateinit var newNoteBtn: JFXButton
    @FXML lateinit var searchNowButton: JFXButton
    @FXML lateinit var tagsLabel: Label
    @FXML lateinit var tagsCombo: TagsDropdown
    
    @FXML
    fun initialize() {
    }
    
    override fun initData(searchParams: Any?) {
        val params = if (searchParams is SearchParams) {
            searchParams
        } else SearchParams()
        descriptionBox.text = params.description
        Platform.runLater {
            tagsCombo.initialize(lucene)
            tagsCombo.setSelections(params.tags)
        }
    }
    
    fun exportToFile(event: ActionEvent) {
        val dirChooser = DirectoryChooser()
        dirChooser.title = "Pick Export Directory"
        val dirFile = dirChooser.showDialog(fxUtils.stageFromEvent(event))
        if (dirFile != null) {
            val dir = dirFile.toPath()
            val dlg = ProgressDialog(fxUtils, lucene, noteDB, "Exporting")
            val error = dlg.start(dir)
            val exportError = if (error.isNullOrBlank()) {
                if (dlg.canceled()) {
                    noteDB.cleanExportFolder(dir)
                    "Export canceled"
                } else error
            } else error
            if (!exportError.isNullOrBlank()) {
                uiUtils.showError(exportError, panel)
            } else {
                uiUtils.showSuccess("successfully exported to $dirFile", panel)
            }
        }
    }
    
    fun createNewNote(event: ActionEvent) {
        eventBus.post(ChangeScene(scene = SCENES.CREATE_NEW_NOTE, event = event, params = createSearchParams()))
    }

    fun searchNow(event: ActionEvent) {
        eventBus.post(ChangeScene(scene = SCENES.SEARCH_RESULTS, event = event, params = createSearchParams()))
    }
    
    private fun createSearchParams() = SearchParams(
        tags = tagsCombo.getSelection(),
        description = descriptionBox.text.trim(),
    )
}

class ProgressDialog(
    val fxUtils: FXUtils,
    val lucene: LuceneSearch,
    val noteDB: NoteDB,
    val labelText: String = ""
): ControllerBase() {
    @FXML lateinit var progressBar: ProgressBar
    @FXML lateinit var label: Label
    @FXML lateinit var root: Pane
    
    private lateinit var dlg: Dialog<String?>
    private lateinit var service: Service<String?>
    private var isCanceled: Boolean = false
    private var exportError: String? = null
    
    @FXML
    fun initialize() {
        progressBar.progress = 0.0
        label.text = labelText
    }
    
    fun canceled() = isCanceled
    
    fun start(dirFile: Path): String? {
        val (parent, _) = fxUtils.loadFXML(classpath = "/fragments/progress-dialog.fxml", controller = this)
        dlg = Dialog()
        dlg.title = "Please Wait"
        dlg.dialogPane.content = parent
        dlg.initModality(Modality.APPLICATION_MODAL)
     
        val task = object: Task<String?>() {
            override fun call(): String? {
                updateProgress(0.5, 1.0)
                Thread.sleep(2000) // todo : remove
                return when (val searched = lucene.searchForDescription(null, false)) {
                    is Success -> noteDB.exportNotes(dirFile, searched.value)
                    is Failure -> searched.e.message
                }
            }
        }
        
        progressBar.progressProperty().bind(task.progressProperty())
        service = object: Service<String?>() {
            override fun createTask(): Task<String?> {
                return task
            }
    
            override fun succeeded() {
                exportError = task.get()
                closeDialog()
            }
    
            override fun cancelled() {
                super.cancelled()
                closeDialog()
            }
    
            override fun failed() {
                exportError = task.get()
                closeDialog()
            }
        }
        
        service.start()
        dlg.showAndWait()
        return exportError
    }
    
    private fun closeDialog() {
        dlg.result = "fake" // without this, dialog won't close
        dlg.close()
    }
    
    fun cancel(event: ActionEvent) {
        isCanceled = true
        service!!.cancel()
    }
}