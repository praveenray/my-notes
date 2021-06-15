package com.praveenray.notes.ui

import com.google.inject.Injector
import javafx.event.ActionEvent
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.util.EventObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FXUtils  @Inject constructor(val injector: Injector) {

    private val logger = LoggerFactory.getLogger(FXUtils::class.java)

    fun createNewScene(fxml: SCENES, stage: Stage, data: Any? = null, controller: ControllerBase? = null): Pair<Parent, Any?> {
        val (fxmlParent, myController) = loadFXML(fxml.toString(), data, controller)
        stage.sizeToScene()
        val scene = Scene(fxmlParent)
        stage.scene = scene
        return Pair(fxmlParent, myController)
    }

    fun createNewScene(fxml: SCENES, event: ActionEvent, data: Any? = null): Pair<Parent, Any?> {
        val stage = stageFromEvent(event)
        return createNewScene(fxml, stage, data)
    }

    fun stageFromEvent(event: EventObject): Stage {
        val source = event.source
        val stage = (if (source is Node) {
            val stage = source.scene.window
            if (stage is Stage) {
                stage
            } else null
        } else null) ?: throw IllegalStateException("Cannot get Stage from Event")
        return stage
    }

    fun loadFXML(scene: SCENES, controllerParams: Any? = null, controller: ControllerBase? = null, root: Node? = null): Pair<Parent, Any?> {
        return loadFXML(scene.toString(), controllerParams, controller, root)
    }

    fun loadFXML(classpath: String, controllerParams: Any? = null, controller: ControllerBase? = null, root: Node? = null): Pair<Parent, Any?> {
        val javaClass = this.javaClass
        return javaClass.getResourceAsStream(classpath).use { resource ->
            val fxLoader = FXMLLoader()
            if (controller == null) {
                fxLoader.setControllerFactory { injector.getInstance(it) }
            } else {
                fxLoader.setController(controller)
            }
            fxLoader.location = javaClass.getResource(classpath)
            logger.info("Location URL: ${fxLoader.location}")
            if (root != null) {
                fxLoader.setRoot(root)
            }
            val fx: Parent = fxLoader.load(resource)
            val myController: Any? = fxLoader.getController()
            if (myController != null) {
                if (myController is ControllerBase) {
                    myController.initData(controllerParams)
                }
            }
            Pair(fx, myController)
        }
    }

    fun centerToParent(parent: Stage, dialog: Stage) {
        val root: Pane = dialog.scene.root as Pane
        val dialogX: Double = (parent.x + parent.width / 2
                - root.getPrefWidth() / 2)
        val dialogY: Double = (parent.y + parent.height / 2
                - root.getPrefHeight() / 2)
        dialog.x = dialogX /*from  w w w .j  a  va 2s . c o m*/
        dialog.y = dialogY
    }
}