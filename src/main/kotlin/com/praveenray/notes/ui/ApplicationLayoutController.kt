package com.praveenray.notes.ui

import com.google.common.eventbus.Subscribe
import com.praveenray.notes.models.AppEventBus
import com.praveenray.notes.models.ChangeScene
import com.praveenray.notes.models.ChangeSceneForStage
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.scenicview.ScenicView
import org.slf4j.LoggerFactory
import java.util.EventObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationLayoutController @Inject constructor(
    private val eventBus: AppEventBus,
    private val fxUtils: FXUtils,
) {
    @FXML lateinit var appRoot: VBox
    @FXML lateinit var container: VBox

    private val logger = LoggerFactory.getLogger(ApplicationLayoutController::class.java)

    @FXML
    fun initialize() {
        eventBus.register(this)
    }

    @Subscribe
    fun changeScene(event: ChangeScene) {
        val (scene, event, sceneParams) = event.params as Triple<SCENES, EventObject, Any?>
        setContainerContent(scene, fxUtils.stageFromEvent(event), sceneParams)
    }

    private fun setContainerContent(
        scene: SCENES,
        stage: Stage,
        sceneParams: Any?,
    ) {
        val (fxmlParent, _) = fxUtils.loadFXML(scene, sceneParams)
        container.children.setAll(fxmlParent)
        if (fxmlParent is Region) {
            logger.debug("Setting Stage Height to ${fxmlParent.prefHeight}")
            stage.height = fxmlParent.prefHeight
        }
    }

    @Subscribe
    fun changeScene(event: ChangeSceneForStage) {
        val (scene, stage, sceneParams) = event.params as Triple<SCENES, Stage, Any?>
        setContainerContent(scene, stage, sceneParams)
    }

    @FXML
    fun showDebug(event: ActionEvent) {
        ScenicView.show(appRoot);
    }
}