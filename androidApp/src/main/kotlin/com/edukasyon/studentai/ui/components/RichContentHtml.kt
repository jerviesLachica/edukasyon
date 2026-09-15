package com.edukasyon.studentai.ui.components

/**
 * Converts AI message content to HTML with KaTeX (math), Mermaid (diagrams),
 * and Chart.js (graphs).
 *
 * Detection rules:
 * - LaTeX math: $$...$$ (display) or $...$ (inline)
 * - Mermaid diagrams: ```mermaid code blocks
 * - Chart.js graphs: ```chart code blocks (JSON: {type, data, options})
 * - Regular text: rendered as styled paragraphs
 */
internal fun buildRichContentHtml(
    content: String,
    isDark: Boolean = false,
): String {
    val bgColor = if (isDark) "#1a1a2e" else "#fafafa"
    val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
    val codeBg = if (isDark) "#2d2d44" else "#f0f0f5"

    val bodyHtml = buildString {
        val blocks = splitContentBlocks(content)
        blocks.forEachIndexed { blockIndex, block ->
            when {
                block.isCodeFence && block.lang == "mermaid" -> {
                    append("""<div class="mermaid">${escapeHtml(block.code)}</div>""")
                }
                block.isCodeFence && block.lang == "chart" -> {
                    val chartId = "chart_$blockIndex"
                    append("""<div class="chart-container"><canvas id="$chartId"></canvas></div>""")
                    append(
                        """<script>
try {
    const cfg = JSON.parse('${escapeJs(block.code)}');
    const ctx = document.getElementById('$chartId').getContext('2d');
    new Chart(ctx, cfg);
} catch(e) {
    document.getElementById('$chartId').parentElement.innerHTML =
        '<pre class="code-block">${escapeHtml(block.code)}</pre>';
}
</script>"""
                    )
                }
                block.isCodeFence -> {
                    val lang = block.lang ?: ""
                    append("""<pre class="code-block"><code class="language-${lang}">${escapeHtml(block.code)}</code></pre>""")
                }
                else -> {
                    val withMath = renderInlineMath(block.text, "b$blockIndex")
                    append("""<p class="msg-text">$withMath</p>""")
                }
            }
        }
    }

    return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11.17.2/dist/mermaid.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.5.1"></script>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,'Segoe UI',Roboto,sans-serif;font-size:15px;line-height:1.55;color:$textColor;background:transparent;padding:0;-webkit-text-size-adjust:none}
.msg-text{margin:0 0 8px 0}
.code-block{background:$codeBg;border-radius:8px;padding:10px 12px;font-family:'Fira Code','Cascadia Code',monospace;font-size:13px;overflow-x:auto;margin:6px 0;white-space:pre-wrap;word-break:break-word}
.chart-container{background:$bgColor;border-radius:12px;padding:12px;margin:8px 0;max-height:300px}
.chart-container canvas{max-height:280px}
.katex-display{margin:8px 0;overflow-x:auto}
.katex{font-size:1.05em}
.mermaid{background:$bgColor;border-radius:12px;padding:12px;margin:8px 0;text-align:center}
</style>
</head>
<body>
$bodyHtml
<script>
mermaid.initialize({
    startOnLoad:true,
    theme:'${if (isDark) "dark" else "default"}',
    securityLevel:'loose',
    fontFamily:'-apple-system, sans-serif',
});
document.addEventListener('DOMContentLoaded',function(){
    renderMathInElement(document.body,{
        delimiters:[
            {left:'\\(',right:'\\)',display:false},
            {left:'\\[',right:'\\]',display:true},
        ],
        throwOnError:false,
    });
    function reportHeight(){
        const h=document.body.scrollHeight;
        if(h>0)Bridge.onHeightChanged(h);
    }
    reportHeight();
    setTimeout(reportHeight,500);
    setTimeout(reportHeight,2000);
    new MutationObserver(reportHeight).observe(document.body,{childList:true,subtree:true});
});
</script>
</body>
</html>""".trimIndent()
}

/** Represents a parsed block of content. */
internal data class ContentBlock(
    val text: String = "",
    val isCodeFence: Boolean = false,
    val lang: String? = null,
    val code: String = "",
)

/** Split content into code fences and paragraph text. */
internal fun splitContentBlocks(content: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = content.replace("\r\n", "\n").split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim().lowercase().ifEmpty { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(ContentBlock(isCodeFence = true, lang = lang, code = codeLines.joinToString("\n")))
            if (i < lines.size) i++ // skip closing ```
        } else if (line.isBlank()) {
            i++
        } else {
            val paraLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().isNotEmpty() && !lines[i].trimStart().startsWith("```")) {
                paraLines.add(lines[i].trim())
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(ContentBlock(text = paraLines.joinToString(" ")))
            }
        }
    }
    return blocks
}

/**
 * Render inline $...$ and display $$...$$ math as KaTeX spans.
 * [idPrefix] must be unique per call site — sequential call indices keep
 * element IDs globally distinct (a plain text index collides across blocks).
 */
internal fun renderInlineMath(text: String, idPrefix: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        // Display math $$...$$
        if (i + 1 < text.length && text[i] == '$' && text[i + 1] == '$') {
            val end = text.indexOf("$$", i + 2)
            if (end != -1 && end > i + 2) {
                val expr = text.substring(i + 2, end).trim()
                if (looksLikeMath(expr)) {
                    val id = "${idPrefix}_d$i"
                    sb.append("""<span id="$id"></span>""")
                    sb.append(
                        """<script>try{katex.render('${escapeJs(expr)}',document.getElementById('$id'),{displayMode:true,throwOnError:false})}catch(e){document.getElementById('$id').textContent='${escapeJs(expr)}'}</script>"""
                    )
                    i = end + 2
                    continue
                }
            }
        }
        // Inline math $...$ (only when it actually looks like math —
        // "$5 and $10" must stay literal currency text)
        if (text[i] == '$') {
            val end = text.indexOf('$', i + 1)
            if (end != -1 && end > i + 1) {
                val expr = text.substring(i + 1, end).trim()
                if (looksLikeMath(expr)) {
                    val id = "${idPrefix}_i$i"
                    sb.append("""<span id="$id"></span>""")
                    sb.append(
                        """<script>try{katex.render('${escapeJs(expr)}',document.getElementById('$id'),{displayMode:false,throwOnError:false})}catch(e){document.getElementById('$id').textContent='${escapeJs(expr)}'}</script>"""
                    )
                    i = end + 1
                    continue
                }
            }
        }
        sb.append(escapeHtml(text[i].toString()))
        i++
    }
    return sb.toString()
}

/**
 * Heuristic: real LaTeX contains markup or math operators, or is a single
 * tight token. Rejects currency runs like "5 and " and bare numbers.
 */
internal fun looksLikeMath(expr: String): Boolean {
    if (expr.isBlank()) return false
    if (expr.contains('\\')) return true
    if (Regex("""[_^{}=]""").containsMatchIn(expr)) return true
    if (Regex("""\d\s*[+\-*/<>=]\s*\d""").containsMatchIn(expr)) return true
    // No spaces + short + has letters/digits (e.g. "x^2" handled above;
    // "E=mc2" too) — but reject multi-word prose.
    return !expr.contains(' ') && Regex("""^[A-Za-z0-9.,;!?]+$""").matches(expr).not() &&
        Regex("""^[A-Za-z0-9]+$""").matches(expr)
}

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun escapeJs(s: String): String =
    s.replace("\\", "\\\\").replace("'", "\\'")
        .replace("\n", "\\n").replace("\r", "")
        // Neutralize "</script" so embedded strings can never close the tag.
        .replace("<", "\\u003C")
