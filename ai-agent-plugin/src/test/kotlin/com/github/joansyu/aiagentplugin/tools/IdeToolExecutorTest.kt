package com.github.joansyu.aiagentplugin.tools

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path

class IdeToolExecutorTest : BasePlatformTestCase() {
    fun testReadsUnsavedDocumentAndEditsOnlyUniqueContent() {
        val executor = IdeToolExecutor(project)
        val created = executor.execute("write_file", mapOf("path" to "AgentSample.txt", "content" to "first"))
        assertFalse(created, created.startsWith("错误"))
        val file = LocalFileSystem.getInstance().findFileByNioFile(Path.of(project.basePath!!, "AgentSample.txt"))!!
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("unsaved\nsecond\nthird") }
        assertTrue(executor.execute("read_file", mapOf("path" to "AgentSample.txt")).contains("unsaved"))
        // Gson deserializes JSON numbers to Double: 2.0 must still address line 2.
        val region = executor.execute("read_file_region", mapOf("path" to "AgentSample.txt", "startLine" to 2.0, "endLine" to 2.0))
        assertTrue(region, region.contains("second"))
        assertFalse(region, region.contains("unsaved"))
        val edited = executor.execute("edit_file", mapOf("path" to "AgentSample.txt", "old_string" to "second", "new_string" to "changed"))
        assertFalse(edited, edited.startsWith("错误"))
        assertEquals("unsaved\nchanged\nthird", document.text)
        assertTrue(executor.execute("write_file", mapOf("path" to "AgentSample.txt", "content" to "overwrite")).startsWith("错误"))
        WriteCommandAction.runWriteCommandAction(project) { document.setText("repeat repeat") }
        assertTrue(executor.execute("edit_file", mapOf("path" to "AgentSample.txt", "old_string" to "repeat", "new_string" to "bad")).startsWith("错误"))
        assertEquals("repeat repeat", document.text)
    }
}
