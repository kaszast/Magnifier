package com.example

import android.graphics.Bitmap
import androidx.camera.core.CameraInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Az élő nézet és a kimerevített kép fölötti overlay-komponensek és az alsó kártya vezérlősorai.
// Az állapotot a MagnifierMainScreen tartja; ezek csak megjelenítenek és hoistolt lambdákkal jeleznek.

// Picture-in-Picture kicsinyített nézet a digitális zoom kivágásának mutatásához
@Composable
fun ZoomMinimap(
    isFrozen: Boolean,
    frozenBitmap: Bitmap?,
    liveThumbnailBitmap: Bitmap?,
    frozenScale: Float,
    extraDigitalZoom: Float,
    frozenOffset: Offset,
    extraDigitalPan: Offset,
    viewportSize: IntSize,
    combinedColorFilter: ColorFilter,
    liveColorFilter: ColorFilter,
    themeColor: Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xE60D0C11)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier
            .width(110.dp)
            .height(170.dp)
            .border(1.5.dp, themeColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Render the un-zoomed source (either frozen bitmap or live preview periodic thumbnail)
            if (isFrozen) {
                frozenBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_minimap_frozen),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = combinedColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                liveThumbnailBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_minimap_live),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = liveColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = themeColor,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // 2. Overlay view bounds highlighted box with custom drawing canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                val scale = if (isFrozen) frozenScale else extraDigitalZoom
                val pan = if (isFrozen) frozenOffset else extraDigitalPan

                val wWidth = viewportSize.width.toFloat().coerceAtLeast(1f)
                val wHeight = viewportSize.height.toFloat().coerceAtLeast(1f)

                val boxWidthFraction = 1f / scale
                val boxHeightFraction = 1f / scale

                val centerXFraction = 0.5f - (pan.x / (scale * wWidth))
                val centerYFraction = 0.5f - (pan.y / (scale * wHeight))

                val rectW = canvasW * boxWidthFraction
                val rectH = canvasH * boxHeightFraction

                val rectX = ((canvasW * centerXFraction) - (rectW / 2f)).coerceIn(0f, canvasW - rectW)
                val rectY = ((canvasH * centerYFraction) - (rectH / 2f)).coerceIn(0f, canvasH - rectH)

                // Draw dimmed non-visible outer region
                // Top
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(canvasW, rectY)
                )
                // Bottom
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(0f, rectY + rectH),
                    size = androidx.compose.ui.geometry.Size(canvasW, canvasH - (rectY + rectH))
                )
                // Left
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(0f, rectY),
                    size = androidx.compose.ui.geometry.Size(rectX, rectH)
                )
                // Right
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(rectX + rectW, rectY),
                    size = androidx.compose.ui.geometry.Size(canvasW - (rectX + rectW), rectH)
                )

                // Draw glowing neon viewport border
                drawRect(
                    color = themeColor,
                    topLeft = Offset(rectX, rectY),
                    size = androidx.compose.ui.geometry.Size(rectW, rectH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

// Bal-felső lebegő vezérlők: teljes képernyő (kezelőszervek) váltó + kamera-indikátor/váltó
@Composable
fun TopLeftControls(
    themeColor: Color,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    isFrozen: Boolean,
    availableCameras: List<CameraInfo>,
    selectedCameraIndex: Int,
    onSwapCamera: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Floating Controls Visibility Toggle Button (Full Screen)
        Box(
            modifier = Modifier
                .background(Color(0xFF09090B).copy(alpha = 0.75f), CircleShape)
                .border(1.5.dp, themeColor.copy(alpha = 0.6f), CircleShape)
                .clickable { onToggleControls() }
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (controlsVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(R.string.cd_toggle_controls),
                tint = themeColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // Floating Camera Indicator and Swap Button (in a separated box)
        if (!isFrozen && availableCameras.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF09090B).copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .border(1.5.dp, themeColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .clickable { onSwapCamera() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwitchCamera,
                        contentDescription = stringResource(R.string.cd_switch_camera),
                        tint = themeColor,
                        modifier = Modifier.size(18.dp)
                    )

                    val activeCameraInfo = availableCameras.getOrNull(selectedCameraIndex)
                    val cameraIcon = if (activeCameraInfo != null) {
                        when (activeCameraInfo.lensFacing) {
                            0 -> Icons.Default.Person // selfie/front
                            1 -> Icons.Default.PhotoCamera // back
                            else -> Icons.Default.Videocam // external
                        }
                    } else {
                        Icons.Default.PhotoCamera
                    }

                    val cameraLabel = if (activeCameraInfo != null) {
                        when (activeCameraInfo.lensFacing) {
                            0 -> stringResource(R.string.camera_front)
                            1 -> {
                                val backCameras = availableCameras.filter { it.lensFacing == 1 }
                                if (backCameras.size > 1) {
                                    val idx = backCameras.indexOf(activeCameraInfo)
                                    stringResource(R.string.camera_back_n, idx + 1)
                                } else {
                                    stringResource(R.string.camera_back)
                                }
                            }
                            else -> stringResource(R.string.camera_external)
                        }
                    } else {
                        stringResource(R.string.camera_generic)
                    }

                    Icon(
                        imageVector = cameraIcon,
                        contentDescription = cameraLabel,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Fő akciógombsor: zseblámpa, mentés, kimerevítés/folytatás (hero), megosztás.
// A tényleges műveletek a MagnifierMainScreen lambdáiban élnek, ahol az összes állapot elérhető.
@Composable
fun ActionButtonsRow(
    themeColor: Color,
    torchEnabled: Boolean,
    isFrozen: Boolean,
    onToggleTorch: () -> Unit,
    onSave: () -> Unit,
    onToggleFreeze: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Torch Button (compact circular glass button)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (torchEnabled) themeColor else Color(0xFF1F1E26),
                    CircleShape
                )
                .border(
                    1.dp,
                    if (torchEnabled) themeColor else Color(0xFF2E2C33),
                    CircleShape
                )
                .clickable { onToggleTorch() }
                .testTag("torch_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = stringResource(R.string.cd_torch),
                tint = if (torchEnabled) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Save Button (compact circular glass button)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF1F1E26), CircleShape)
                .border(1.dp, Color(0xFF2E2C33), CircleShape)
                .clickable { onSave() }
                .testTag("save_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = stringResource(R.string.cd_save),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Hero Freeze/Resume Shutter Button (Dual-ring camera shutter style)
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(3.dp, if (isFrozen) Color(0xFFEF4444) else themeColor, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isFrozen) Color(0xFFEF4444) else themeColor,
                        CircleShape
                    )
                    .clickable { onToggleFreeze() }
                    .testTag("freeze_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(if (isFrozen) R.string.cd_resume else R.string.cd_freeze),
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Share Button (compact circular glass button)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF1F1E26), CircleShape)
                .border(1.dp, Color(0xFF2E2C33), CircleShape)
                .clickable { onShare() }
                .testTag("share_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.cd_share),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Alsó szegmentált navigációs tab-sor (nagyítás / szűrők / korrekció / téma)
@Composable
fun ControlTabBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    themeColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF131217), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF23222A), RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf(
            Pair(Icons.Default.ZoomIn, stringResource(R.string.tab_zoom)),
            Pair(Icons.Default.ColorLens, stringResource(R.string.tab_filters)),
            Pair(Icons.Default.Tune, stringResource(R.string.tab_tune)),
            Pair(Icons.Default.Palette, stringResource(R.string.tab_theme))
        )

        tabs.forEachIndexed { index, (icon, label) ->
            val selected = activeTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF25212E) else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .testTag("tab_$index"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) themeColor else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        color = if (selected) themeColor else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
