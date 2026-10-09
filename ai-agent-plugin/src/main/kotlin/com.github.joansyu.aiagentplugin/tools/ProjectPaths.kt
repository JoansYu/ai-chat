package com.github.joansyu.aiagentplugin.tools

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Shared by reads and writes; validate existing ancestors, including Windows junctions. */
internal class ProjectPaths(basePath: String) {
    val root: Path = Path.of(basePath).toRealPath()
    private val excluded = setOf(".git", ".idea", ".gradle", "node_modules", "target", "build", "dist", "__pycache__", ".venv", "venv")
    fun resolve(relative: String): Path {
        require(relative.isNotBlank()) { "path 参数不能为空" }
        val input = Path.of(relative)
        require(!input.isAbsolute) { "请使用相对项目根目录的路径" }
        val target = root.resolve(input).normalize()
        require(target.startsWith(root)) { "路径越界: $relative" }
        require(root.relativize(target).none { it.toString() in excluded }) { "禁止访问构建或内部目录" }
        var ancestor: Path? = target
        while (ancestor != null && ancestor.startsWith(root)) {
            if (Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
                require(ancestor.toRealPath().startsWith(root)) { "符号链接或 junction 指向项目外: $relative" }
            }
            ancestor = ancestor.parent
        }
        return target
    }
    fun allowed(path: Path): Boolean = try {
        resolve(root.relativize(path).toString().ifEmpty { "." })
        true
    } catch (_: Exception) { false }
}
