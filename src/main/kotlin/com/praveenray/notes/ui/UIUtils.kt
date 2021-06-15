package com.praveenray.notes.ui

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXSnackbar
import com.jfoenix.controls.JFXSnackbar.SnackbarEvent
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.util.Duration
import javax.inject.Singleton

typealias CLOSE_CALLBACK = (root: Pane) -> Unit

@Singleton
class UIUtils {
    fun isDoubleClick(event: MouseEvent) = event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2

    fun showError(msg: String, root: Pane, pause: Boolean = false, closeCallback: CLOSE_CALLBACK = {})= showSnack(msg, root, pause,"button-warn", closeCallback)
    fun showSuccess(msg: String, root: Pane, pause: Boolean = false, closeCallback: CLOSE_CALLBACK = {}) = showSnack(msg, root, pause,"button-success", closeCallback)

    private fun showSnack(msg: String, root: Pane, pause: Boolean, cssClass: String, closeCallback: CLOSE_CALLBACK): JFXSnackbar {
        val bar = JFXSnackbar(root)
        bar.visibleProperty().addListener {_, _, isVisible ->
            if (!isVisible) {
                closeCallback.invoke(root)
            }
        }
        val jfxButton = JFXButton(msg)
        jfxButton.styleClass.add(cssClass)
        jfxButton.minWidth = 300.0
        val event = if (pause) SnackbarEvent(jfxButton, Duration.INDEFINITE) else SnackbarEvent(jfxButton, Duration.seconds(3.0))
        bar.enqueue(event)
        return bar
    }

    fun confirm(msg: String, block: () -> Unit) {
        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.headerText = msg
        val response = alert.showAndWait()
        if (response.get() == ButtonType.OK) {
            block()
        }
    }
}