package com.praveenray.notes.ui

enum class SCENES(val fxml: String) {
    LAYOUT("application-layout"),
    SEARCH("search"),
    SEARCH_RESULTS("search-results"),
    CREATE_NOTE("create-note"),
    ATTACHMENT_NOTE("attachment-note"),
    CREATE_NEW_NOTE("create-note");
    
    override fun toString() = "/scenes/${this.fxml}.fxml"
}