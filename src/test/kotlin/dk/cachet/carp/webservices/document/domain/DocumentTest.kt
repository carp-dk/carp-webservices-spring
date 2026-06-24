package dk.cachet.carp.webservices.document.domain

import dk.cachet.carp.webservices.file.service.FileService
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.node.JsonNodeFactory
import kotlin.test.Test

class DocumentTest {
    @Nested
    inner class DocumentListenerTest {
        @Nested
        inner class DeleteImageResource {
            private val fileService: FileService = mockk()

            @Test
            fun `if document doesn't have data do nothing`() {
                val listener = DocumentListener()
                val document = Document(data = null)
                every { fileService.deleteImage(any()) } just runs
                ReflectionTestUtils.setField(listener, "fileService", fileService)

                listener.deleteImageResource(document)

                verify(exactly = 0) { fileService.deleteImage(any()) }
            }

            @Test
            fun `if url is empty do nothing`() {
                val listener = DocumentListener()
                val dataNode = JsonNodeFactory.instance.objectNode().put("image", "")
                val document = Document(data = dataNode)
                every { fileService.deleteImage(any()) } just runs
                ReflectionTestUtils.setField(listener, "fileService", fileService)

                listener.deleteImageResource(document)

                verify(exactly = 0) { fileService.deleteImage(any()) }
            }

            @Test
            fun `if image url exists delete it`() {
                val listener = DocumentListener()
                val url = "someUrl"
                val dataNode = JsonNodeFactory.instance.objectNode().put("image", url)
                val document = Document(data = dataNode)
                every { fileService.deleteImage(any()) } just runs
                ReflectionTestUtils.setField(listener, "fileService", fileService)

                listener.deleteImageResource(document)

                verify(exactly = 1) { fileService.deleteImage(any()) }
            }

            @Test
            fun `if image property not set, do nothing`() {
                val listener = DocumentListener()
                val dataNode = JsonNodeFactory.instance.objectNode()
                val document = Document(data = dataNode)
                ReflectionTestUtils.setField(listener, "fileService", fileService)

                listener.deleteImageResource(document)

                verify(exactly = 0) { fileService.deleteImage(any()) }
            }
        }
    }
}
