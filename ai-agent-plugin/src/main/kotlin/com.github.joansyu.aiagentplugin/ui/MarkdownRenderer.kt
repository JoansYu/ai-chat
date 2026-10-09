package com.github.joansyu.aiagentplugin.ui

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Markdown -> HTML（使用 IntelliJ Platform 自带 org.intellij.markdown）。
 *
 * 输出 Swing HTMLEditorKit 安全格式：
 * - 不用 <table>/CSS（表格会导致 JEditorPane 未显示时视口宽度为 0，
 *   气泡被缩到最小宽度、文字竖排）
 * - 代码块用 <pre> + <font face=monospace>
 * - 暗色/亮色主题自适应
 */
object MarkdownRenderer {

    private val flavour = GFMFlavourDescriptor()

    fun toHtml(markdown: String): String {
        val parsed = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        val rawBody = HtmlGenerator(markdown, parsed, flavour).generateHtml()
        return postProcess(rawBody)
    }

    /** 转义纯文本 */
    fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    /** 当前是否暗色主题 */
    private fun isDarkTheme(): Boolean {
        return javax.swing.UIManager.getLookAndFeel()?.name?.lowercase()?.contains("dark") == true
    }

    private fun postProcess(html: String): String {
        val dark = isDarkTheme()
        val codeColor = if (dark) "#CE9178" else "#C7254E"
        val textColor = if (dark) "#D4D4D4" else "#333333"
        val quoteColor = if (dark) "#9DA3A8" else "#6A737D"
        val linkColor = if (dark) "#569CD6" else "#0066CC"

        var result = html
        result = result.replace(Regex("""\s*class="[^"]*""""), "")
        // 行内代码
        result = result.replace("<code>", "<font face='Consolas,Monaco,monospace' color='$codeColor'>")
        result = result.replace("</code>", "</font>")
        // 代码块（<pre> 内已含 <code>，先处理 pre 的字体再让内部 code 上色）
        result = result.replace("<pre>", "<pre><font face='Consolas,Monaco,monospace' color='$textColor'>")
        result = result.replace("</pre>", "</font></pre>")
        // 引用
        result = result.replace("<blockquote>", "<font color='$quoteColor'><i>")
        result = result.replace("</blockquote>", "</i></font><br>")
        // 链接
        result = result.replace(Regex("""<a\s+href="([^"]*)"(\s+target="[^"]*")?>"""), "<a href='$1'><font color='$linkColor'>")
        result = result.replace("</a>", "</font></a>")
        // 标题
        for (i in 1..6) {
            val size = 6 - i + 3
            result = result.replace("<h$i>", "<font size='$size'><b>")
            result = result.replace("</h$i>", "</b></font><br>")
        }
        // 列表
        result = result.replace("<ul>", "<ul type='disc'>")
        result = result.replace("<ol>", "<ol type='1'>")
        return result
    }
}
