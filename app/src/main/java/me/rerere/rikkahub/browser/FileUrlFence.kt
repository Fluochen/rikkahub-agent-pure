package me.rerere.rikkahub.browser

import java.io.File

/**
 * `file://` 围栏：把「能读哪些本地文件」这件事收敛成两个纯函数。
 *
 * 背景：技能页与技能 iframe 的本地包装页都是 `file://` 文档，WebView 开着
 * `allowFileAccessFromFileURLs` 才能加载同目录的音频/图片/子页面。代价是——只要当前文档是
 * `file://`，一次 `location.href = 'file:///data/data/<pkg>/shared_prefs/…'` 就能把应用私有
 * 文件读进 WebView，再用 `browser_get_text` 带出去。放行过宽的地方有两处：技能的
 * `window.open` 返回值（RunJsTool）与 WebView 的导航回调（BrowserView）。
 *
 * 两个函数都不碰 Android API —— 手写解析而不是 `Uri.parse`，是为了能在裸 JVM 单测里覆盖。
 */

/** `file://` URL 的路径部分；不是 file://、或 authority 不是本机时返回 null。 */
internal fun fileUrlPath(url: String): String? {
    if (!url.startsWith("file:", ignoreCase = true)) return null
    var rest = url.substring("file:".length)
    if (rest.startsWith("//")) {
        rest = rest.substring(2)
        val slash = rest.indexOf('/')
        // file://host/path —— 只认本机/空 authority；外部主机名一律不当本地路径处理。
        if (slash < 0) return null
        val authority = rest.substring(0, slash)
        if (authority.isNotEmpty() && !authority.equals("localhost", ignoreCase = true)) return null
        rest = rest.substring(slash)
    }
    val path = rest.substringBefore('?').substringBefore('#')
    return path.ifEmpty { null }
}

/**
 * [path] 是否落在 [roots] 之中（含根本身）。
 *
 * 用 canonicalPath 比前缀，`../`、`./`、符号链接都先被解开再判，避免「拼字符串前缀」那种
 * `/skills-evil` 冒充 `/skills` 的老问题。
 */
internal fun isWithinAnyRoot(path: String?, roots: List<File>): Boolean {
    if (path.isNullOrBlank()) return false
    val target = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
    return roots.any { root ->
        val rootCanonical = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
        target == rootCanonical || target.startsWith(rootCanonical + File.separator)
    }
}
