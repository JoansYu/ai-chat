package com.github.joansyu.aiagentplugin.tools

import com.github.joansyu.aiagentplugin.settings.AiChatSettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.TextOccurenceProcessor
import com.intellij.psi.search.searches.ReferencesSearch
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * IDE 侧工具执行器：替代原 local-agent-client 的 LocalToolExecutor，
 * 在用户本机执行 Agent 的文件工具请求。包含沙箱路径白名单 + 扩展名白名单。
 *
 * 与后端/原客户端完全兼容的工具集：
 *   read_file / list_directory / search_code / glob_files （核心只读 4 件套）
 * 额外增强的写操作：
 *   write_file / edit_file / create_directory / delete_file / move_file / get_file_info
 */
class IdeToolExecutor {

    private val allowedExtensions: Set<String> = setOf(
        ".java", ".py", ".js", ".ts", ".vue", ".jsx", ".tsx",
        ".xml", ".yml", ".yaml", ".json", ".properties",
        ".md", ".txt", ".sql", ".html", ".css", ".scss",
        ".go", ".rs", ".c", ".cpp", ".h", ".hpp",
        ".kt", ".swift", ".rb", ".php", ".sh", ".toml",
        ".config", ".conf", ".ini", ".gradle", ".pom"
    )

    private val ignoredDirs: Set<String> = setOf(
        "node_modules", ".git", ".idea", "__pycache__", ".vscode",
        "target", "build", "dist", ".gradle", ".mvn"
    )

    companion object {
        private const val MAX_FILE_SIZE = 1024L * 1024
        private const val MAX_DIR_DEPTH = 5
        private const val MAX_SEARCH_RESULTS = 50
        private const val MAX_GLOB_RESULTS = 100
        private const val MAX_SYMBOL_RESULTS = 50
        private const val MAX_STRUCTURE_DEPTH = 3
    }

    fun execute(tool: String, args: Map<String, Any?>): String {
        return try {
            when (tool) {
                "read_file" -> readFile(strArg(args, "path"))
                "list_directory" -> listDirectory(strArg(args, "path", "."))
                "search_code" -> searchCode(strArg(args, "pattern"), strArg(args, "path", "."))
                "glob_files" -> globFiles(strArg(args, "pattern"), strArg(args, "path", "."))
                "write_file" -> writeFile(strArg(args, "path"), strArg(args, "content", ""))
                "edit_file" -> editFile(strArg(args, "path"), strArg(args, "old_string"), strArg(args, "new_string", ""))
                "create_directory" -> createDirectory(strArg(args, "path"))
                "delete_file" -> deleteFile(strArg(args, "path"))
                "move_file" -> moveFile(strArg(args, "source"), strArg(args, "destination"))
                "get_file_info" -> getFileInfo(strArg(args, "path"))
                "read_project_structure" -> readProjectStructure(strArg(args, "path", "."))
                "get_symbol_info" -> getSymbolInfo(strArg(args, "symbol"))
                "find_usages" -> findUsages(strArg(args, "symbol"))
                "read_file_region" -> readFileRegion(
                    strArg(args, "path"),
                    strArg(args, "startLine")?.toIntOrNull(),
                    strArg(args, "endLine")?.toIntOrNull()
                )
                else -> "未知工具: $tool"
            }
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: Exception) {
            thisLogger().warn("工具执行异常: $tool", e)
            "工具[$tool]执行异常: ${e.message}"
        }
    }

    private fun workspaceRoot(): Path {
        val ws = AiChatSettings.getInstance().workspacePath
        if (ws.isNullOrBlank()) throw SandboxException("未授权工作区，请先在设置中配置工作区路径")
        val root = Path.of(ws).toAbsolutePath().normalize()
        return root
    }

    /** 沙箱核心校验：相对路径 -> 工作区内绝对路径，越界抛异常 */
    private fun resolveSafe(rel: String?): Path {
        if (rel.isNullOrBlank()) throw SandboxException("path 参数不能为空")
        val root = workspaceRoot()
        val target = root.resolve(rel).normalize()
        if (!target.startsWith(root)) throw SandboxException("路径越界: $rel 不在授权工作区内")
        return target
    }

    private fun isAllowedExtension(path: Path): Boolean {
        val name = path.fileName?.toString() ?: return false
        val dot = name.lastIndexOf('.')
        if (dot < 0) return false
        return allowedExtensions.contains(name.substring(dot).lowercase())
    }

    // ===================== 只读工具（与原 LocalToolExecutor 一致） =====================

    private fun readFile(rel: String?): String {
        return try {
            val target = resolveSafe(rel)
            if (!Files.isRegularFile(target)) return "错误: 文件不存在 - $rel"
            val size = Files.size(target)
            if (size > MAX_FILE_SIZE) return "错误: 文件过大 ($size bytes)，最大支持 $MAX_FILE_SIZE bytes"
            if (!isAllowedExtension(target)) return "错误: 不支持的文件类型（仅允许代码/配置文件）"

            val lines = Files.readAllLines(target, StandardCharsets.UTF_8)
            val sb = StringBuilder()
            val width = lines.size.toString().length
            for (i in lines.indices) {
                sb.append(String.format("%${width}d\t%s%n", i + 1, lines[i]))
            }
            sb.toString()
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "读取文件异常: ${e.message}"
        }
    }

    private fun listDirectory(rel: String?): String {
        return try {
            val target = resolveSafe(rel)
            if (!Files.isDirectory(target)) return "错误: 目录不存在 - $rel"
            val lines = ArrayList<String>()
            buildTree(target, "", 0, lines)
            if (lines.isEmpty()) "（空目录）" else lines.joinToString("\n")
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "列目录异常: ${e.message}"
        }
    }

    private fun buildTree(dir: Path, prefix: String, depth: Int, result: MutableList<String>) {
        if (depth >= MAX_DIR_DEPTH) {
            result.add("$prefix... (最大深度 $MAX_DIR_DEPTH 层)")
            return
        }
        val entries: List<Path> = Files.list(dir).use { it.sorted().toList() }
        val filtered = entries.filter { p ->
            val name = p.fileName?.toString() ?: return@filter false
            !ignoredDirs.contains(name) && !name.startsWith(".")
        }
        for (i in filtered.indices) {
            val entry = filtered[i]
            val isLast = i == filtered.size - 1
            val connector = if (isLast) "└── " else "├── "
            result.add(prefix + connector + entry.fileName)
            if (Files.isDirectory(entry)) {
                val ext = if (isLast) "    " else "│   "
                buildTree(entry, prefix + ext, depth + 1, result)
            }
        }
    }

    private fun searchCode(pattern: String?, rel: String?): String {
        if (pattern.isNullOrBlank()) return "错误: pattern 参数不能为空"
        val regex: Pattern = try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        } catch (e: PatternSyntaxException) {
            return "正则表达式错误: ${e.message}"
        }
        return try {
            val root = workspaceRoot()
            val target = root.resolve(rel ?: ".").normalize()
            val searchRoot = if (Files.isDirectory(target)) target else target.parent
                ?: return "错误: 无效路径 - $rel"
            if (!searchRoot.startsWith(root)) return "沙箱拦截: 路径越界 $rel"

            val results = ArrayList<String>()
            Files.walk(searchRoot).use { walkStream ->
                val walkIter = walkStream.iterator()
                while (walkIter.hasNext() && results.size < MAX_SEARCH_RESULTS) {
                    val file = walkIter.next()
                    if (Files.isDirectory(file)) continue
                    if (!isAllowedExtension(file)) continue
                    if (file.sizeExceeds(MAX_FILE_SIZE)) continue
                    if (isIgnored(file, root)) continue

                    val relPath = root.relativize(file.toAbsolutePath().normalize()).toString()
                    Files.lines(file, StandardCharsets.UTF_8).use { lineStream ->
                        val lineIter = lineStream.iterator()
                        var lineNo = 1
                        while (lineIter.hasNext() && results.size < MAX_SEARCH_RESULTS) {
                            val line = lineIter.next()
                            if (regex.matcher(line).find()) {
                                results.add("$relPath:$lineNo: ${line.trim()}")
                            }
                            lineNo++
                        }
                    }
                }
            }
            if (results.size >= MAX_SEARCH_RESULTS) {
                results.add("... 已达最大结果数 $MAX_SEARCH_RESULTS，截断")
            }
            if (results.isEmpty()) "未找到匹配结果" else results.joinToString("\n")
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "搜索代码异常: ${e.message}"
        }
    }

    private fun globFiles(pattern: String?, rel: String?): String {
        if (pattern.isNullOrBlank()) return "错误: pattern 参数不能为空"
        return try {
            val root = workspaceRoot()
            val target = root.resolve(rel ?: ".").normalize()
            if (!target.startsWith(root)) return "沙箱拦截: 路径越界 $rel"
            if (!Files.isDirectory(target)) return "错误: 目录不存在 - $rel"
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            val lines = ArrayList<String>()
            Files.walk(target).use { stream ->
                val iter = stream.iterator()
                while (iter.hasNext() && lines.size < MAX_GLOB_RESULTS) {
                    val p = iter.next()
                    val r = root.relativize(p.toAbsolutePath().normalize())
                    if (matcher.matches(r)) {
                        lines.add((if (Files.isDirectory(p)) "??" else "??") + " " + r)
                    }
                }
            }
            if (lines.size >= MAX_GLOB_RESULTS) {
                lines.add("... 已达最大显示数 $MAX_GLOB_RESULTS")
            }
            when {
                lines.isEmpty() -> "未找到匹配 $pattern 的文件"
                else -> lines.joinToString("\n")
            }
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "文件匹配异常: ${e.message}"
        }
    }

    // ===================== 项目阅读工具（PSI 语义） =====================

    /** 项目结构树：目录 + 代码文件内的类/方法概览 */
    private fun readProjectStructure(rel: String?): String {
        val project = currentProject() ?: return "错误: 未打开任何 IntelliJ 项目"
        val target = resolveSafe(rel ?: ".")
        return try {
            ReadAction.compute<String, Exception> {
                val sb = StringBuilder()
                sb.append("?? 项目结构: ${AiChatSettings.getInstance().workspacePath}\n")
                buildPsiTree(project, target, "", 0, sb)
                if (sb.length == 0) "（空目录）" else sb.toString()
            }
        } catch (e: Exception) {
            "读取项目结构异常: ${e.message}"
        }
    }

    private fun buildPsiTree(project: Project, dir: Path, prefix: String, depth: Int, sb: StringBuilder) {
        if (depth >= MAX_STRUCTURE_DEPTH) {
            sb.append(prefix).append("... (最大深度 $MAX_STRUCTURE_DEPTH 层)\n")
            return
        }
        val entries: List<Path> = Files.list(dir).use { it.sorted().toList() }
        val filtered = entries.filter { p ->
            val name = p.fileName?.toString() ?: return@filter false
            !ignoredDirs.contains(name) && !name.startsWith(".")
        }
        for (i in filtered.indices) {
            val entry = filtered[i]
            val isLast = i == filtered.size - 1
            val connector = if (isLast) "└── " else "├── "
            if (Files.isDirectory(entry)) {
                sb.append(prefix).append(connector).append(entry.fileName).append("/\n")
                val ext = if (isLast) "    " else "│   "
                buildPsiTree(project, entry, prefix + ext, depth + 1, sb)
            } else {
                sb.append(prefix).append(connector).append(entry.fileName)
                val symbols = fileSymbols(project, entry)
                if (symbols.isNotEmpty()) {
                    sb.append("   (").append(symbols.joinToString(" | ")).append(")")
                }
                sb.append("\n")
            }
        }
    }

    /** 提取单个代码文件的类/方法概览（目前支持 Java；其余仅列文件名） */
    private fun fileSymbols(project: Project, file: Path): List<String> {
        val vf = LocalFileSystem.getInstance()
            .findFileByPath(file.toAbsolutePath().normalize().toString().replace('\\', '/'))
            ?: return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return emptyList()
        val result = ArrayList<String>()
        if (psiFile is PsiJavaFile) {
            for (cls in psiFile.classes) {
                val methodBrief = cls.methods.take(6).joinToString(", ") { methodBrief(it) }
                result.add("class ${cls.name}{$methodBrief${if (cls.methods.size > 6) ",..." else ""}}")
            }
        }
        return result
    }

    private fun methodBrief(m: PsiMethod): String {
        val params = m.parameterList.parameters.joinToString(", ") { it.type.presentableText }
        return "${m.name}($params)"
    }

    /** 查询符号（类/方法/字段）的定义位置与签名 */
    private fun getSymbolInfo(symbol: String?): String {
        if (symbol.isNullOrBlank()) return "错误: symbol 参数不能为空"
        val project = currentProject() ?: return "错误: 未打开任何 IntelliJ 项目"
        return try {
            ReadAction.compute<String, Exception> {
                val scope = GlobalSearchScope.projectScope(project)
                val psiClass = JavaPsiFacade.getInstance(project).findClass(symbol, scope)
                if (psiClass != null) {
                    return@compute classInfo(psiClass)
                }
                val found = ArrayList<String>()
                PsiSearchHelper.getInstance(project).processElementsWithWord(
                    TextOccurenceProcessor { element: PsiElement, _: Int ->
                        if (element is PsiNamedElement && element.name == symbol) {
                            val file = element.containingFile.virtualFile?.path ?: ""
                            found.add("${element.javaClass.simpleName.replace("Psi", "")} @ $file:${lineOf(element)}")
                        }
                        found.size < MAX_SYMBOL_RESULTS
                    },
                    scope,
                    "",
                    1,
                    false
                )
                if (found.isEmpty()) "未找到符号: $symbol" else found.joinToString("\n")
            }
        } catch (e: Exception) {
            "查询符号异常: ${e.message}"
        }
    }

    private fun classInfo(cls: PsiClass): String {
        val sb = StringBuilder()
        sb.append("类: ${cls.qualifiedName}\n")
        sb.append("文件: ${cls.containingFile.virtualFile?.path}\n")
        sb.append("行号: ${lineOf(cls)}\n")
        val parents = cls.superTypes.joinToString(", ") { it.presentableText }
        if (parents.isNotEmpty()) sb.append("继承: $parents\n")
        if (cls.methods.isNotEmpty()) {
            sb.append("方法:\n")
            cls.methods.forEach { m ->
                sb.append("  - ${methodBrief(m)}")
                if (m.containingFile.virtualFile != null) {
                    sb.append("  @${cls.containingFile.name}:${lineOf(m)}")
                }
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    /** 查找符号的所有引用（文件:行） */
    private fun findUsages(symbol: String?): String {
        if (symbol.isNullOrBlank()) return "错误: symbol 参数不能为空"
        val project = currentProject() ?: return "错误: 未打开任何 IntelliJ 项目"
        return try {
            ReadAction.compute<String, Exception> {
                val scope = GlobalSearchScope.projectScope(project)
                var target: PsiElement? = JavaPsiFacade.getInstance(project).findClass(symbol, scope)
                if (target == null) {
                    val holder = arrayOfNulls<PsiElement>(1)
                    PsiSearchHelper.getInstance(project).processElementsWithWord(
                        TextOccurenceProcessor { element: PsiElement, _: Int ->
                            if (element is PsiNamedElement && element.name == symbol &&
                                (element is PsiMethod || element is com.intellij.psi.PsiField)) {
                                if (holder[0] == null) {
                                    holder[0] = element
                                    return@TextOccurenceProcessor false
                                }
                            }
                            true
                        },
                        scope,
                        "",
                        1,
                        false
                    )
                    target = holder[0]
                }
                if (target == null) return@compute "未找到符号: $symbol"

                val lines = ArrayList<String>()
                ReferencesSearch.search(target, scope).forEach { ref ->
                    val el = ref.element
                    val file = el.containingFile.virtualFile?.path ?: ""
                    val text = el.text.replace("\n", " ").trim().take(80)
                    lines.add("$file:${lineOf(el)} : $text")
                    lines.size < MAX_SYMBOL_RESULTS
                }
                if (lines.isEmpty()) "未找到引用: $symbol" else lines.joinToString("\n")
            }
        } catch (e: Exception) {
            "查找引用异常: ${e.message}"
        }
    }

    /** 分片读取文件（行区间） */
    private fun readFileRegion(rel: String?, startLine: Int?, endLine: Int?): String {
        return try {
            val target = resolveSafe(rel)
            if (!Files.isRegularFile(target)) return "错误: 文件不存在 - $rel"
            if (!isAllowedExtension(target)) return "错误: 不支持的文件类型"
            val allLines = Files.readAllLines(target, StandardCharsets.UTF_8)
            val start = (startLine ?: 1).coerceAtLeast(1)
            val end = (endLine ?: allLines.size).coerceAtMost(allLines.size)
            if (start > end) return "错误: startLine($start) 大于 endLine($end)"
            val sb = StringBuilder()
            val width = end.toString().length
            for (i in start..end) {
                sb.append(String.format("%${width}d\t%s%n", i, allLines[i - 1]))
            }
            sb.append("--- ${end - start + 1} 行 / 共 ${allLines.size} 行 ---")
            sb.toString()
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "读取文件异常: ${e.message}"
        }
    }

    /** 当前打开的第一个 IntelliJ 项目 */
    private fun currentProject(): Project? =
        ProjectManager.getInstance().openProjects.firstOrNull()

    /** PSI 元素所在行号 */
    private fun lineOf(el: PsiElement): Int {
        val file = el.containingFile ?: return 1
        val doc = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return 1
        return doc.getLineNumber(el.textOffset) + 1
    }

    // ===================== 写操作工具（增强） =====================

    private fun writeFile(rel: String?, content: String): String {
        return try {
            val target = resolveSafe(rel)
            if (content.length > MAX_FILE_SIZE) return "错误: 内容过大"
            Files.createDirectories(target.parent)
            Files.writeString(target, content, StandardCharsets.UTF_8)
            refreshVfs(target)
            "已写入: $rel (${content.length} 字符)"
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "写入文件异常: ${e.message}"
        }
    }

    private fun editFile(rel: String?, oldString: String?, newString: String): String {
        if (oldString.isNullOrBlank()) return "错误: old_string 参数不能为空"
        return try {
            val target = resolveSafe(rel)
            if (!Files.isRegularFile(target)) return "错误: 文件不存在 - $rel"
            val original = Files.readString(target, StandardCharsets.UTF_8)
            val count = original.windowedOccurrenceCount(oldString)
            if (count == 0) return "错误: 未找到匹配的 old_string"
            if (count > 1) return "错误: old_string 匹配到 $count 处，必须唯一匹配"
            val updated = original.replace(oldString, newString)
            Files.writeString(target, updated, StandardCharsets.UTF_8)
            refreshVfs(target)
            "已替换: $rel"
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "编辑文件异常: ${e.message}"
        }
    }

    private fun createDirectory(rel: String?): String {
        return try {
            val target = resolveSafe(rel)
            Files.createDirectories(target)
            refreshVfs(target)
            "已创建目录: $rel"
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "创建目录异常: ${e.message}"
        }
    }

    private fun deleteFile(rel: String?): String {
        return try {
            val target = resolveSafe(rel)
            val root = workspaceRoot()
            if (target == root) return "错误: 禁止删除工作区根目录"
            if (!Files.exists(target)) return "错误: 路径不存在 - $rel"
            Files.walk(target)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
            refreshVfs(target)
            "已删除: $rel"
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "删除异常: ${e.message}"
        }
    }

    private fun moveFile(srcRel: String?, dstRel: String?): String {
        if (srcRel.isNullOrBlank() || dstRel.isNullOrBlank()) return "错误: source/destination 不能为空"
        return try {
            val src = resolveSafe(srcRel)
            val dst = resolveSafe(dstRel)
            if (!Files.exists(src)) return "错误: 源路径不存在 - $srcRel"
            Files.createDirectories(dst.parent)
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING)
            refreshVfs(src)
            refreshVfs(dst)
            "已移动: $srcRel -> $dstRel"
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "移动异常: ${e.message}"
        }
    }

    private fun getFileInfo(rel: String?): String {
        return try {
            val target = resolveSafe(rel)
            if (!Files.exists(target)) return "错误: 路径不存在 - $rel"
            val size = if (Files.isRegularFile(target)) Files.size(target) else 0L
            val type = if (Files.isDirectory(target)) "directory" else "file"
            val sb = StringBuilder()
            sb.append("path: $rel\n")
            sb.append("type: $type\n")
            sb.append("size: $size bytes\n")
            sb.append("readable: ${Files.isReadable(target)}\n")
            sb.append("writable: ${Files.isWritable(target)}\n")
            sb.append("modified: ${Files.getLastModifiedTime(target)}")
            sb.toString()
        } catch (e: SandboxException) {
            "沙箱拦截: ${e.message}"
        } catch (e: IOException) {
            "获取信息异常: ${e.message}"
        }
    }

    // ===================== 辅助 =====================

    private fun strArg(args: Map<String, Any?>, key: String): String? =
        args[key]?.let { it.toString() }

    private fun strArg(args: Map<String, Any?>, key: String, default: String): String =
        strArg(args, key)?.takeIf { it.isNotBlank() } ?: default

    private fun isIgnored(file: Path, root: Path): Boolean {
        val rel = root.relativize(file)
        for (part in rel) {
            val name = part.toString()
            if (ignoredDirs.contains(name) || name.startsWith(".")) return true
        }
        return false
    }

    private fun Path.sizeExceeds(limit: Long): Boolean = try {
        Files.size(this) > limit
    } catch (e: IOException) {
        true
    }

    private fun String.windowedOccurrenceCount(sub: String): Int {
        if (sub.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val pos = indexOf(sub, idx)
            if (pos < 0) break
            count++
            idx = pos + sub.length
        }
        return count
    }

    /** 写操作后刷新 VFS，让 IDE 项目视图即时看到变化 */
    private fun refreshVfs(path: Path) {
        try {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())
            if (file != null) {
                LocalFileSystem.getInstance().refreshFiles(listOf(file))
            }
        } catch (e: Exception) {
            thisLogger().warn("VFS 刷新失败: $path", e)
        }
    }

    private class SandboxException(message: String) : RuntimeException(message)
}
