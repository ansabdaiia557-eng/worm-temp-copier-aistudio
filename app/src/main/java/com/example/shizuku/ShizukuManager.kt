package com.example.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

data class ShizukuStatus(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val shizukuVersion: Int = -1,
    val uid: Int = -1,
    val shouldShowRationale: Boolean = false,
    val statusDetailMessage: String = ""
) {
    val isReadyForElevatedOps: Boolean
        get() = isBinderAlive && isPermissionGranted
}

data class ShellCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

class ShizukuManager(private val context: Context) {

    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        const val REQUEST_CODE_SHIZUKU_PERMISSION = 1001
    }

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                refreshStatus(
                    customMessage = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        "Shizuku permission granted"
                    } else {
                        "Shizuku permission denied by user"
                    }
                )
            }
        }

    fun registerListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
            // Safe guard if binder not initialized yet
        }
        refreshStatus()
    }

    fun unregisterListeners() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
        }
    }

    fun isShizukuAppInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun refreshStatus(customMessage: String? = null): ShizukuStatus {
        val installed = isShizukuAppInstalled()
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

        if (!binderAlive) {
            val newState = ShizukuStatus(
                isInstalled = installed,
                isBinderAlive = false,
                isPermissionGranted = false,
                statusDetailMessage = customMessage ?: if (installed) {
                    "Shizuku app installed, but service is not running"
                } else {
                    "Shizuku service is not active on this device"
                }
            )
            _status.value = newState
            return newState
        }

        return try {
            if (Shizuku.isPreV11()) {
                val preV11State = ShizukuStatus(
                    isInstalled = installed,
                    isBinderAlive = true,
                    isPermissionGranted = false,
                    statusDetailMessage = "Unsupported pre-v11 Shizuku version"
                )
                _status.value = preV11State
                preV11State
            } else {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                val rationale = try {
                    Shizuku.shouldShowRequestPermissionRationale()
                } catch (_: Throwable) {
                    false
                }
                val version = try {
                    Shizuku.getVersion()
                } catch (_: Throwable) {
                    -1
                }
                val uid = try {
                    Shizuku.getUid()
                } catch (_: Throwable) {
                    -1
                }
                val state = ShizukuStatus(
                    isInstalled = true,
                    isBinderAlive = true,
                    isPermissionGranted = granted,
                    shizukuVersion = version,
                    uid = uid,
                    shouldShowRationale = rationale,
                    statusDetailMessage = customMessage ?: if (granted) {
                        "Connected (v$version, UID $uid)"
                    } else {
                        "Service running • Permission required"
                    }
                )
                _status.value = state
                state
            }
        } catch (t: Throwable) {
            val fallback = ShizukuStatus(
                isInstalled = installed,
                isBinderAlive = false,
                isPermissionGranted = false,
                statusDetailMessage = t.localizedMessage ?: "Failed to query Shizuku binder"
            )
            _status.value = fallback
            fallback
        }
    }

    /**
     * Requests Shizuku permission or opens the Shizuku Manager app if installed.
     * Returns a user-readable status message.
     */
    fun connectOrRequestPermission(): String {
        val current = refreshStatus()
        if (!current.isBinderAlive) {
            if (current.isInstalled) {
                try {
                    val launchIntent = context.packageManager
                        .getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        return "Opened Shizuku Manager. Start the service and return to Worm Temp Copier."
                    }
                } catch (_: Throwable) {
                }
            }
            return "Shizuku service is not running. Start Shizuku via Wireless Debugging / ADB first."
        }

        if (current.isPermissionGranted) {
            return "Shizuku is already connected and permission is granted."
        }

        return try {
            if (Shizuku.isPreV11()) {
                "Shizuku version is too old (pre-v11)."
            } else {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
                "Requested Shizuku runtime permission..."
            }
        } catch (t: Throwable) {
            "Could not request Shizuku permission: ${t.localizedMessage}"
        }
    }

    /**
     * Escapes a path for safe single-quoted POSIX shell usage.
     */
    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    private suspend fun executeShellCommand(command: String): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val current = refreshStatus()
            if (!current.isReadyForElevatedOps) {
                return@withContext ShellCommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Shizuku not connected or permission not granted"
                )
            }

            try {
                // Invoke Shizuku.newProcess via reflection to avoid visibility/linker constraints
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", command),
                    null,
                    null
                ) as ShizukuRemoteProcess

                val stdoutBuilder = StringBuilder()
                val stderrBuilder = StringBuilder()

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stdoutBuilder.appendLine(line)
                    }
                }

                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stderrBuilder.appendLine(line)
                    }
                }

                val exitCode = process.waitFor()
                process.destroy()

                ShellCommandResult(
                    exitCode = exitCode,
                    stdout = stdoutBuilder.toString().trim(),
                    stderr = stderrBuilder.toString().trim()
                )
            } catch (t: Throwable) {
                ShellCommandResult(
                    exitCode = -2,
                    stdout = "",
                    stderr = t.localizedMessage ?: "Shizuku shell execution error"
                )
            }
        }

    suspend fun ensureDirectoryExistsViaShizuku(dirPath: String): Boolean {
        val escaped = escapeShellArg(dirPath)
        val res = executeShellCommand("mkdir -p $escaped && test -d $escaped")
        return res.isSuccess
    }

    suspend fun fileExistsViaShizuku(filePath: String): Boolean {
        val escaped = escapeShellArg(filePath)
        val res = executeShellCommand("test -e $escaped")
        return res.isSuccess
    }

    suspend fun directoryExistsViaShizuku(dirPath: String): Boolean {
        val escaped = escapeShellArg(dirPath)
        val res = executeShellCommand("test -d $escaped")
        return res.isSuccess
    }

    suspend fun copyFileViaShizuku(sourcePath: String, targetPath: String): ShellCommandResult {
        val srcEscaped = escapeShellArg(sourcePath)
        val dstEscaped = escapeShellArg(targetPath)
        val parentDir = targetPath.substringBeforeLast('/', "")
        val mkdirCmd = if (parentDir.isNotEmpty()) {
            "mkdir -p ${escapeShellArg(parentDir)} && "
        } else {
            ""
        }
        return executeShellCommand("${mkdirCmd}cp -f $srcEscaped $dstEscaped")
    }

    suspend fun moveFileViaShizuku(sourcePath: String, targetPath: String): ShellCommandResult {
        val srcEscaped = escapeShellArg(sourcePath)
        val dstEscaped = escapeShellArg(targetPath)
        val parentDir = targetPath.substringBeforeLast('/', "")
        val mkdirCmd = if (parentDir.isNotEmpty()) {
            "mkdir -p ${escapeShellArg(parentDir)} && "
        } else {
            ""
        }
        return executeShellCommand("${mkdirCmd}mv -f $srcEscaped $dstEscaped")
    }

    suspend fun deleteFileViaShizuku(filePath: String): ShellCommandResult {
        val escaped = escapeShellArg(filePath)
        return executeShellCommand("rm -f $escaped")
    }

    suspend fun computeSha256ViaShizuku(filePath: String): String? {
        val escaped = escapeShellArg(filePath)
        val res = executeShellCommand("sha256sum -b $escaped")
        if (!res.isSuccess || res.stdout.isBlank()) return null
        return res.stdout.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
    }
}
