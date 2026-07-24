package com.example.coinswapmobile.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

object OrbotHelper {
    const val ORBOT_PACKAGE = "org.torproject.android"
    private const val TORPROJECT_DOWNLOADS = "https://www.torproject.org/download/"

    fun isOrbotInstalled(context: Context): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            true
        }.getOrDefault(false)

    fun openOrbotInstallPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TORPROJECT_DOWNLOADS)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openOrbotApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } else {
            openOrbotInstallPage(context)
        }
    }
}

@Composable
fun OrbotInstallDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onInstalledCheck: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text("Install Orbot", color = TextPrimary)
        },
        confirmButton = {
            Button(
                onClick = {
                    OrbotHelper.openOrbotInstallPage(context)
                    onDismiss()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TorActive),
            ) {
                Text("Install", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onInstalledCheck?.invoke()
            }) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
fun OrbotPromptBanner(
    modifier: Modifier = Modifier,
    onInstallClick: () -> Unit,
) {
    Text(
        "Tor unreachable",
        color = TorInactive,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onInstallClick)
            .padding(10.dp),
    )
}
