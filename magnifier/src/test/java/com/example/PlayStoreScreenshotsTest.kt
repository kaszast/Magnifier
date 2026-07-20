package com.example

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PlayStoreScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    fun AppScreenMock(
        bgImagePath: String?,
        themeColor: Color,
        content: @Composable () -> Unit
    ) {
        MyApplicationTheme {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // Fake Camera Feed
                if (bgImagePath != null && File(bgImagePath).exists()) {
                    val bitmap = BitmapFactory.decodeFile(bgImagePath)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Camera Feed",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Bottom 40% Panel background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.40f)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF131217))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            content()
                        }
                        
                        ControlTabBar(
                            activeTab = if (content.toString().contains("Settings")) 1 else 0,
                            onTabSelected = {},
                            themeColor = themeColor
                        )
                    }
                }
            }
        }
    }

    @Test
    fun screenshot_1_zoom() {
        composeTestRule.setContent {
            AppScreenMock(
                bgImagePath = "src/test/assets/book.jpg",
                themeColor = Color(0xFFB180FF) // Purple
            ) {
                CombinedZoomFiltersTuneTabContent(
                    themeColor = Color(0xFFB180FF),
                    isFrozen = false,
                    frozenScale = 1.0f,
                    onFrozenScaleChange = {},
                    liveZoomRatio = 2.0f,
                    extraDigitalZoom = 1.0f,
                    sliderMin = 1.0f,
                    sliderMax = 10.0f,
                    onApplyTotalZoom = { _, _ -> },
                    filterMode = FilterMode.NORMAL,
                    onFilterModeChange = {},
                    contrast = 1.0f,
                    onContrastChange = {},
                    brightness = 0f,
                    onBrightnessChange = {},
                    exposureIndex = 0,
                    onExposureIndexChange = {},
                    minExposureIndex = -2,
                    maxExposureIndex = 2,
                    sharpenStrength = 0.0f,
                    onSharpenStrengthChange = {},
                    focusMode = "auto",
                    onFocusModeChange = {},
                    manualFocusDistance = 0f,
                    onManualFocusDistanceChange = {},
                    minFocusDistance = 10f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/playstore_1_zoom.png")
    }

    @Test
    fun screenshot_2_frozen_tune() {
        composeTestRule.setContent {
            AppScreenMock(
                bgImagePath = "src/test/assets/leaf.jpg",
                themeColor = Color(0xFF00E676) // Green
            ) {
                CombinedZoomFiltersTuneTabContent(
                    themeColor = Color(0xFF00E676),
                    isFrozen = true,
                    frozenScale = 3.5f,
                    onFrozenScaleChange = {},
                    liveZoomRatio = 1.0f,
                    extraDigitalZoom = 1.0f,
                    sliderMin = 1.0f,
                    sliderMax = 10.0f,
                    onApplyTotalZoom = { _, _ -> },
                    filterMode = FilterMode.NORMAL,
                    onFilterModeChange = {},
                    contrast = 1.5f,
                    onContrastChange = {},
                    brightness = 15f,
                    onBrightnessChange = {},
                    exposureIndex = 0,
                    onExposureIndexChange = {},
                    minExposureIndex = -2,
                    maxExposureIndex = 2,
                    sharpenStrength = 4.0f,
                    onSharpenStrengthChange = {},
                    focusMode = "manual",
                    onFocusModeChange = {},
                    manualFocusDistance = 5f,
                    onManualFocusDistanceChange = {},
                    minFocusDistance = 10f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/playstore_2_frozen.png")
    }

    @Test
    fun screenshot_3_filters() {
        composeTestRule.setContent {
            AppScreenMock(
                bgImagePath = "src/test/assets/book.jpg",
                themeColor = Color(0xFFFFEA00) // Yellow
            ) {
                CombinedZoomFiltersTuneTabContent(
                    themeColor = Color(0xFFFFEA00),
                    isFrozen = false,
                    frozenScale = 1.0f,
                    onFrozenScaleChange = {},
                    liveZoomRatio = 5.0f,
                    extraDigitalZoom = 1.0f,
                    sliderMin = 1.0f,
                    sliderMax = 10.0f,
                    onApplyTotalZoom = { _, _ -> },
                    filterMode = FilterMode.INVERTED,
                    onFilterModeChange = {},
                    contrast = 1.0f,
                    onContrastChange = {},
                    brightness = -10f,
                    onBrightnessChange = {},
                    exposureIndex = 1,
                    onExposureIndexChange = {},
                    minExposureIndex = -2,
                    maxExposureIndex = 2,
                    sharpenStrength = 0.0f,
                    onSharpenStrengthChange = {},
                    focusMode = "auto",
                    onFocusModeChange = {},
                    manualFocusDistance = 0f,
                    onManualFocusDistanceChange = {},
                    minFocusDistance = 10f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/playstore_3_filters.png")
    }

    @Test
    fun screenshot_4_settings() {
        composeTestRule.setContent {
            AppScreenMock(
                bgImagePath = null,
                themeColor = Color(0xFFB180FF) // Purple
            ) {
                SettingsTabContent(
                    themeColor = Color(0xFFB180FF),
                    themeOptions = listOf(
                        AppThemeColor(R.string.theme_purple, Color(0xFFB180FF)),
                        AppThemeColor(R.string.theme_green, Color(0xFF00E676)),
                        AppThemeColor(R.string.theme_gold, Color(0xFFFFEA00)),
                        AppThemeColor(R.string.theme_blue, Color(0xFF00E5FF)),
                        AppThemeColor(R.string.theme_orange, Color(0xFFFF3D00))
                    ),
                    currentThemeIndex = 0,
                    onThemeIndexChange = {},
                    onRateApp = {},
                    onShowTutorial = {},
                    onShowTipJar = {},
                    onChangeLanguage = {},
                    isHdrEnabled = false,
                    onHdrEnabledChange = {},
                    isNightEnabled = false,
                    onNightEnabledChange = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/playstore_4_settings.png")
    }
}
