/*
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flamingo.clock.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CircularProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    thickness: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val thicknessPx = with(LocalDensity.current) { thickness.toPx() }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .drawWithContent {
                drawArc(
                    trackColor,
                    0f,
                    360f,
                    true,
                    size = Size(size.width - 2 * thicknessPx, size.height - 2 * thicknessPx),
                    topLeft = Offset(x = thicknessPx, y = thicknessPx),
                    style = Stroke(width = thicknessPx),
                    alpha = alpha
                )
                drawArc(
                    progressColor,
                    -90f,
                    progress * 180,
                    false,
                    size = Size(size.width - 2 * thicknessPx, size.height - 2 * thicknessPx),
                    topLeft = Offset(x = thicknessPx, y = thicknessPx),
                    style = Stroke(width = thicknessPx),
                    alpha = alpha
                )
                drawArc(
                    progressColor,
                    90 * (progress * 2 - 1),
                    progress * 180f,
                    false,
                    size = Size(size.width - 2 * thicknessPx, size.height - 2 * thicknessPx),
                    topLeft = Offset(x = thicknessPx, y = thicknessPx),
                    style = Stroke(width = thicknessPx, cap = StrokeCap.Round),
                    alpha = alpha
                )
                drawContent()
            },
        content = content,
        contentAlignment = Alignment.Center
    )
}