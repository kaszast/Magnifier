package com.example

import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

import androidx.compose.material.icons.automirrored.filled.RotateRight

fun Modifier.drawVerticalScrollbar(
    scrollState: ScrollState,
    color: Color = Color.White.copy(alpha = 0.45f),
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val elementHeight = size.height
        val totalHeight = elementHeight + scrollState.maxValue.toFloat()
        val thumbHeight = (elementHeight / totalHeight * elementHeight).coerceAtLeast(24.dp.toPx())
        val scrollOffset = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        val thumbY = scrollOffset * (elementHeight - thumbHeight)

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx(), thumbY),
            size = Size(width.toPx(), thumbHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}

data class WalkthroughStep(
    val titleRes: Int,
    val bodyRes: Int,
    val icon: ImageVector,
    val iconColor: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalkthroughOverlay(
    themeColor: Color,
    onDismiss: () -> Unit
) {
    val steps = listOf(
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_1,
            bodyRes = R.string.walkthrough_body_1,
            icon = Icons.Default.ZoomIn,
            iconColor = themeColor
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_2,
            bodyRes = R.string.walkthrough_body_2,
            icon = Icons.Default.Tune,
            iconColor = themeColor
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_3,
            bodyRes = R.string.walkthrough_body_3,
            icon = Icons.Default.Pause,
            iconColor = Color(0xFFEF4444)
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_4,
            bodyRes = R.string.walkthrough_body_4,
            icon = Icons.AutoMirrored.Filled.TextSnippet,
            iconColor = themeColor
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_rotate_flip,
            bodyRes = R.string.walkthrough_body_rotate_flip,
            icon = Icons.AutoMirrored.Filled.RotateRight,
            iconColor = themeColor
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_5,
            bodyRes = R.string.walkthrough_body_5,
            icon = Icons.Default.FilterCenterFocus,
            iconColor = themeColor
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_6,
            bodyRes = R.string.walkthrough_body_6,
            icon = Icons.Default.Info,
            iconColor = themeColor
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_7,
            bodyRes = R.string.walkthrough_body_7,
            icon = Icons.Default.Tune,
            iconColor = themeColor
        ),
        WalkthroughStep(
            titleRes = R.string.walkthrough_title_8,
            bodyRes = R.string.walkthrough_body_8,
            icon = Icons.Default.Settings,
            iconColor = themeColor
        )
    )

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}, // Intercept clicks to avoid tapping elements underneath
        contentAlignment = Alignment.Center
    ) {
        // Main container Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131217)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .border(1.dp, Color(0xFF2E2C33).copy(alpha = 0.5f), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header: Title & Skip button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.action_skip),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pager for Walkthrough Steps
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) { page ->
                    val step = steps[page]
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .drawVerticalScrollbar(scrollState)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon circle
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(step.iconColor.copy(alpha = 0.15f), CircleShape)
                                .border(2.dp, step.iconColor.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = step.iconColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title
                        Text(
                            text = stringResource(step.titleRes),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Body description
                        if (page >= 6) {
                            val bodyText = stringResource(step.bodyRes)
                            val parsedLines = remember(bodyText) { parseOnboardingLines(bodyText) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                parsedLines.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(step.iconColor.copy(alpha = 0.15f), CircleShape)
                                                .border(1.dp, step.iconColor.copy(alpha = 0.8f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                tint = step.iconColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = item.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (item.description.isNotEmpty()) {
                                                Text(
                                                    text = item.description,
                                                    color = Color(0xFFA1A1AA),
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(step.bodyRes),
                                color = Color(0xFFA1A1AA),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dot Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    repeat(steps.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 10.dp else 8.dp)
                                .background(
                                    color = if (isSelected) themeColor else Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Next / Finish button
                Button(
                    onClick = {
                        if (pagerState.currentPage < steps.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage < steps.size - 1) {
                            stringResource(R.string.action_next)
                        } else {
                            stringResource(R.string.action_start_using)
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun getIconForTitle(title: String): ImageVector {
    val lower = title.lowercase()
    return when {
        lower.contains("vaku") || lower.contains("flash") -> Icons.Default.FlashOn
        lower.contains("kimerevítés") || lower.contains("freeze") -> Icons.Default.Pause
        lower.contains("elforgatás") || lower.contains("rotate") -> Icons.AutoMirrored.Filled.RotateRight
        lower.contains("tükrözés") || lower.contains("flip") -> Icons.Default.Flip
        lower.contains("mentés") || lower.contains("save") -> Icons.Default.Save
        lower.contains("megosztás") || lower.contains("share") -> Icons.Default.Share
        lower.contains("szövegolvasó") || lower.contains("text reader") || lower.contains("ocr") -> Icons.AutoMirrored.Filled.TextSnippet
        lower.contains("qr") || lower.contains("vonalkód") || lower.contains("barcode") -> Icons.Default.QrCodeScanner
        lower.contains("zoom") || lower.contains("nagyítás") -> Icons.Default.ZoomIn
        lower.contains("fényerő") || lower.contains("brightness") || lower.contains("expozíció") || lower.contains("exposure") -> Icons.Default.Brightness5
        lower.contains("kontraszt") || lower.contains("contrast") -> Icons.Default.Contrast
        lower.contains("élesítés") || lower.contains("sharpen") -> Icons.Default.ChangeHistory
        lower.contains("auto fókusz") || lower.contains("auto focus") -> Icons.Default.CenterFocusStrong
        lower.contains("manuális fókusz") || lower.contains("manual focus") -> Icons.Default.Tune
        lower.contains("téma") || lower.contains("theme") -> Icons.Default.Palette
        lower.contains("nyelv") || lower.contains("language") -> Icons.Default.Language
        lower.contains("értékelés") || lower.contains("rate") -> Icons.Default.Star
        else -> Icons.Default.Info
    }
}

private data class ParsedLine(val icon: ImageVector, val title: String, val description: String)

private fun parseOnboardingLines(text: String): List<ParsedLine> {
    val lines = text.split("\n")
    return lines.mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val colonIndex = line.indexOf(":")
        if (colonIndex != -1) {
            val title = line.substring(0, colonIndex).trim()
            val desc = line.substring(colonIndex + 1).trim()
            ParsedLine(getIconForTitle(title), title, desc)
        } else {
            ParsedLine(getIconForTitle(line), line.trim(), "")
        }
    }
}
