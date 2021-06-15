package com.praveenray.notes.ui

import com.praveenray.notes.service.LuceneSearch
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.layout.Pane
import org.controlsfx.control.CheckComboBox
import org.slf4j.LoggerFactory
import javax.inject.Singleton

@Singleton
class TagsDropdown: Pane() {
    private val logger = LoggerFactory.getLogger(TagsDropdown::class.java)
    @FXML lateinit var choices: CheckComboBox<String>
    
    init {
        val javaClass = this.javaClass
        val fx = FXMLLoader(javaClass.getResource("/scenes/tags-combo.fxml"))
        fx.setRoot(this)
        fx.setController(this)
        fx.load<Parent>()
        logger.info("Loaded tags-combo.fxml. Choice: $choices")
    }
    
    fun getSelection(): List<String> {
        return choices.checkModel.checkedItems.map(Any::toString).map(String::trim)
    }
 
    fun setSelections(tags: List<String>) {
        tags.forEach {
            choices.checkModel.check(it)
        }
    }
    
    fun initialize(lucene: LuceneSearch) {
        choices.items.setAll(lucene.uniqueTags())
    }
}