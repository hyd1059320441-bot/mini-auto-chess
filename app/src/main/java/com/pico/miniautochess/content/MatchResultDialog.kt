package com.pico.miniautochess.content

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pico.miniautochess.rules.MatchResultPresentation
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.launch

/** Native spatial modal appears only on terminal victory or human elimination. */
@Composable
fun MatchResultDialog(controller: GameController) {
    val result = MatchResultPresentation.from(controller.ui) ?: return
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    PicoTheme {
        AlertDialog(
            modifier = Modifier.width(720.dp).semantics { contentDescription = "棋会终局：${result.title}" },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            onDismissRequest = {},
            title = {
                Text(result.title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = if (result.victory) ChessTheme.Brass else ChessTheme.Clay,
                    style = PicoTheme.typography.headlineLarge.copy(fontSize = 58.sp, lineHeight = 72.sp))
            },
            content = {
                Text(result.detail, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 40.sp))
            },
            buttons = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
                    Button(onClick = { controller.start() }, modifier = Modifier.weight(1f).height(72.dp)) {
                        Text("再来一局", style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 38.sp))
                    }
                    Button(onClick = {
                        controller.background()
                        scope.launch { navigator.closeStage() }
                    }, modifier = Modifier.weight(1f).height(72.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillSecondary)) {
                        Text("退出", style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 38.sp))
                    }
                }
            },
        )
    }
}
