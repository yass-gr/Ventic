package com.yass.vintageplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yass.vintageplayer.ui.components.BevelButton
import com.yass.vintageplayer.ui.components.LcdPanel
import com.yass.vintageplayer.ui.theme.Barlow
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.Orbitron
import com.yass.vintageplayer.ui.theme.ShareTechMono

/**
 * Permission gate: vintage LCD card with a message and a GRANT ACCESS
 * bevel button. Shown instead of the app until audio permission is granted.
 */
@Composable
fun PermissionGate(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        LcdPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(
                    "NO STATIC WITHOUT VINYL",
                    style = TextStyle(
                        color = tokens.lcdText,
                        fontFamily = Orbitron,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                        shadow = tokens.lcdGlow,
                        textAlign = TextAlign.Center,
                    ),
                )
                BasicText(
                    "ALLOW ACCESS TO THE AUDIO FILES ON THIS DEVICE TO FILL THE JUKEBOX. NOTHING EVER LEAVES YOUR PHONE.",
                    style = TextStyle(
                        color = tokens.lcdText,
                        fontFamily = ShareTechMono,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
                BevelButton(
                    onClick = onGrant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    BasicText(
                        "GRANT ACCESS",
                        style = TextStyle(
                            color = tokens.btnText,
                            fontFamily = Barlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 2.sp,
                        ),
                    )
                }
            }
        }
    }
}
