package com.github.joansyu.aiagentplugin.tools

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ProjectPathsTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun rejectsEscapeAndInternalDirectories() {
        val root = temp.newFolder("project").toPath()
        val paths = ProjectPaths(root.toString())
        assertEquals(root.resolve("src/Main.java"), paths.resolve("src/Main.java"))
        for (path in listOf("../other/Secret.java", ".git/config", "build/output.java", root.resolve("file.java").toString())) {
            assertThrows(IllegalArgumentException::class.java) { paths.resolve(path) }
        }
    }
    @Test fun validatesNonexistentDescendantsOfLinks() {
        val root = temp.newFolder("project").toPath()
        val outside = temp.newFolder("outside").toPath()
        try { Files.createSymbolicLink(root.resolve("linked"), outside) }
        catch (e: Exception) { org.junit.Assume.assumeNoException(e) }
        assertThrows(IllegalArgumentException::class.java) { ProjectPaths(root.toString()).resolve("linked/new/File.java") }
    }
}
