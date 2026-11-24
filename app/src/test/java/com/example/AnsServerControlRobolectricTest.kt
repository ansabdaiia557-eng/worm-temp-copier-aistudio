package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.content.ContentRepository
import com.example.data.AppSettings
import com.example.data.OperationStatus
import com.example.data.TransferProgressState
import com.example.server.AuthSessionState
import com.example.server.InstalledServerFileMeta
import com.example.server.MaintenanceInfo
import com.example.server.PubgCopyInstallState
import com.example.server.ServerGateStatus
import com.example.server.SessionStore
import com.example.server.SupabaseClient
import com.example.shizuku.ShizukuStatus
import com.example.ui.components.AccountAuthAndSubscriptionDialog
import com.example.ui.components.MaintenanceBlockingOverlay
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.WormTempCopierTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnsServerControlRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sessionStore_persistsAuthSessionAndInstalledFileMeta() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SessionStore(context)
        store.clearSession()
        store.clearPubgInstallVerified()

        val session = AuthSessionState(
            accessToken = "jwt_access_token_123",
            refreshToken = "jwt_refresh_456",
            userId = "user-uuid-001",
            email = "player@ans.local",
            expiresAtEpochSec = 1999999999L
        )
        store.saveSession(session)
        val restored = store.getSession()
        assertTrue(restored.isLoggedIn)
        assertEquals("player@ans.local", restored.email)

        val meta = InstalledServerFileMeta(
            contentId = "skin_pack_1",
            versionCode = 4,
            versionName = "v4.0.0",
            sha256 = "abcdef1234567890",
            localCachePath = "/tmp/cache/skin.pak",
            installedDestPath = "/storage/emulated/0/Android/data/com.tencent.ig/files/puffer_temp/skin.pak",
            installedAtMillis = 1700000000000L,
            sizeBytes = 2048L
        )
        store.saveInstalledFile(meta)
        val installed = store.getInstalledFiles()
        assertEquals(4, installed["skin_pack_1"]?.versionCode)

        store.markPubgInstallVerified("PUBG_GLOBAL", 7)
        assertEquals("PUBG_GLOBAL", store.lastVerifiedPubgEditionFlow.first())

        store.clearSession()
        assertFalse(store.getSession().isLoggedIn)
    }

    @Test
    fun parseServerGateJson_handlesMaintenanceBanOutdatedAndInstantResume() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = SupabaseClient(context, SessionStore(context))

        val maintJson = JSONObject().apply {
            put("server_time", "2026-11-24T12:00:00Z")
            put("server_name", "ANS SERVER CONTROL")
            put("global_file_revision", 5)
            put("can_execute_copy", false)
            put("block_reason_code", "MAINTENANCE")
            put("block_reason_ar", "السيرفر في وضع الصيانة")
            put("block_reason_en", "Maintenance mode active")
            put("maintenance", JSONObject().apply {
                put("is_active", true)
                put("is_scheduled", true)
                put("reason_ar", "صيانة مجدولة")
                put("reason_en", "Scheduled maintenance")
                put("ends_at", "2026-11-24T14:00:00Z")
            })
        }

        val maintGate = client.parseServerGateJson(maintJson)
        assertTrue(maintGate.isReachable)
        assertTrue(maintGate.maintenance.isActive)
        assertTrue(maintGate.maintenance.isScheduled)
        assertFalse(maintGate.canExecuteCopy)
        assertEquals("MAINTENANCE", maintGate.blockReasonCode)
        assertEquals(5, maintGate.globalFileRevision)

        // Simulate Instant Resume unlocking gate with outdated file + mandatory update check
        val activeJson = JSONObject().apply {
            put("server_time", "2026-11-24T12:05:00Z")
            put("global_file_revision", 6)
            put("can_execute_copy", true)
            put("maintenance", JSONObject().apply {
                put("is_active", false)
                put("is_scheduled", false)
            })
            put("subscription", JSONObject().apply {
                put("required", true)
                put("is_active", true)
                put("plan_name", "VIP 30 Days")
            })
            put("telegram", JSONObject().apply {
                put("required", true)
                put("verified", true)
                put("channel_url", "https://t.me/Kharbash_anas")
            })
            put("outdated_file_ids", JSONArray().put("file_1"))
            put("files", JSONArray().put(JSONObject().apply {
                put("id", "file_1")
                put("title", "Tactical Pak")
                put("description", "Official asset")
                put("category", "SKINS")
                put("version", "v3.0.0")
                put("version_code", 3)
                put("size_bytes", 1024L)
                put("updated_at", "2026-11-24")
                put("target_filename", "tactical.pak")
                put("target_relative_path", "sub")
                put("sha256", "a".repeat(64))
                put("compatible_editions", JSONArray().put("PUBG_GLOBAL"))
                put("is_required", true)
                put("target_group", "vip")
            }))
        }

        val activeGate = client.parseServerGateJson(activeJson)
        assertFalse(activeGate.maintenance.isActive)
        assertTrue(activeGate.canExecuteCopy)
        assertEquals(listOf("file_1"), activeGate.outdatedFileIds)
        assertEquals(1, activeGate.files.size)
        assertEquals("vip", activeGate.files.first().targetGroup)
        assertEquals("sub", activeGate.files.first().targetRelativePath)
    }

    @Test
    fun verifyAndWriteStream_rejectsSha256OrSizeMismatchAndAcceptsValidFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = SupabaseClient(context, SessionStore(context))
        val payload = "VERIFIED_ANS_BINARY_PAYLOAD_2026".toByteArray(Charsets.UTF_8)
        val validSha = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }

        val tempDir = File(context.cacheDir, "dl_verify_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val badShaDest = File(tempDir, "bad_sha.pak")
            val failSha = client.verifyAndWriteStream(
                input = payload.inputStream(),
                totalBytesHint = payload.size.toLong(),
                destinationFile = badShaDest,
                expectedSha256 = "f".repeat(64),
                expectedSizeBytes = payload.size.toLong(),
                onProgress = {}
            )
            assertTrue("SHA-256 mismatch must reject binary", failSha.isFailure)
            assertFalse(badShaDest.exists())

            val badSizeDest = File(tempDir, "bad_size.pak")
            val failSize = client.verifyAndWriteStream(
                input = payload.inputStream(),
                totalBytesHint = payload.size.toLong(),
                destinationFile = badSizeDest,
                expectedSha256 = validSha,
                expectedSizeBytes = 999999L,
                onProgress = {}
            )
            assertTrue("Byte size mismatch must reject binary", failSize.isFailure)
            assertFalse(badSizeDest.exists())

            val goodDest = File(tempDir, "good.pak")
            val okResult = client.verifyAndWriteStream(
                input = payload.inputStream(),
                totalBytesHint = payload.size.toLong(),
                destinationFile = goodDest,
                expectedSha256 = validSha,
                expectedSizeBytes = payload.size.toLong(),
                onProgress = {}
            )
            assertTrue(okResult.isSuccess)
            assertEquals(validSha, okResult.getOrNull())
            assertTrue(goodDest.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun contentRepository_syncCatalogMarksNeedsUpdateFromOutdatedIds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SessionStore(context)
        val client = SupabaseClient(context, store)
        val repo = ContentRepository(context, client, store)

        val gate = client.parseServerGateJson(
            JSONObject().apply {
                put("outdated_file_ids", JSONArray().put("item_abc"))
                put("files", JSONArray().put(JSONObject().apply {
                    put("id", "item_abc")
                    put("title", "Pro Config")
                    put("description", "Server verified")
                    put("category", "AIM_SETTINGS")
                    put("version", "v2.0")
                    put("version_code", 2)
                    put("size_bytes", 512L)
                    put("updated_at", "2026-11-24")
                    put("target_filename", "cfg.ini")
                    put("sha256", "b".repeat(64))
                    put("compatible_editions", JSONArray().put("PUBG_GLOBAL"))
                    put("is_required", true)
                    put("target_group", "all")
                }))
            }
        )
        repo.syncCatalogFromServer(gate)
        val items = repo.itemsFlow.first()
        assertEquals(1, items.size)
        assertTrue(items.first().needsUpdate)
        assertEquals(2, items.first().versionCode)
    }

    @Test
    fun dashboardUi_locksLaunchPubgUntilInstallVerified_andRendersServerCard() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                WormTempCopierTheme(darkTheme = true, isRtl = false) {
                    DashboardScreen(
                        shizukuStatus = ShizukuStatus(),
                        selectedFiles = emptyList(),
                        settings = AppSettings(languageCode = "en"),
                        favoritePaths = emptyList(),
                        pathAccessStatus = com.example.data.PathAccessStatus.NEEDS_SHIZUKU,
                        transferState = TransferProgressState(),
                        onConnectShizuku = {},
                        onRefreshShizuku = {},
                        onPickFiles = {},
                        onGenerateTestFiles = {},
                        onClearSelection = {},
                        onPickDestinationFolder = {},
                        onSetManualDestination = {},
                        onResetDefaultPath = {},
                        onSelectGameEdition = {},
                        onToggleAutoDelete = {},
                        onSetRetentionMinutes = {},
                        onStartCopy = {},
                        onStartMove = {},
                        onCancelTransfer = {},
                        onNavigateToFiles = {},
                        onNavigateToSettings = {},
                        onToggleLanguage = {},
                        serverGateStatus = ServerGateStatus(isReachable = true, canExecuteCopy = true),
                        pubgInstallState = PubgCopyInstallState(canLaunchPubg = false)
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ans_server_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("pubg_auto_install_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_install_server_to_pubg").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_launch_pubg").assertIsNotEnabled()
    }

    @Test
    fun dashboardUi_blocksLaunchPubgWhenServerGateIsClosedEvenIfPreviouslyVerified() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                WormTempCopierTheme(darkTheme = true, isRtl = false) {
                    DashboardScreen(
                        shizukuStatus = ShizukuStatus(),
                        selectedFiles = emptyList(),
                        settings = AppSettings(languageCode = "en"),
                        favoritePaths = emptyList(),
                        pathAccessStatus = com.example.data.PathAccessStatus.NEEDS_SHIZUKU,
                        transferState = TransferProgressState(),
                        onConnectShizuku = {},
                        onRefreshShizuku = {},
                        onPickFiles = {},
                        onGenerateTestFiles = {},
                        onClearSelection = {},
                        onPickDestinationFolder = {},
                        onSetManualDestination = {},
                        onResetDefaultPath = {},
                        onSelectGameEdition = {},
                        onToggleAutoDelete = {},
                        onSetRetentionMinutes = {},
                        onStartCopy = {},
                        onStartMove = {},
                        onCancelTransfer = {},
                        onNavigateToFiles = {},
                        onNavigateToSettings = {},
                        onToggleLanguage = {},
                        serverGateStatus = ServerGateStatus(
                            isConfigured = true,
                            isReachable = true,
                            canExecuteCopy = false,
                            blockReasonEn = "Files update required"
                        ),
                        pubgInstallState = PubgCopyInstallState(canLaunchPubg = true)
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("pubg_auto_install_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_launch_pubg").assertIsNotEnabled()
    }

    @Test
    fun maintenanceOverlay_andAuthDialog_renderCorrectly() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                WormTempCopierTheme(darkTheme = true, isRtl = false) {
                    MaintenanceBlockingOverlay(
                        gateStatus = ServerGateStatus(
                            isReachable = true,
                            maintenance = MaintenanceInfo(
                                isActive = true,
                                isScheduled = true,
                                reasonEn = "Scheduled security upgrade"
                            )
                        ),
                        isChecking = false,
                        onRefreshNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maintenance_blocking_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_check_maintenance_status").assertIsDisplayed()
    }
}
