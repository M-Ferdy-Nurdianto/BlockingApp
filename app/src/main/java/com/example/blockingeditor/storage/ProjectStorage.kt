package com.example.blockingeditor.storage

import android.content.Context
import com.example.blockingeditor.model.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ProjectStorage {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun saveProject(context: Context, project: Project, filename: String = "project.json") {
        val safeFilename = if (filename.endsWith(".json")) filename else "$filename.json"
        val jsonString = json.encodeToString(project)
        context.openFileOutput(safeFilename, Context.MODE_PRIVATE).use {
            it.write(jsonString.toByteArray())
        }
    }

    fun loadProject(context: Context, filename: String = "project.json"): Project? {
        return try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) return null
            val jsonString = file.readText()
            json.decodeFromString<Project>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listProjects(context: Context): List<String> {
        return context.fileList()?.filter { it.endsWith(".json") } ?: emptyList()
    }

    fun deleteProject(context: Context, filename: String) {
        val file = File(context.filesDir, filename)
        if (file.exists()) file.delete()
    }
}
