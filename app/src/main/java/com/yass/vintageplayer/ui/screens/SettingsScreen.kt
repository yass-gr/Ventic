package com.yass.vintageplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yass.vintageplayer.data.Accents
import com.yass.vintageplayer.ui.MainViewModel
import com.yass.vintageplayer.ui.UiState
import com.yass.vintageplayer.ui.components.BevelButton
import com.yass.vintageplayer.ui.components.LcdPill
import com.yass.vintageplayer.ui.components.Led
import com.yass.vintageplayer.ui.components.Module
import com.yass.vintageplayer.ui.components.SectionLabel
import com.yass.vintageplayer.ui.components.VintageIcons
import com.yass.vintageplayer.ui.theme.Barlow
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.ShareTechMono

@Composable
fun SettingsScreen(ui: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    val settings = ui.settings
    var folderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "SETTINGS",
                style = TextStyle(
                    color = tokens.label,
                    fontFamily = Barlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 4.sp,
                    shadow = tokens.labelShadow,
                ),
            )
        }

        SectionLabel("APPEARANCE", modifier = Modifier.padding(top = 4.dp))
        Module(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Theme row.
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(
                            "Theme",
                            style = TextStyle(
                                color = tokens.text,
                                fontFamily = Barlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            ),
                        )
                        BasicText(
                            "Chassis finish",
                            style = TextStyle(color = tokens.sub, fontFamily = Barlow, fontSize = 14.sp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeButton(
                            selected = settings.dark,
                            onClick = vm::setDark,
                            icon = VintageIcons.Moon,
                            label = "DARK",
                        )
                        ThemeButton(
                            selected = !settings.dark,
                            onClick = vm::setLight,
                            icon = VintageIcons.Sun,
                            label = "LIGHT",
                        )
                    }
                }
                Divider()
                // Accent row.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(
                            "Accent color",
                            style = TextStyle(
                                color = tokens.text,
                                fontFamily = Barlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            ),
                        )
                        BasicText(
                            "Lights the display, LEDs and dials",
                            style = TextStyle(color = tokens.sub, fontFamily = Barlow, fontSize = 14.sp),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for ((argb, name) in Accents.ALL) {
                            val selected = settings.accent == argb
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Swatch(
                                    color = Color(argb),
                                    selected = selected,
                                    label = "$name accent",
                                    onClick = { vm.setAccent(argb) },
                                )
                                BasicText(
                                    name,
                                    style = TextStyle(
                                        color = if (selected) tokens.accentText else tokens.sub,
                                        fontFamily = Barlow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.5.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionLabel("LIBRARY", modifier = Modifier.padding(top = 4.dp))
        Module(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Folder row.
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicText(
                            "Music folder",
                            style = TextStyle(
                                color = tokens.text,
                                fontFamily = Barlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            ),
                        )
                        FolderPill(
                            text = if (ui.isScanning) "SCANNING…" else settings.folder ?: "ALL AUDIO",
                            onClick = { folderDialog = true },
                        )
                    }
                    BevelButton(
                        onClick = vm::rescan,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(46.dp).padding(horizontal = 0.dp),
                    ) {
                        BasicText(
                            if (ui.isScanning) "SCANNING…" else "RESCAN",
                            style = TextStyle(
                                color = tokens.btnText,
                                fontFamily = Barlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.5.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                Divider()
                // Tracks-found row.
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText(
                        "Tracks found",
                        style = TextStyle(
                            color = tokens.text,
                            fontFamily = Barlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                    )
                    LcdPill("${ui.tracks.size}")
                }
            }
        }
    }

    if (folderDialog) {
        FolderDialog(
            folders = ui.folders,
            selected = settings.folder,
            onPick = { vm.pickFolder(it); folderDialog = false },
            onDismiss = { folderDialog = false },
        )
    }
}

@Composable
private fun Divider() {
    val tokens = LocalVintage.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.dividerHi))
    }
}

@Composable
private fun ThemeButton(selected: Boolean, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val tokens = LocalVintage.current
    BevelButton(
        onClick = onClick,
        on = selected,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(84.dp, 46.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Led(selected)
            Image(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                colorFilter = ColorFilter.tint(tokens.btnText),
            )
            BasicText(
                label,
                style = TextStyle(
                    color = tokens.btnText,
                    fontFamily = Barlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.5.sp,
                ),
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    val tokens = LocalVintage.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .then(
                if (selected) Modifier.border(2.dp, tokens.accentText, CircleShape)
                else Modifier,
            )
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(
                    if (selected) 8.dp else 2.dp,
                    CircleShape,
                    clip = false,
                    ambientColor = color,
                    spotColor = color,
                )
                .background(color, CircleShape)
                .border(1.dp, tokens.btnBorder, CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.RadioButton, onClick = onClick),
        )
    }
}

@Composable
private fun FolderPill(text: String, onClick: () -> Unit) {
    val tokens = LocalVintage.current
    Row(
        modifier = Modifier
            .shadow(2.dp, RoundedCornerShape(6.dp), clip = false)
            .background(tokens.lcdBrush, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            VintageIcons.Folder,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(tokens.lcdText),
        )
        BasicText(
            text,
            style = TextStyle(
                color = tokens.lcdText,
                fontFamily = ShareTechMono,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                shadow = tokens.lcdGlow,
            ),
            maxLines = 1,
        )
    }
}

/** In-app folder picker styled with Module/BevelButton. */
@Composable
private fun FolderDialog(
    folders: List<String>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Module(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("MUSIC FOLDER")
                FolderOption(
                    label = "ALL AUDIO",
                    selected = selected == null,
                    onClick = { onPick(null) },
                )
                for (folder in folders) {
                    FolderOption(
                        label = folder,
                        selected = selected == folder,
                        onClick = { onPick(folder) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalVintage.current
    BevelButton(
        onClick = onClick,
        on = selected,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Led(selected)
            BasicText(
                label,
                style = TextStyle(
                    color = tokens.btnText,
                    fontFamily = ShareTechMono,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                ),
                maxLines = 1,
            )
        }
    }
}
