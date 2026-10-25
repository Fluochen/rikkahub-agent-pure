package me.rerere.rikkahub.browser

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `file://` 围栏的单测。
 *
 * 这一对函数是「技能页能把应用私有文件读出来」那条路（RunJsTool 的 window.open 返回值、
 * WebView 的导航回调）唯一的判定点，所以边界值要逐条钉住。
 */
class FileUrlFenceTest {

    @Test
    fun `file url path is extracted for local authority`() {
        assertEquals("/data/data/pkg/files/a.html", fileUrlPath("file:///data/data/pkg/files/a.html"))
        assertEquals("/data/x.html", fileUrlPath("file://localhost/data/x.html"))
        // query / fragment 不属于路径
        assertEquals("/a/b.html", fileUrlPath("file:///a/b.html?v=1#top"))
    }

    @Test
    fun `non local file urls are not paths`() {
        assertNull("remote authority", fileUrlPath("file://evil.example.com/etc/passwd"))
        assertNull("http is not file", fileUrlPath("https://example.com/a.html"))
        assertNull("no path", fileUrlPath("file://"))
    }

    @Test
    fun `root check keeps descendants and rejects siblings with a shared prefix`() {
        val root = Files.createTempDirectory("fence-root").toFile()
        val inner = File(root, "ui/skill.html").apply { parentFile?.mkdirs(); writeText("x") }
        val evil = File(root.parentFile, "${root.name}-evil/skill.html").apply {
            parentFile?.mkdirs(); writeText("x")
        }

        assertTrue("root itself", isWithinAnyRoot(root.absolutePath, listOf(root)))
        assertTrue("descendant", isWithinAnyRoot(inner.absolutePath, listOf(root)))
        assertFalse("shared-prefix sibling", isWithinAnyRoot(evil.absolutePath, listOf(root)))
        assertFalse("escape via dot-dot", isWithinAnyRoot("$root/../etc/passwd", listOf(root)))
        assertFalse("null path", isWithinAnyRoot(null, listOf(root)))
    }

    @Test
    fun `app private directories are outside a skill root`() {
        val skills = Files.createTempDirectory("fence-skills").toFile()
        val secrets = "/data/data/me.rerere.rikkahub/shared_prefs/skill_secrets.xml"
        assertFalse("app private file", isWithinAnyRoot(secrets, listOf(skills)))
    }
}
