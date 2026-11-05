package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DEFAULT_PUFFER_TEMP_PATH
import com.example.data.OperationStatus
import com.example.data.OperationType
import com.example.data.PathAccessStatus
import com.example.data.SavedPathEntity
import com.example.domain.FileOperationEngine
import com.example.shizuku.ShizukuManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    @Test
    fun defaultPufferTempPath_matchesSpecification() {
        assertEquals(
            "/storage/emulated/0/Android/data/com.tencent.ig/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp",
            DEFAULT_PUFFER_TEMP_PATH
        )
    }

    @Test
    fun fileOperationEngine_copiesMultipleFiles_verifiesSha256Checksum_andPreservesOriginals() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shizukuManager = ShizukuManager(context)
        val engine = FileOperationEngine(context, shizukuManager)

        // 1. إنشاء عينة ملفات متعددة دفعة واحدة
        val sampleFiles = engine.createSampleTestFiles()
        assertTrue("يجب توليد ملفات متعددة للتحقق", sampleFiles.size >= 3)

        val sandboxDest = engine.getLocalTestDestinationPath()

        // 2. اختبار COPY لكل ملف والتحقق من بصمة SHA-256
        for (fileItem in sampleFiles) {
            val sourceHash = engine.computeSha256ForItem(fileItem)
            assertNotNull("حساب SHA-256 للملف الأصلي", sourceHash)

            val (copyResult, _) = engine.transferSingleFile(
                item = fileItem,
                destinationPath = sandboxDest,
                destinationTreeUri = null,
                operationType = OperationType.COPY,
                verifySha256 = true,
                autoUseShizuku = false,
                skipIfExists = false,
                onVerifyingHash = {}
            )

            assertEquals(OperationStatus.SUCCESS, copyResult.status)
            assertTrue(copyResult.sha256Verified)
            assertEquals(sourceHash, copyResult.targetHash)

            // التأكد أن الملف الأصلي لم يُحذف أثناء النسخ
            assertTrue("الملف الأصلي يجب أن يبقى دون حذف", File(fileItem.localFilePath!!).exists())
            // التأكد من وجود الملف في الوجهة
            val copiedFile = File(sandboxDest, fileItem.name)
            assertTrue("الملف المنسوخ يجب أن يتواجد في الوجهة", copiedFile.exists())
        }
    }

    @Test
    fun fileOperationEngine_testsMoveOperation_andSafeDeletionOfSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shizukuManager = ShizukuManager(context)
        val engine = FileOperationEngine(context, shizukuManager)

        val sampleFiles = engine.createSampleTestFiles()
        val targetMoveItem = sampleFiles.first()
        val originalFile = File(targetMoveItem.localFilePath!!)
        assertTrue(originalFile.exists())

        val moveSandboxDest = File(context.filesDir, "move_test_sandbox").apply { mkdirs() }.absolutePath

        val (moveResult, _) = engine.transferSingleFile(
            item = targetMoveItem,
            destinationPath = moveSandboxDest,
            destinationTreeUri = null,
            operationType = OperationType.MOVE,
            verifySha256 = true,
            autoUseShizuku = false,
            skipIfExists = false,
            onVerifyingHash = {}
        )

        assertEquals(OperationStatus.SUCCESS, moveResult.status)
        assertTrue(moveResult.sha256Verified)

        // التأكد من وجود الملف في الوجهة وحذف الملف الأصلي بأمان فقط بعد التحقق
        val movedFile = File(moveSandboxDest, targetMoveItem.name)
        assertTrue(movedFile.exists())
        assertFalse("يجب حذف الملف الأصلي بعد نجاح عملية MOVE والتحقق من SHA-256", originalFile.exists())
    }

    @Test
    fun fileOperationEngine_preventsSelfCopy_andHandlesExistingFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shizukuManager = ShizukuManager(context)
        val engine = FileOperationEngine(context, shizukuManager)

        val sampleFiles = engine.createSampleTestFiles()
        val testFile = sampleFiles.first()

        // 1. منع نسخ الملف إلى نفسه
        val sourceParentDir = File(testFile.localFilePath!!).parentFile!!.absolutePath
        val (selfCopyResult, _) = engine.transferSingleFile(
            item = testFile,
            destinationPath = sourceParentDir,
            destinationTreeUri = null,
            operationType = OperationType.COPY,
            verifySha256 = true,
            autoUseShizuku = false,
            skipIfExists = false,
            onVerifyingHash = {}
        )
        assertEquals(OperationStatus.FAILED, selfCopyResult.status)
        assertTrue(selfCopyResult.message.contains("itself", ignoreCase = true))

        // 2. كشف الملفات الموجودة مسبقًا وتخطيها عند طلب skipIfExists
        val sandboxDest = engine.getLocalTestDestinationPath()
        // انسخه أول مرة
        engine.transferSingleFile(
            item = testFile,
            destinationPath = sandboxDest,
            destinationTreeUri = null,
            operationType = OperationType.COPY,
            verifySha256 = false,
            autoUseShizuku = false,
            skipIfExists = false,
            onVerifyingHash = {}
        )

        // فحص وجود الملف في الوجهة
        val existingNames = engine.findExistingDestinationFiles(
            files = listOf(testFile),
            destinationPath = sandboxDest,
            destinationTreeUri = null,
            useShizuku = false
        )
        assertTrue(existingNames.contains(testFile.name))

        // محاولة النسخ مع خيار التخطي (skipIfExists = true)
        val (skipResult, _) = engine.transferSingleFile(
            item = testFile,
            destinationPath = sandboxDest,
            destinationTreeUri = null,
            operationType = OperationType.COPY,
            verifySha256 = true,
            autoUseShizuku = false,
            skipIfExists = true,
            onVerifyingHash = {}
        )
        assertEquals(OperationStatus.SKIPPED, skipResult.status)
    }

    @Test
    fun shizukuIntegration_testsStatusEvaluation_andRestrictedPathDetection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shizukuManager = ShizukuManager(context)
        val engine = FileOperationEngine(context, shizukuManager)

        // 1. فحص كشف مسارات Android/data المحمية
        assertTrue(engine.isRestrictedAndroidDataPath(DEFAULT_PUFFER_TEMP_PATH))
        assertTrue(engine.isRestrictedAndroidDataPath("/storage/emulated/0/Android/data/com.example.other/"))
        assertFalse(engine.isRestrictedAndroidDataPath("/storage/emulated/0/Download/MyFolder"))
        assertFalse(engine.isRestrictedAndroidDataPath(context.packageName.let { "/storage/emulated/0/Android/data/$it/files" }))

        // 2. فحص حالة Shizuku عند عدم تشغيل الخدمة
        val status = shizukuManager.refreshStatus()
        assertFalse("الخدمة غير مفعّلة في بيئة الاختبار بدون خدمة خارجية", status.isBinderAlive)
        assertFalse("الصلاحية غير ممنوحة عند توقف الخدمة", status.isPermissionGranted)
        assertFalse("isReadyForElevatedOps يجب أن تكون false", status.isReadyForElevatedOps)

        // 3. فحص كشف المسارات المخصصة والمحمية
        val accessStatus = engine.checkPathAccessStatus(DEFAULT_PUFFER_TEMP_PATH)
        assertEquals(PathAccessStatus.NEEDS_SHIZUKU, accessStatus)

        val sandboxStatus = engine.checkPathAccessStatus(engine.getLocalTestDestinationPath())
        assertEquals(PathAccessStatus.DIRECT_WRITABLE, sandboxStatus)
    }
}
