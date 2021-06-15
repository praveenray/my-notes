package com.praveenray.notes

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provider
import com.google.inject.name.Names
import com.praveenray.notes.models.AppEventBus
import com.praveenray.notes.models.ChangeSceneForStage
import com.praveenray.notes.ui.FXUtils
import com.praveenray.notes.ui.SCENES
import javafx.application.Application
import javafx.stage.Stage
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Properties
import javax.inject.Inject

class ApplicationMainKot(private val params: Application.Parameters? = null) {
    fun start(primaryStage: Stage) {
        primaryStage.title = "My Notes"
        val injector = Guice.createInjector(AppConfigModule(params))
        val eventBus = injector.getInstance(AppEventBus::class.java)
        val fxUtils = injector.getInstance(FXUtils::class.java)
        fxUtils.createNewScene(SCENES.LAYOUT, primaryStage)
        primaryStage.isResizable = true;
        primaryStage.show()

        eventBus.post(ChangeSceneForStage(SCENES.SEARCH, primaryStage))
    }
}

class AppConfigModule(val params: Application.Parameters?) : AbstractModule() {
    override fun configure() {
        bind(FXUtils::class.java).toProvider(FXUtilsProvider::class.java)

        loadProperties().let { props ->
            if (props.size > 0) {
                Names.bindProperties(binder(), props)
            }
        }
    }

    private fun loadProperties(): Properties {
        val dbg = Paths.get("debug.txt")
        if (Files.exists(dbg)) Files.delete(dbg)
        Files.write(dbg, "starting\n".toByteArray(), StandardOpenOption.CREATE)

        val props = Properties()
        this.javaClass.getResourceAsStream("/application.properties").use { props.load(it) }
        if (params != null) {
            val rawParams = parseCommandLineArgs(params.raw)
            val profile = rawParams["profile"]
            val propsResource = "/application-$profile.properties"
            try {
                javaClass.getResourceAsStream(propsResource)?.use { props.load(it) }
            } catch (e: Exception) {
                println("The resource $propsResource is not found or not parseable")
            }
            rawParams.minus("profile").forEach { (key, value) -> props[key] = value }
        }
        return props
    }

    private fun parseCommandLineArgs(args: List<String>): Map<String, String> {
        return args.associate { argument ->
            val arg = argument.trim().replaceFirst(Regex("^--"), "")
            if (arg.contains("=")) {
                val tokens = arg.split("=").map { it.trim() }
                Pair(tokens[0], tokens[1])
            } else {
                Pair(arg, "true")
            }
        }
    }
}

class FXUtilsProvider : Provider<FXUtils> {
    @Inject
    lateinit var injector: Injector
    override fun get(): FXUtils {
        return FXUtils(injector)
    }
}

/*
set CLASSPATH=%CLASSPATH%;%DIRNAME%
echo %CLASSPATH%
*/