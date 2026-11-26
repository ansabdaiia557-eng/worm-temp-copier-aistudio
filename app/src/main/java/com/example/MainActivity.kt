package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.WormCopierViewModel
import com.example.ui.components.AccountAuthAndSubscriptionDialog
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.MaintenanceBlockingOverlay
import com.example.ui.components.SplashScreenOverlay
import com.example.ui.components.TelegramGateDialog
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UsageScreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSurface
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WormTempCopierTheme
import kotlinx.coroutines.delay

enum class AppDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", Icons.Filled.Home, Icons.Outlined.Home),
    USAGE("usage", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    // On-device signature self-check. Fails open if the signature cannot be read.
    @Suppress("DEPRECATION")
    private fun integritySelfCheck(): Boolean {
        return try {
            val sigs = if (android.os.Build.VERSION.SDK_INT >= 28) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
            }
            val first = sigs?.firstOrNull() ?: return true
            val got = java.security.MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
                .joinToString("") { "%02x".format(it) }
            got == EXPECTED_SIG
        } catch (_: Throwable) { true }
    }

    private companion object {
        private const val EXPECTED_SIG = "631e2c62058b87f8f63c24d93ab217a051500c9dd69d919468b37484195ed9e4"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!integritySelfCheck()) {
            android.widget.Toast.makeText(this, "نسخة غير رسمية. نزّل النسخة الرسمية من القناة.", android.widget.Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }
        enableEdgeToEdge()
        setContent {
            WormCopierApp()
        }
    }
}

@Composable
fun WormCopierApp(
    viewModel: WormCopierViewModel = viewModel(),
    skipSplashForTests: Boolean = false
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val historyList by viewModel.operationHistory.collectAsStateWithLifecycle()
    val scheduledDeletions by viewModel.scheduledDeletions.collectAsStateWithLifecycle()
    val usageSessions by viewModel.usageSessions.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val isArabic = settings.languageCode == "ar"
    val appStrings = if (isArabic) ArabicStrings else EnglishStrings

    var showSplash by rememberSaveable { mutableStateOf(!skipSplashForTests) }
    var currentTab by rememberSaveable { mutableStateOf(AppDestination.HOME) }

    val telegramGateCompleted by viewModel.telegramGateCompleted.collectAsStateWithLifecycle()
    val authSession by viewModel.authSession.collectAsStateWithLifecycle()
    val serverGateStatus by viewModel.serverGateStatus.collectAsStateWithLifecycle()
    val pubgInstallState by viewModel.pubgInstallState.collectAsStateWithLifecycle()
    val isGateChecking by viewModel.isGateChecking.collectAsStateWithLifecycle()
    val isAuthBusy by viewModel.isAuthBusy.collectAsStateWithLifecycle()
    val showAccountSheet by viewModel.showAccountSheet.collectAsStateWithLifecycle()
    val dismissedOptionalUpdateCode by viewModel.dismissedOptionalUpdateCode.collectAsStateWithLifecycle()

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or dismissed */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(1100L)
            showSplash = false
        }
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(3800L)
            viewModel.dismissBanner()
        }
    }

    BackHandler(enabled = currentTab != AppDestination.HOME) {
        currentTab = AppDestination.HOME
    }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        WormTempCopierTheme(
            darkTheme = settings.darkMode,
            isRtl = isArabic
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    bottomBar = {
                        NavigationBar(
                            containerColor = androidx.compose.ui.graphics.Color(0x331B1440),
                            tonalElevation = 0.dp,
                            modifier = Modifier.testTag("bottom_navigation_bar")
                        ) {
                            AppDestination.entries.forEach { dest ->
                                val isSelected = currentTab == dest
                                val labelText = when (dest) {
                                    AppDestination.HOME -> appStrings.navHome
                                    AppDestination.USAGE -> appStrings.navUsage
                                    AppDestination.SETTINGS -> appStrings.navSettings
                                }
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = dest },
                                    modifier = Modifier.testTag("nav_tab_${dest.route}"),
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = labelText
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = labelText,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ObsidianDark,
                                        selectedTextColor = NeonGreen,
                                        indicatorColor = NeonGreen,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(220),
                            label = "screen_crossfade"
                        ) { destination ->
                            when (destination) {
                                AppDestination.HOME -> HomeScreen(
                                    shizukuStatus = shizukuStatus,
                                    selectedEditionKey = settings.selectedGameEdition,
                                    serverGateStatus = serverGateStatus,
                                    authSession = authSession,
                                    installState = pubgInstallState,
                                    isGateChecking = isGateChecking,
                                    operationHistory = historyList,
                                    lastDurationMs = usageSessions.firstOrNull()?.durationMs,
                                    autoDeleteEnabled = settings.autoDeleteEnabled,
                                    retentionMinutes = settings.defaultRetentionMinutes,
                                    pendingDeletions = scheduledDeletions.size,
                                    onSetAutoDelete = { enabled, minutes ->
                                        viewModel.setAutoDeleteEnabled(enabled)
                                        if (enabled) viewModel.setDefaultRetentionMinutes(minutes)
                                    },
                                    onConnectShizuku = viewModel::connectOrRequestShizukuPermission,
                                    onRefreshShizuku = viewModel::refreshShizuku,
                                    onSelectEdition = viewModel::selectGameEdition,
                                    onStartInstall = { viewModel.startServerPubgInstall() },
                                    onOpenAccountSheet = viewModel::openAccountSheet,
                                    onRefreshServerGate = { viewModel.refreshServerGate(silent = false) },
                                    onLaunchPubg = viewModel::launchPubg
                                )

                                AppDestination.USAGE -> UsageScreen(
                                    sessions = usageSessions,
                                    onClear = viewModel::clearHistoryAndLogs
                                )

                                AppDestination.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    authSession = authSession,
                                    serverGateStatus = serverGateStatus,
                                    isGateChecking = isGateChecking,
                                    onLanguageChange = viewModel::setLanguage,
                                    onCheckUpdates = { viewModel.refreshServerGate(silent = false) },
                                    onOpenAccountSheet = viewModel::openAccountSheet,
                                    onSignOut = viewModel::signOut
                                )
                            }
                        }

                        // Floating feedback toast banner
                        AnimatedVisibility(
                            visible = bannerMessage != null,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            bannerMessage?.let { (msg, isError) ->
                                val tint = if (isError) ErrorRed else NeonGreen
                                val bg = if (isError) ErrorRedSurface else NeonGreenSurface
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.dismissBanner() }
                                        .testTag("status_banner"),
                                    color = bg,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, tint),
                                    shadowElevation = 8.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Animated Opening Splash Screen
                SplashScreenOverlay(visible = showSplash)

                // 1. Authoritative Server Maintenance Blocking Overlay (highest priority after splash)
                if (!showSplash && serverGateStatus.isConfigured && serverGateStatus.maintenance.isActive) {
                    MaintenanceBlockingOverlay(
                        gateStatus = serverGateStatus,
                        isChecking = isGateChecking,
                        onRefreshNow = { viewModel.refreshServerGate(silent = false) }
                    )
                }

                // 1b. Access denied screen (banned / wrong device / tampered / no subscription)
                if (!showSplash && serverGateStatus.isConfigured && !serverGateStatus.maintenance.isActive &&
                    serverGateStatus.blockReasonCode in com.example.ui.components.ACCESS_DENIED_CODES &&
                    !showAccountSheet
                ) {
                    com.example.ui.components.AccessDeniedOverlay(
                        gateStatus = serverGateStatus,
                        onOpenAccount = viewModel::openAccountSheet
                    )
                }

                // 2. App Update Dialog (Mandatory or Dismissible Optional)
                val appUpdate = serverGateStatus.appUpdate
                if (!showSplash &&
                    !serverGateStatus.maintenance.isActive &&
                    appUpdate != null &&
                    (appUpdate.isMandatory || appUpdate.versionCode != dismissedOptionalUpdateCode)
                ) {
                    AppUpdateDialog(
                        updateInfo = appUpdate,
                        onDismissOptional = { viewModel.dismissOptionalAppUpdate(appUpdate.versionCode) }
                    )
                }

                // 3. Account Auth, Ban Notice & Subscription Activation Dialog
                if (!showSplash && showAccountSheet) {
                    AccountAuthAndSubscriptionDialog(
                        authSession = authSession,
                        gateStatus = serverGateStatus,
                        isBusy = isAuthBusy,
                        onCodeLogin = viewModel::signInWithCode,
                        onSignOut = viewModel::signOut,
                        onRedeemCode = viewModel::redeemActivationCode,
                        onDismiss = viewModel::closeAccountSheet,
                        bannerMessage = bannerMessage
                    )
                }

                // 4. Telegram Channel Welcome & Server Verification Gate
                val needsServerTelegramVerify = serverGateStatus.isReachable &&
                    serverGateStatus.user != null &&
                    serverGateStatus.telegram.required &&
                    !serverGateStatus.telegram.verified
                if (!showSplash &&
                    !showAccountSheet &&
                    !serverGateStatus.maintenance.isActive &&
                    (!telegramGateCompleted || needsServerTelegramVerify)
                ) {
                    TelegramGateDialog(
                        channelUrl = serverGateStatus.telegram.channelUrl,
                        showServerVerification = serverGateStatus.isReachable && serverGateStatus.user != null,
                        isVerifiedByServer = serverGateStatus.telegram.verified,
                        isBusy = isAuthBusy,
                        onVerifyMembership = viewModel::verifyTelegramMembership,
                        onContinue = viewModel::completeTelegramGate,
                        bannerMessage = bannerMessage
                    )
                }
            }
        }
    }
}
