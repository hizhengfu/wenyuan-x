package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

class CollectionPrintAttachmentResolverTest {
    @TempDir lateinit var temp: Path
    private fun print(vararg fields: CollectionPrintFieldVo) = CollectionPrintVo(
        type = EnumCollectionPrintType.INVENTORY, title = "测试清点", documentNo = "123456789", orgName = "测试馆", fields = fields.toList())
    private fun user() = SecurityUser(id = "reader", name = "测试读者", username = "reader", password = "",
        groupIds = setOf("museum"), postIds = emptySet(), dataType = 0, roles = emptySet(),
        authorities = listOf(SimpleGrantedAuthority("FILE_SELECT")), enabled = true)
    private fun fixture(block: (FileReadAccess, CollectionActorDirectory) -> Unit) {
        val actor = user()
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
        try {
            transaction(Database.connect("jdbc:sqlite:${temp.resolve(UUID.randomUUID().toString() + ".db")}", "org.sqlite.JDBC")) {
                SchemaUtils.create(Files)
                val access = mock(FileReadAccess::class.java)
                val directory = mock(CollectionActorDirectory::class.java)
                `when`(directory.requireActive("reader")).thenReturn(actor)
                block(access, directory)
            }
        } finally { SecurityContextHolder.clearContext() }
    }
    private fun file(id: String, path: String, ext: String, size: Long) {
        FileDao.new(id) { name = "合法原件"; this.path = path; this.ext = ext; this.size = size; hash = "hash-$id" }
    }
    private fun png(width: Int = 1200, height: Int = 600): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try { g.color = Color.BLUE; g.fillRect(0, 0, width / 2, height) } finally { g.dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
    private fun evidence(access: FileReadAccess, path: String, baseline: FileContentBaseline? = null, revision: FileObjectRevision? = null) {
        `when`(access.readEvidence(path, "LOCAL")).thenReturn(FileReadAccess.ReadEvidence(FileReadAccess.ReadLocation(path, true), baseline, revision))
    }

    @Test fun `ordinary numeric text and labels never become attachments`() {
        val storage = mock(FileObjectStorage::class.java); val access = mock(FileReadAccess::class.java); val directory = mock(CollectionActorDirectory::class.java)
        val result = CollectionPrintAttachmentResolver(storage, access, directory).resolve(print(
            CollectionPrintFieldVo("文件编号", "2101234567891234560"), CollectionPrintFieldVo("影像", "/userfiles/picture.png")))
        assertTrue(result.isEmpty()); verifyNoInteractions(storage, access, directory)
    }

    @Test fun `authorized nonimage displays registered name and relative path without content read`() = fixture { access, directory ->
        file("document", "/2026/report.txt", "txt", 100)
        evidence(access, "/2026/report.txt")
        val storage = mock(FileObjectStorage::class.java)
        `when`(storage.exists("/2026/report.txt", "LOCAL")).thenReturn(true)
        `when`(storage.metadata("/2026/report.txt", "LOCAL")).thenReturn(FileObjectMetadata("/2026/report.txt", EnumFileStorageType.LOCAL, 100))
        val result = CollectionPrintAttachmentResolver(storage, access, directory).resolve(print(CollectionPrintFieldBuilder.attachment("附件", "document"))).single()
        assertEquals("合法原件.txt", result.name); assertEquals("2026/report.txt", result.path); assertNull(result.imageBytes)
        verify(storage, never()).openStream(anyString(), anyString(), nullable(Long::class.java), nullable(Long::class.java))
        verify(access).requireMetadataReadable("/2026/report.txt", "LOCAL")
    }

    @Test fun `real PNG content baseline produces proportional white background thumbnail`() = fixture { access, directory ->
        val bytes = png(); val path = "/2026/image.png"
        file("image", path, "png", bytes.size.toLong())
        val stored = temp.resolve("2026/image.png"); java.nio.file.Files.createDirectories(stored.parent); java.nio.file.Files.write(stored, bytes)
        evidence(access, path, FileContentBaseline.read(ByteArrayInputStream(bytes)))
        val result = CollectionPrintAttachmentResolver(LocalFileObjectStorage(temp.toString()), access, directory)
            .resolve(print(CollectionPrintFieldBuilder.attachment("点交影像", "image"))).single()
        val image = ImageIO.read(ByteArrayInputStream(result.imageBytes!!))
        assertEquals(600, image.width); assertEquals(300, image.height)
        assertEquals(Color.WHITE.rgb, image.getRGB(590, 100)); assertEquals(Color.BLUE.rgb, image.getRGB(50, 100))
        assertNull(result.status)
    }

    @Test fun `changed image bytes cannot be downgraded to a damaged-file thumbnail`() = fixture { access, directory ->
        val bytes = png(100, 50); val path = "/changed.png"
        file("changed", path, "png", bytes.size.toLong()); java.nio.file.Files.write(temp.resolve("changed.png"), bytes)
        evidence(access, path, FileContentBaseline.read(ByteArrayInputStream("different original".toByteArray())))
        assertThrows(BadRequestException::class.java) { CollectionPrintAttachmentResolver(LocalFileObjectStorage(temp.toString()), access, directory)
            .resolve(print(CollectionPrintFieldBuilder.attachment("原件", "changed"))) }
    }

    @Test fun `denied metadata reveals neither name nor path and never touches storage`() = fixture { access, directory ->
        file("secret", "/secret.png", "png", 10)
        doThrow(BadRequestException("无读取资格")).`when`(access).requireMetadataReadable("/secret.png", "LOCAL")
        val storage = mock(FileObjectStorage::class.java)
        assertThrows(BadRequestException::class.java) { CollectionPrintAttachmentResolver(storage, access, directory)
            .resolve(print(CollectionPrintFieldBuilder.attachment("原件", "secret"))) }
        verifyNoInteractions(storage)
    }

    @Test fun `actor revocation during image read blocks output`() = fixture { access, directory ->
        val bytes = png(100, 50); val path = "/revoked.png"
        file("revoked", path, "png", bytes.size.toLong()); evidence(access, path)
        val storage = mock(FileObjectStorage::class.java)
        `when`(storage.exists(path, "LOCAL")).thenReturn(true)
        `when`(storage.metadata(path, "LOCAL")).thenReturn(FileObjectMetadata(path, EnumFileStorageType.LOCAL, bytes.size.toLong()))
        `when`(storage.openStream(path, "LOCAL")).thenAnswer {
            `when`(directory.requireActive("reader")).thenThrow(BadRequestException("当前账号已停用"))
            ByteArrayInputStream(bytes)
        }
        assertThrows(BadRequestException::class.java) { CollectionPrintAttachmentResolver(storage, access, directory)
            .resolve(print(CollectionPrintFieldBuilder.attachment("原件", "revoked"))) }
    }

    @Test fun `frozen remote version is read even when current object is absent`() = fixture { access, directory ->
        val bytes = png(100, 50); val path = "/rustfs/image.png"
        file("remote", path, "png", bytes.size.toLong()); FileDao["remote"].storageType = EnumFileStorageType.RUSTFS
        val revision = FileObjectRevision("a".repeat(64), "fixed-version", "etag")
        `when`(access.readEvidence(path, "RUSTFS")).thenReturn(FileReadAccess.ReadEvidence(FileReadAccess.ReadLocation(path, true), FileContentBaseline.read(ByteArrayInputStream(bytes)), revision))
        val storage = mock(FileObjectStorage::class.java)
        `when`(storage.metadataAtRevision(path, "RUSTFS", revision)).thenReturn(FileObjectMetadata(path, EnumFileStorageType.RUSTFS, bytes.size.toLong(), revision = revision))
        `when`(storage.openRevision(path, "RUSTFS", revision)).thenReturn(ByteArrayInputStream(bytes))
        assertNotNull(CollectionPrintAttachmentResolver(storage, access, directory).resolve(print(CollectionPrintFieldBuilder.attachment("原件", "remote"))).single().imageBytes)
        verify(storage, never()).exists(anyString(), anyString())
        verify(storage, never()).openStream(anyString(), anyString(), nullable(Long::class.java), nullable(Long::class.java))
    }

    @Test fun `external urls and invalid references are not fetched or printed as paths`() = fixture { access, directory ->
        val storage = mock(FileObjectStorage::class.java)
        val result = CollectionPrintAttachmentResolver(storage, access, directory).resolve(print(
            CollectionPrintFieldVo("旧附件", "兼容正文", fileReferences = listOf("https://example.test/a.png?token=secret", "../secret", "unknown-id"))))
        assertEquals(3, result.size); assertTrue(result.all { it.name.isEmpty() && it.path.isEmpty() && it.imageBytes == null && it.status != null })
        verifyNoInteractions(storage, access, directory)
    }

    @Test fun `missing file and oversized image retain authorized file information with visible status`() = fixture { access, directory ->
        file("missing", "/missing.png", "png", 10); evidence(access, "/missing.png")
        file("large", "/large.png", "png", 10); evidence(access, "/large.png")
        val storage = mock(FileObjectStorage::class.java)
        `when`(storage.exists("/large.png", "LOCAL")).thenReturn(true)
        `when`(storage.metadata("/large.png", "LOCAL")).thenReturn(FileObjectMetadata("/large.png", EnumFileStorageType.LOCAL, CollectionPrintAttachmentResolver.MAX_IMAGE_BYTES.toLong() + 1))
        val result = CollectionPrintAttachmentResolver(storage, access, directory).resolve(print(CollectionPrintFieldBuilder.attachment("影像", "missing,large")))
        assertEquals(2, result.size); assertTrue(result.all { it.status != null && it.imageBytes == null && it.path.isNotEmpty() })
        verify(storage, never()).openStream(anyString(), anyString(), nullable(Long::class.java), nullable(Long::class.java))
    }
}
