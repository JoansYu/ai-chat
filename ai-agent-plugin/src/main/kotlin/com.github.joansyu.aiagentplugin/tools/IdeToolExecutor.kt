package com.github.joansyu.aiagentplugin.tools

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Every tool is bound to the Project owning the chat window. */
class IdeToolExecutor(private val project: Project) {
    private val paths by lazy { ProjectPaths(project.basePath ?: error("当前项目没有根目录")) }
    private val extensions = setOf("java", "kt", "kts", "py", "js", "ts", "vue", "jsx", "tsx",
        "xml", "yml", "yaml", "json", "properties", "md", "txt", "sql", "html", "css", "scss",
        "go", "rs", "c", "cpp", "h", "hpp", "swift", "rb", "php", "sh", "toml", "config", "conf", "ini", "gradle", "pom")
    private val maxBytes = 1024 * 1024

    fun execute(tool: String, args: Map<String, Any?>): String = try {
        check(!project.isDisposed) { "项目已关闭" }
        when (tool) {
            "get_project_context" -> projectContext()
            "read_file" -> readFile(string(args, "path"), 1, null)
            "read_file_region" -> readFile(string(args, "path"), integer(args, "startLine", 1), args["endLine"]?.let { integer(args, "endLine", 1) })
            "list_directory", "read_project_structure" -> listDirectory(string(args, "path", "."))
            "search_code" -> search(string(args, "path", "."), string(args, "pattern"))
            "glob_files" -> glob(string(args, "path", "."), string(args, "pattern"))
            "get_symbol_info" -> symbolInfo(string(args, "symbol"))
            "find_usages" -> usages(string(args, "symbol"))
            "write_file" -> writeFile(string(args, "path"), string(args, "content"))
            "edit_file" -> editFile(string(args, "path"), string(args, "old_string"), string(args, "new_string"))
            "rename_symbol" -> rename(string(args, "path"), integer(args, "offset", -1), string(args, "new_name"))
            "create_directory" -> writeCommand {
                val target = paths.resolve(string(args, "path"))
                VfsUtil.createDirectoryIfMissing(target.toString()) ?: error("无法创建目录")
                "已创建目录: " + relative(target)
            }
            "get_file_info" -> {
                val target = paths.resolve(string(args, "path"))
                "path: " + relative(target) + "\nexists: " + Files.exists(target) + "\ndirectory: " + Files.isDirectory(target)
            }
            else -> "错误: 未支持的工具: $tool"
        }
    } catch (e: Exception) {
        "错误: " + (e.message ?: e.javaClass.simpleName)
    }

    private fun string(args: Map<String, Any?>, key: String, default: String? = null): String =
        args[key] as? String ?: default ?: error("缺少字符串参数: $key")

    private fun integer(args: Map<String, Any?>, key: String, default: Int): Int {
        val value = args[key] ?: return default
        val number = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: error("无效整数: $key")
        require(number.isFinite() && number == number.toInt().toDouble()) { "无效整数: $key" }
        return number.toInt()
    }

    private fun relative(path: Path) = paths.root.relativize(path).toString().replace(92.toChar(), '/')
    private fun textFile(path: Path): Boolean = path.fileName.toString().substringAfterLast('.', "").lowercase() in extensions ||
        path.fileName.toString() in setOf("Dockerfile", "Makefile", ".gitignore", ".editorconfig")

    private fun checkedTextPath(rel: String): Path = paths.resolve(rel).also {
        require(textFile(it)) { "不支持的文件类型" }
        require(!Files.exists(it) || Files.size(it) <= maxBytes) { "文件超过 1 MiB 限制" }
    }

    private fun virtualFile(path: Path) = LocalFileSystem.getInstance().findFileByNioFile(path)
        ?: error("IDE 尚未发现文件，请刷新项目: " + relative(path))

    /** Prefer the live Document, including unsaved editor changes. */
    private fun text(path: Path): String = ReadAction.compute<String, RuntimeException> {
        val vf = LocalFileSystem.getInstance().findFileByNioFile(path)
        val content = vf?.let { FileDocumentManager.getInstance().getDocument(it)?.text }
            ?: Files.readString(path)
        require(content.toByteArray(Charsets.UTF_8).size <= maxBytes) { "文件超过 1 MiB 限制" }
        content
    }

    private fun readFile(rel: String, start: Int, requestedEnd: Int?): String {
        val content = text(checkedTextPath(rel))
        val lines = content.lines()
        require(start >= 1 && start <= lines.size) { "startLine 超出范围" }
        val end = minOf(requestedEnd ?: (start + 399), lines.size, start + 399)
        require(end >= start) { "endLine 小于 startLine" }
        return (start..end).joinToString("\n") { "$it\t" + lines[it - 1] } +
            "\n--- $start-$end / 共 " + lines.size + " 行；后续可用 read_file_region ---"
    }

    /** Prune excluded directories and all links during traversal; explicit paths still validate real ancestors. */
    private fun files(base: Path, limit: Int = 5000): List<Path> {
        require(Files.exists(base)) { "路径不存在" }
        val result = ArrayList<Path>()
        Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (!paths.allowed(dir) || (dir != base && dir.toRealPath() != dir.toAbsolutePath().normalize()))
                    FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && paths.allowed(file) && textFile(file)) result.add(file)
                return if (result.size >= limit) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, exc: java.io.IOException) = FileVisitResult.CONTINUE
        })
        return result
    }

    private fun listDirectory(rel: String): String {
        val base = paths.resolve(rel)
        require(Files.isDirectory(base)) { "目录不存在" }
        return "项目: " + project.name + "\n根目录: " + paths.root + "\n" +
            files(base, 500).joinToString("\n") { relative(it) } + "\n（最多列出 500 个代码/配置文件）"
    }

    private fun search(rel: String, pattern: String): String {
        require(pattern.isNotBlank()) { "pattern 不能为空" }
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val result = ArrayList<String>()
        for (file in files(paths.resolve(rel))) {
            if (Files.size(file) > maxBytes) continue
            text(file).lineSequence().forEachIndexed { index, line ->
                if (result.size < 50 && regex.containsMatchIn(line)) result.add(relative(file) + ":" + (index + 1) + ": " + line.take(500))
            }
            if (result.size >= 50) break
        }
        return result.joinToString("\n").ifEmpty { "未找到匹配结果" } + "\n（最多扫描 5000 个文件、返回 50 条结果）"
    }

    private fun glob(rel: String, pattern: String): String {
        val base = paths.resolve(rel)
        val normalized = pattern.replace(92.toChar(), '/')
        val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$normalized")
        val rootMatcher = if (normalized.startsWith("**/")) java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + normalized.removePrefix("**/")) else matcher
        return files(base).filter { matcher.matches(base.relativize(it)) || rootMatcher.matches(base.relativize(it)) }
            .take(100).joinToString("\n") { relative(it) }.ifEmpty { "未找到匹配文件" } + "\n（最多扫描 5000 个文件、返回 100 条结果）"
    }

    private fun projectContext(): String = onEdt {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val vf = editor?.let { FileDocumentManager.getInstance().getFile(it.document) }
        val active = vf?.let { Path.of(it.path) }?.takeIf { paths.allowed(it) && textFile(it) }
        Gson().toJson(mapOf(
            "projectName" to project.name, "workspaceRoot" to paths.root.toString(),
            "activeFile" to active?.let { relative(it) },
            "caretOffset" to if (active != null) editor?.caretModel?.offset else null,
            "selection" to if (active != null) editor?.selectionModel?.selectedText?.take(12000) else null,
            "indexing" to DumbService.isDumb(project)
        ))
    }

    private fun symbols(symbol: String): List<PsiNamedElement> {
        check(!DumbService.isDumb(project)) { "IDE 正在建立索引，请稍后重试或使用文本搜索" }
        val scope = GlobalSearchScope.projectScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        facade.findClass(symbol, scope)?.let { return listOf(it) }
        val cache = PsiShortNamesCache.getInstance(project)
        return (cache.getClassesByName(symbol, scope).toList() + cache.getMethodsByName(symbol, scope).toList() +
            cache.getFieldsByName(symbol, scope).toList()).filter { element ->
                element.containingFile?.virtualFile?.let { paths.allowed(Path.of(it.path)) } == true
            }.take(50)
    }

    private fun location(element: PsiElement): String {
        val file = element.containingFile ?: return "unknown"
        val doc = PsiDocumentManager.getInstance(project).getDocument(file)
        return relative(Path.of(file.virtualFile.path)) + ":" + ((doc?.getLineNumber(element.textOffset) ?: 0) + 1) +
            " offset=" + element.textOffset
    }

    private fun symbolInfo(symbol: String): String = ReadAction.compute<String, RuntimeException> {
        symbols(symbol).joinToString("\n") { element ->
            val details = if (element is PsiClass) element.methods.take(30).joinToString("\n") { "  " + it.name + it.parameterList.text + " @ " + location(it) } else element.text.take(600)
            element.name + " @ " + location(element) + "\n" + details
        }.ifEmpty { "未找到 Java 符号；其他语言请使用 search_code/read_file" }
    }

    private fun usages(symbol: String): String = ReadAction.compute<String, RuntimeException> {
        val targets = symbols(symbol)
        require(targets.size == 1) { "符号不存在或存在歧义，请使用类全限定名，或先定位到文件后使用文本搜索" }
        val result = ArrayList<String>()
        ReferencesSearch.search(targets.single(), GlobalSearchScope.projectScope(project)).forEach { ref ->
            if (paths.allowed(Path.of(ref.element.containingFile.virtualFile.path))) result.add(location(ref.element))
            result.size < 100
        }
        result.joinToString("\n").ifEmpty { "未找到引用" }
    }

    private fun writeFile(rel: String, content: String): String = writeCommand {
        val path = checkedTextPath(rel)
        require(!Files.exists(path)) { "文件已存在，请先读取并使用 edit_file 精确修改" }
        require(content.toByteArray(Charsets.UTF_8).size <= maxBytes) { "内容超过 1 MiB 限制" }
        val parent = VfsUtil.createDirectoryIfMissing(path.parent.toString()) ?: error("无法创建父目录")
        val vf = parent.createChildData(this, path.fileName.toString())
        val document = FileDocumentManager.getInstance().getDocument(vf) ?: error("无法创建文本文档")
        document.setText(content.replace("\r\n", "\n"))
        FileDocumentManager.getInstance().saveDocument(document)
        "已创建: $rel（支持 IDEA 撤销）"
    }

    private fun editFile(rel: String, old: String, replacement: String): String = writeCommand {
        require(old.isNotEmpty()) { "old_string 不能为空" }
        val path = checkedTextPath(rel)
        val document = FileDocumentManager.getInstance().getDocument(virtualFile(path)) ?: error("无法读取文本文档")
        val needle = old.replace("\r\n", "\n")
        val current = document.text
        val offset = current.indexOf(needle)
        require(offset >= 0 && current.indexOf(needle, offset + 1) < 0) { "old_string 必须在当前编辑器内容中唯一匹配，请重新读取" }
        val updated = current.replaceRange(offset, offset + needle.length, replacement.replace("\r\n", "\n"))
        require(updated.toByteArray(Charsets.UTF_8).size <= maxBytes) { "内容超过 1 MiB 限制" }
        document.setText(updated)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        FileDocumentManager.getInstance().saveDocument(document)
        "已修改: $rel（支持 IDEA 撤销）"
    }

    /** PSI resolves the declaration from a zero-based UTF-16 offset, avoiding ambiguous names. */
    private fun rename(rel: String, offset: Int, newName: String): String = onEdt {
        check(!DumbService.isDumb(project)) { "IDE 正在建立索引，请稍后重试" }
        val path = checkedTextPath(rel)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val psi = PsiManager.getInstance(project).findFile(virtualFile(path)) ?: error("无法获取 PSI")
        require(psi is PsiJavaFile) { "rename_symbol 当前支持 Java；其他语言使用 edit_file" }
        require(PsiNameHelper.getInstance(project).isIdentifier(newName)) { "无效 Java 标识符" }
        require(offset >= 0 && offset < psi.textLength) { "offset 超出范围" }
        val leaf = psi.findElementAt(offset) ?: error("未找到符号")
        val target = generateSequence(leaf) { it.parent }.filterIsInstance<PsiNameIdentifierOwner>()
            .firstOrNull { it.nameIdentifier?.textRange?.containsOffset(offset) == true } ?: error("offset 必须指向声明的名称")
        require(target is PsiClass || target is PsiMethod || target is PsiField || target is PsiLocalVariable || target is PsiParameter) { "不支持的声明" }
        val processor = RenameProcessor(project, target, newName, false, false)
        processor.setPreviewUsages(true)
        processor.run()
        "已启动 IDEA 重命名预览: $rel -> $newName。请在 Refactoring Preview 检查引用并点击 Do Refactor；尚未确认应用。"
    }

    private fun <T> onEdt(action: () -> T): T {
        if (ApplicationManager.getApplication().isDispatchThread) return action()
        var result: Result<T>? = null
        ApplicationManager.getApplication().invokeAndWait { result = runCatching { check(!project.isDisposed); action() } }
        return result!!.getOrThrow()
    }

    private fun <T> writeCommand(action: () -> T): T = onEdt {
        var result: Result<T>? = null
        WriteCommandAction.runWriteCommandAction(project, "AI Chat 修改", null, Runnable { result = runCatching(action) })
        result!!.getOrThrow()
    }
}
