package com.example

import com.example.data.DEFAULT_PUFFER_TEMP_PATH
import com.example.data.GameEditionKey
import com.example.data.PRESET_GAME_EDITIONS
import com.example.data.ScheduledDeletionEntity
import com.example.domain.FileOperationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(JUnit4::class)
class ExampleRobolectricTest {

    @Test
    fun defaultPufferTempPath_matchesSpecification() {
        assertEquals(
            "/storage/emulated/0/Android/data/com.tencent.ig/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp",
            DEFAULT_PUFFER_TEMP_PATH
        )
    }

    @Test
    fun gameEditions_verifyAll5Editions_andProposedStatus() {
        assertEquals(5, PRESET_GAME_EDITIONS.size)

        val global = PRESET_GAME_EDITIONS.first { it.key == GameEditionKey.PUBG_GLOBAL }
        assertEquals("com.tencent.ig", global.packageName)
        assertTrue(global.defaultPufferPath.contains("com.tencent.ig"))
        assertFalse(global.isProposedOnly)

        val korea = PRESET_GAME_EDITIONS.first { it.key == GameEditionKey.PUBG_KOREA }
        assertEquals("com.pubg.krmobile", korea.packageName)
        assertTrue(korea.defaultPufferPath.contains("com.pubg.krmobile"))
        assertFalse(korea.isProposedOnly)

        val vietnam = PRESET_GAME_EDITIONS.first { it.key == GameEditionKey.PUBG_VIETNAM }
        assertEquals("com.vng.pubgmobile", vietnam.packageName)
        assertTrue(vietnam.defaultPufferPath.contains("com.vng.pubgmobile"))
        assertFalse(vietnam.isProposedOnly)

        val taiwan = PRESET_GAME_EDITIONS.first { it.key == GameEditionKey.PUBG_TAIWAN }
        assertEquals("com.rekoo.pubgm", taiwan.packageName)
        assertTrue(taiwan.defaultPufferPath.contains("com.rekoo.pubgm"))
        assertFalse(taiwan.isProposedOnly)

        val flash = PRESET_GAME_EDITIONS.first { it.key == GameEditionKey.PUBG_FLASH }
        assertEquals("com.tencent.igfit", flash.packageName)
        assertTrue(flash.defaultPufferPath.contains("com.tencent.igfit"))
        assertTrue("مسار Flash يجب أن يُعامل كمسار مقترح حتى يتم التحقق منه", flash.isProposedOnly)
    }

    @Test
    fun autoDelete_safeDeleteTemporaryCopy_deletesOnlyTargetAndProtectsSource() {
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "test_autodelete_safe_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val targetCopyFile = File(tempDir, "temp_puffer_copy.pak").apply {
                writeBytes("DUMMY_PAK_CONTENT_FOR_SAFE_DELETION".toByteArray(Charsets.UTF_8))
            }
            val safeOtherFile = File(tempDir, "safe_unrelated.pak").apply {
                writeBytes("KEEP_THIS_FILE_SAFE".toByteArray(Charsets.UTF_8))
            }

            assertTrue(targetCopyFile.exists())
            assertTrue(safeOtherFile.exists())

            val targetHash = FileOperationEngine.computeSha256ForFile(targetCopyFile)
            assertNotNull(targetHash)

            // 1. اختبار الحذف الآمن مع SHA-256 مطابق
            val (deleted, err) = FileOperationEngine.safeDeleteDirectFile(
                target = targetCopyFile,
                expectedSha256 = targetHash
            )
            assertTrue(deleted)
            assertEquals(null, err)
            assertFalse("يجب حذف النسخة المؤقتة المستهدفة فقط", targetCopyFile.exists())
            assertTrue("الملفات الأخرى في المجلد يجب أن تظل محمية بالكامل دون أي مساس بها", safeOtherFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun autoDelete_safeDeleteTemporaryCopy_abortsIfSha256Mismatch() {
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "test_mismatch_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val modifiedFile = File(tempDir, "altered_temp.pak").apply {
                writeBytes("MODIFIED_CONTENT".toByteArray(Charsets.UTF_8))
            }

            // تمرير بصمة SHA-256 مختلفة
            val (deleted, err) = FileOperationEngine.safeDeleteDirectFile(
                target = modifiedFile,
                expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
            )
            assertFalse("يجب إيقاف الحذف فورًا إذا لم تتطابق بصمة SHA-256", deleted)
            assertTrue(modifiedFile.exists())
            assertNotNull(err)
            assertTrue(err!!.contains("SHA-256 mismatch", ignoreCase = true))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sha256Verification_computesAccurateHashForStreamAndFile() {
        val tempFile = File.createTempFile("sha_test_", ".txt").apply {
            writeText("Hello Worm Temp Copier 2026")
        }
        try {
            val hash1 = FileOperationEngine.computeSha256ForFile(tempFile)
            assertNotNull(hash1)
            assertEquals(64, hash1!!.length) // SHA-256 is 64 hex characters

            val hash2 = tempFile.inputStream().use { FileOperationEngine.computeSha256FromStream(it) }
            assertEquals("بصمة SHA-256 يجب أن تتطابق بين الملف والـ Stream", hash1, hash2)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun fileOperationEngine_fileSizeFormatting_verifiesAllScales() {
        assertEquals("0 B", FileOperationEngine.formatFileSize(0))
        assertEquals("512 B", FileOperationEngine.formatFileSize(512))
        assertEquals("1 KB", FileOperationEngine.formatFileSize(1024))
        assertEquals("1 MB", FileOperationEngine.formatFileSize(1024 * 1024))
        assertEquals("1.5 MB", FileOperationEngine.formatFileSize((1024 * 1024 * 1.5).toLong()))
        assertEquals("1 GB", FileOperationEngine.formatFileSize(1024L * 1024 * 1024))
    }

    @Test
    fun fileOperationEngine_pathAccessValidation_detectsAndroidDataPatterns() {
        assertTrue(FileOperationEngine.isRestrictedPath("/storage/emulated/0/Android/data/com.tencent.ig/files"))
        assertTrue(FileOperationEngine.isRestrictedPath("/storage/emulated/0/Android/obb/com.tencent.ig/main.obb"))
        assertFalse(FileOperationEngine.isRestrictedPath("/storage/emulated/0/Download/Paks"))
        assertFalse(FileOperationEngine.isRestrictedPath("/storage/emulated/0/Documents/test.pak"))
        assertFalse(FileOperationEngine.isRestrictedPath("/storage/emulated/0/Android/data/my.own.pkg/files", ownPackage = "my.own.pkg"))
    }

    @Test
    fun scheduledDeletionEntity_attributesAndCalculation() {
        val now = System.currentTimeMillis()
        val durationMinutes = 5
        val deleteAt = now + (durationMinutes * 60 * 1000L)

        val entity = ScheduledDeletionEntity(
            uniqueCopyId = "copy_uuid_abc_123",
            fileName = "game_patch.pak",
            destinationPath = "/storage/emulated/0/Android/data/com.tencent.ig/files/puffer_temp/game_patch.pak",
            fileSize = 4096L,
            expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            gameEditionName = "PUBG MOBILE GLOBAL",
            durationMinutes = durationMinutes,
            copiedAtMillis = now,
            deleteAtMillis = deleteAt,
            isDeleted = false,
            cancelled = false
        )

        assertEquals("copy_uuid_abc_123", entity.uniqueCopyId)
        assertEquals(5, entity.durationMinutes)
        assertTrue(entity.durationMinutes in 1..10)
        assertEquals(deleteAt, entity.deleteAtMillis)
        assertFalse(entity.isDeleted)
        assertFalse(entity.cancelled)
    }

    @Test
    fun fileOperations_copyAndMove_checksumVerification() {
        val tempSourceDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "worm_test_src_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempDestDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "worm_test_dest_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            val sourceFile = File(tempSourceDir, "test_pak_1.pak").apply {
                writeBytes("TEST_PAK_CONTENT_12345".toByteArray(Charsets.UTF_8))
            }
            val sourceHash = FileOperationEngine.computeSha256ForFile(sourceFile)
            assertNotNull(sourceHash)

            // 1. اختبار COPY
            val destinationFile = File(tempDestDir, sourceFile.name)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            val copiedHash = FileOperationEngine.computeSha256ForFile(destinationFile)
            assertEquals("بصمة SHA-256 للملف المنسوخ يجب أن تتطابق مع الأصل", sourceHash, copiedHash)
            assertTrue("الملف الأصلي يجب أن يبقى دون مساس أثناء النسخ", sourceFile.exists())
            assertTrue("الملف المنسوخ يجب أن يتواجد في الوجهة", destinationFile.exists())

            // 2. اختبار MOVE (النسخ أولًا، التحقق من التوافق، ثم حذف الأصل)
            val moveTargetFile = File(tempDestDir, "moved_pak.pak")
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(moveTargetFile).use { output ->
                    input.copyTo(output)
                }
            }
            val movedHash = FileOperationEngine.computeSha256ForFile(moveTargetFile)
            assertEquals(sourceHash, movedHash)
            // حذف الأصل بعد نجاح التحقق
            sourceFile.delete()
            assertFalse("الملف الأصلي يجب حذفه بعد نجاح التحقق في عملية MOVE", sourceFile.exists())
            assertTrue("الملف المنقول يجب أن يتواجد في الوجهة", moveTargetFile.exists())
        } finally {
            tempSourceDir.deleteRecursively()
            tempDestDir.deleteRecursively()
        }
    }
}
