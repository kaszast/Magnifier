package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Az alsó vezérlőkártya tab-tartalmai. Az állapotot a MagnifierMainScreen tartja; ezek a
// composable-ök a szükséges értékeket paraméterként, a módosításokat hoistolt lambdaként kapják.

@Composable
fun ZoomTabContent(
    appVersion: String,
    themeColor: Color,
    isFrozen: Boolean,
    frozenScale: Float,
    onFrozenScaleChange: (Float) -> Unit,
    liveZoomRatio: Float,
    extraDigitalZoom: Float,
    maxZoom: Float,
    sliderMin: Float,
    sliderMax: Float,
    presets: List<Float>,
    onApplyTotalZoom: (Float, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val currentTotalZoom = if (isFrozen) frozenScale else (liveZoomRatio * extraDigitalZoom)
        val isDigitalRange = !isFrozen && (liveZoomRatio * extraDigitalZoom > maxZoom)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = stringResource(R.string.tab_zoom),
                    tint = themeColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "v$appVersion",
                    color = themeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isDigitalRange) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD0BCFF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = stringResource(R.string.cd_digital_zoom),
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1B1A21), CircleShape)
                    .border(1.dp, Color(0xFF2E2C33), CircleShape)
                    .clickable {
                        if (isFrozen) {
                            onFrozenScaleChange(max(0.5f, frozenScale - 0.5f))
                        } else {
                            onApplyTotalZoom(liveZoomRatio * extraDigitalZoom - 0.5f, false)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.cd_zoom_out),
                    tint = Color(0xFFE6E1E5),
                    modifier = Modifier.size(18.dp)
                 )
            }

            Slider(
                value = currentTotalZoom.coerceIn(sliderMin, sliderMax),
                onValueChange = { newValue ->
                    if (isFrozen) {
                        onFrozenScaleChange(newValue)
                    } else {
                        onApplyTotalZoom(newValue, false)
                    }
                },
                valueRange = sliderMin..sliderMax,
                colors = SliderDefaults.colors(
                    activeTrackColor = themeColor,
                    thumbColor = themeColor,
                    inactiveTrackColor = Color(0xFF1B1A21)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("zoom_slider")
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1B1A21), CircleShape)
                    .border(1.dp, Color(0xFF2E2C33), CircleShape)
                    .clickable {
                        if (isFrozen) {
                            onFrozenScaleChange(min(sliderMax, frozenScale + 0.5f))
                        } else {
                            onApplyTotalZoom(liveZoomRatio * extraDigitalZoom + 0.5f, false)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_zoom_in),
                    tint = Color(0xFFE6E1E5),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = String.format("%.1fx", currentTotalZoom),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.widthIn(min = 55.dp),
                textAlign = TextAlign.End
            )
        }

        // Quick Presets (Non-scrollable, responsive fitting)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val itemWidth = 52.dp
            val spacing = 6.dp
            // Calculate how many items can fully fit in the available width:
            // k * itemWidth + (k - 1) * spacing <= availableWidth
            val maxFit = ((availableWidth + spacing) / (itemWidth + spacing)).toInt().coerceAtLeast(1)
            val visiblePresets = presets.take(maxFit)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visiblePresets.forEach { preset ->
                    val isSelected = if (isFrozen) {
                        abs(frozenScale - preset) < 0.15f
                    } else {
                        abs((liveZoomRatio * extraDigitalZoom) - preset) < 0.15f
                    }

                    val isPresetPossible = isFrozen || preset <= sliderMax
                    if (isPresetPossible) {
                        Box(
                            modifier = Modifier
                                .width(itemWidth)
                                .heightIn(min = 40.dp)
                                .background(
                                    if (isSelected) themeColor else Color(0xFF1B1A21),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, if (isSelected) themeColor else Color(0xFF2E2C33), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isFrozen) {
                                        onFrozenScaleChange(preset)
                                    } else {
                                        onApplyTotalZoom(preset, true)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                         ) {
                            Text(
                                text = if (preset % 1.0f == 0.0f) String.format("%.0fx", preset) else String.format("%.1fx", preset),
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FiltersTabContent(
    appVersion: String,
    themeColor: Color,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ColorLens,
                contentDescription = stringResource(R.string.tab_filters),
                tint = themeColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "v$appVersion",
                color = themeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterMode.values().forEach { mode ->
                val selected = filterMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .background(
                            if (selected) Color(0xFF231D30) else Color(0xFF111115),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) themeColor else Color(0xFF2E2C33),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onFilterModeChange(mode) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                brush = when (mode) {
                                    FilterMode.NORMAL -> {
                                        Brush.sweepGradient(
                                            colors = listOf(
                                                Color(0xFFFF007F), Color(0xFFFF0000), Color(0xFFFF7F00),
                                                Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
                                                Color(0xFF0000FF), Color(0xFF7F00FF), Color(0xFFFF007F)
                                            )
                                        )
                                    }
                                    FilterMode.MONOCHROME -> {
                                        Brush.linearGradient(
                                            colors = listOf(Color.White, Color.Black)
                                        )
                                    }
                                    FilterMode.INVERTED -> {
                                        Brush.linearGradient(
                                            colors = listOf(Color.Cyan, Color.Black)
                                        )
                                    }
                                    FilterMode.YELLOW -> {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFFBBF24), Color.Black)
                                        )
                                    }
                                    FilterMode.RED -> {
                                        Brush.linearGradient(
                                            colors = listOf(Color.Red, Color.Black)
                                        )
                                    }
                                },
                                shape = CircleShape
                            )
                            .border(1.dp, Color(0xFF2E2C33).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.cd_selected),
                                tint = if (mode == FilterMode.MONOCHROME) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(mode.labelRes),
                        color = if (selected) themeColor else Color(0xFFA1A1AA),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    }
                }
            }
        }
    }
}

@Composable
fun TuneTabContent(
    appVersion: String,
    themeColor: Color,
    isFrozen: Boolean,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    exposureIndex: Int,
    onExposureIndexChange: (Int) -> Unit,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    sharpenStrength: Float,
    onSharpenStrengthChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isFrozen) Icons.Default.Contrast else Icons.Default.Exposure,
                    contentDescription = stringResource(R.string.tab_tune),
                    tint = themeColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "v$appVersion",
                    color = themeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isFrozen) Icons.Default.Contrast else Icons.Default.Exposure,
                contentDescription = stringResource(if (isFrozen) R.string.label_contrast else R.string.label_exposure),
                tint = themeColor,
                modifier = Modifier.size(18.dp)
            )
            Slider(
                value = if (isFrozen) contrast else exposureIndex.toFloat(),
                onValueChange = { newValue ->
                    if (isFrozen) {
                        onContrastChange(newValue)
                    } else {
                        onExposureIndexChange(newValue.roundToInt())
                    }
                },
                valueRange = if (isFrozen) 1.0f..3.0f else minExposureIndex.toFloat()..maxExposureIndex.toFloat(),
                steps = if (!isFrozen && (maxExposureIndex - minExposureIndex > 0)) maxExposureIndex - minExposureIndex - 1 else 0,
                colors = SliderDefaults.colors(
                    activeTrackColor = themeColor,
                    thumbColor = themeColor,
                    inactiveTrackColor = Color(0xFF1B1A21)
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isFrozen) String.format("%.1fx", contrast) else "$exposureIndex EV",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 55.dp),
                textAlign = TextAlign.End
            )
        }

        if (isFrozen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LightMode,
                    contentDescription = stringResource(R.string.label_brightness),
                    tint = themeColor,
                    modifier = Modifier.size(18.dp)
                )
                Slider(
                    value = brightness,
                    onValueChange = { onBrightnessChange(it) },
                    valueRange = -80f..80f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = themeColor,
                        thumbColor = themeColor,
                        inactiveTrackColor = Color(0xFF1B1A21)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format("%+d", brightness.roundToInt()),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 55.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        // Dynamic Sharpening Strength Slider (available for both live digital zoom and frozen images)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.label_sharpen),
                tint = themeColor,
                modifier = Modifier.size(18.dp)
            )
            Slider(
                value = sharpenStrength,
                onValueChange = { onSharpenStrengthChange(it) },
                valueRange = 0.0f..10.0f,
                colors = SliderDefaults.colors(
                    activeTrackColor = themeColor,
                    thumbColor = themeColor,
                    inactiveTrackColor = Color(0xFF1B1A21)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sharpen_strength_slider")
            )
            Text(
                text = if (sharpenStrength == 0.0f) "0.0" else String.format("%.1fx", sharpenStrength),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 55.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun ThemeTabContent(
    appVersion: String,
    themeColor: Color,
    themeOptions: List<AppThemeColor>,
    currentThemeIndex: Int,
    onThemeIndexChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = stringResource(R.string.tab_theme),
                tint = themeColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "v$appVersion",
                color = themeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            themeOptions.forEachIndexed { index, option ->
                val selected = currentThemeIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .background(
                            if (selected) option.color.copy(alpha = 0.15f) else Color(0xFF111115),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) option.color else Color(0xFF2E2C33),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onThemeIndexChange(index) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(option.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.cd_selected),
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(option.nameRes),
                            color = if (selected) option.color else Color(0xFFA1A1AA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
