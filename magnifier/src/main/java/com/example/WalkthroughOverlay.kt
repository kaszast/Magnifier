package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
                        .height(320.dp)
                ) { page ->
                    val step = steps[page]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
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
