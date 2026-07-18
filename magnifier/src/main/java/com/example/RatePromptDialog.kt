package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun RatePromptDialog(
    themeColor: Color,
    onRateNow: () -> Unit,
    onRateLater: () -> Unit,
    onRateNever: () -> Unit
) {
    Dialog(onDismissRequest = onRateLater) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131217)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2E2C33).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Star rating icon header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(themeColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .border(1.5.dp, themeColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    text = stringResource(R.string.rate_dialog_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Body
                Text(
                    text = stringResource(R.string.rate_dialog_body),
                    color = Color(0xFFA1A1AA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rate now (positive action)
                    Button(
                        onClick = onRateNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_rate_now),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rate later (neutral action)
                        OutlinedButton(
                            onClick = onRateLater,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2E2C33))
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_rate_later),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Never rate (negative action)
                        OutlinedButton(
                            onClick = onRateNever,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_rate_never),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
