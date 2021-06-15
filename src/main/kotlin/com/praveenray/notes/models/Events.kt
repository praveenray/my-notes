package com.praveenray.notes.models

import com.google.common.eventbus.EventBus
import com.praveenray.notes.ui.SCENES
import javafx.stage.Stage
import java.util.EventObject
import javax.inject.Singleton

sealed class EventBase(val params: Any? = null)
class ChangeScene(scene: SCENES, event: EventObject, params: Any? = null): EventBase(Triple(scene, event, params))
class ChangeSceneForStage(scene: SCENES, stage: Stage, params: Any? = null): EventBase(Triple(scene, stage, params))
@Singleton
class AppEventBus: EventBus()