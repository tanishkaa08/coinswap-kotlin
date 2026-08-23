package com.example.coinswapmobile.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.Surface
import com.example.coinswapmobile.ui.theme.TextPrimary
import com.example.coinswapmobile.ui.theme.TextSecondary
import com.example.coinswapmobile.ui.theme.TorActive
import com.example.coinswapmobile.ui.theme.TorInactive
import kotlinx.coroutines.launch

object OrbotHelper {
    const val ORBOT_PACKAGE = "org.torproject.android"
    private const val PLAY_STORE =
        "https://play.google.com/store/apps/details?id=$ORBOT_PACKAGE"
    private const val PLAY_STORE_MARKET = "market://details?id=$ORBOT_PACKAGE"
    private const val ACTION_START = "org.torproject.android.intent.action.START"
    private const val EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME"

    fun isOrbotInstalled(context: Context): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            true
        }.getOrDefault(false)

    fun openOrbotInstallPage(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_MARKET)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(market) }
            .recoverCatching { context.startActivity(web) }
    }

    fun openOrbotApp(context: Context) {
        requestOrbotStart(context)
        val launch = context.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } else {
            openOrbotInstallPage(context)
        }
    }

    /** Install if missing, otherwise start/open Orbot. */
    fun promptOrbot(context: Context) {
        if (isOrbotInstalled(context)) openOrbotApp(context)
        else openOrbotInstallPage(context)
    }

    fun requestOrbotStart(context: Context) {
        if (!isOrbotInstalled(context)) return
        val intent = Intent(ACTION_START).apply {
            setPackage(ORBOT_PACKAGE)
            putExtra(EXTRA_PACKAGE_NAME, context.packageName)
        }
        runCatching { context.sendBroadcast(intent) }
    }
}

@Composable
fun OrbotRequiredDialog(
    visible: Boolean,
    reason: String?,
    onDismiss: () -> Unit,
    onOpened: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!visible) return

    val installed = OrbotHelper.isOrbotInstalled(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text(
                if (installed) "Start Orbot" else "Download Orbot",
                color = TextPrimary,
            )
        },
        text = {
            Text(
                reason?.takeIf { it.isNotBlank() }
                    ?: if (installed) "Start Orbot with SocksPort 9050." else "Install Orbot to continue.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        OrbotHelper.promptOrbot(context)
                        onOpened?.invoke()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TorActive),
            ) {
                Text(
                    if (installed) "Start Orbot" else "Download Orbot",
                    color = Color.Black,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = TextSecondary)
            }
        },
    )
}

@Composable
fun OrbotInstallDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpened: (() -> Unit)? = null,
) {
    OrbotRequiredDialog(
        visible = visible,
        reason = null,
        onDismiss = onDismiss,
        onOpened = onOpened,
    )
}

/** Banner when SOCKS 9050 is down: download Orbot if missing, else start it. */
@Composable
fun TorPromptBanner(
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val installed = OrbotHelper.isOrbotInstalled(context)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (installed) "Orbot SOCKS not on 9050" else "Orbot required",
            color = TorInactive,
            style = MaterialTheme.typography.labelSmall,
        )
        Button(
            onClick = {
                OrbotHelper.promptOrbot(context)
                onAction?.invoke()
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TorActive),
        ) {
            Text(
                if (installed) "Start Orbot" else "Download Orbot",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
