package me.rerere.rikkahub.service.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowTemplatesTest {

    @Test
    fun `ships the mainstream work types with unique ids and file names`() {
        assertTrue(WorkflowTemplates.ALL.size >= 6)
        assertEquals(
            WorkflowTemplates.ALL.size,
            WorkflowTemplates.ALL.map { it.id }.distinct().size,
        )
        assertEquals(
            WorkflowTemplates.ALL.size,
            WorkflowTemplates.fileNames().distinct().size,
        )
        assertTrue(WorkflowTemplates.ALL.any { it.id == "coding" })
        assertTrue(WorkflowTemplates.ALL.any { it.id == "visual" })
        assertTrue(WorkflowTemplates.ALL.any { it.id == "writing" })
    }

    @Test
    fun `every template has the six sections the editor and launcher expect`() {
        WorkflowTemplates.ALL.forEach { template ->
            listOf("## 目标", "## 输入", "## 步骤", "## 产出", "## 验收", "## 注意事项")
                .forEach { section ->
                    assertTrue(
                        "${template.id} is missing $section",
                        template.body.contains(section),
                    )
                }
            assertTrue(template.summary.isNotBlank())
            assertTrue(template.category.isNotBlank())
        }
    }

    @Test
    fun `find resolves id, name and a trailing md suffix`() {
        assertNotNull(WorkflowTemplates.find("coding"))
        assertNotNull(WorkflowTemplates.find("coding.md"))
        assertNotNull(WorkflowTemplates.find("编程实现"))
        assertEquals(null, WorkflowTemplates.find("no-such-workflow"))
    }
}
