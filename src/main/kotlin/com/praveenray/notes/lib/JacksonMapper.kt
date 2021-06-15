package com.praveenray.notes.lib

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Path
import javax.inject.Singleton

@Singleton
class JacksonMapper: ObjectMapper() {
    init {
        registerModule(KotlinModule())
        findAndRegisterModules()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun readTree(file: Path): JsonNode {
        return this.readTree(file.toFile()) ?: throw IllegalArgumentException("File $file is not readable as json")
    }

    fun toJsonString(obj: Any?) = if (obj == null) null else this.writeValueAsString(obj)
    fun toJsonPrettyString(obj: Any?): String? {
        return if (obj != null) {
            this.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
        } else null
    }

    fun parseToJsonNode(jsonString: String) = this.readTree(jsonString)

    fun <T> parseToObject(jsonString: String, valueType: Class<T>): T {
        return this.readValue(jsonString, valueType)
    }

    fun parseToMap(jsonString: String): Map<String, Any?> {
        return readValue(jsonString)
    }

    fun writeToFile(jsonObj: Any, file: Path) {
        this.writeValue(file.toFile(), jsonObj)
    }

    inline fun <reified T> readValue(content: String): T = readValue(content, object: TypeReference<T>(){})
    fun <T> readValue(src: Path): T = readValue(src.toFile(), object: TypeReference<T>(){})
}