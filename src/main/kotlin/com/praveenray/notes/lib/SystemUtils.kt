package com.praveenray.notes.lib

import java.nio.file.Paths
import java.util.*
import javax.inject.Singleton

@Singleton
class SystemUtils {
    fun currentDir() = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    fun tempDir() = currentDir().resolve(UUID.randomUUID().toString())
}