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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times

import com.flamingo.clock.R
import com.flamingo.clock.data.Time
import com.flamingo.clock.data.formatTime
import com.flamingo.clock.data.getPrependedString
import com.flamingo.clock.ui.ContentOrientation
import com.flamingo.clock.ui.states.StopwatchScreenState
import com.flamingo.clock.ui.states.rememberStopwatchScreenState
import com.flamingo.clock.ui.theme.ButtonSizeVertical
import com.flamingo.clock.ui.theme.ButtonSizeHorizontal

import kotlinx.coroutines.delay

private const val ButtonAspectRatioAnimation = "Button aspect ratio animation"
private const val ButtonCornerRadiusAnimation = "Button corner radius animation"

internal const val EnterExpansionDuration = 500
internal const val EnterFadeInDuration = 500
internal const val ExitShrinkDuration = 900
internal const val ExitFadeOutDuration = 100
private const val ListWeightAnimation = "List weight animation"
private const val ListAlphaAnimation = "List alpha animation"

@Composable
fun StopwatchScreen(
    orientation: ContentOrientation,
    modifier: Modifier = Modifier,
    state: StopwatchScreenState = rememberStopwatchScreenState()
) {
    Box(modifier = modifier) {
        val time by state.time.collectAsState(initial = Time.Zero)
        val hasStarted by state.started.collectAsState(initial = false)
        val isRunning by state.running.collectAsState(initial = false)
        val laps by state.laps.collectAsState(initial = emptyList())
        val currentLap by state.currentLap.collectAsState(initial = Pair(0, ""))
        val toggleStateCallback by rememberUpdatedState(newValue = {
            if (isRunning) {
                state.pause()
            } else {
                state.start()
            }
        })
        val lapSetRequest by rememberUpdatedState(newValue = { state.lap() })
        val resetRequest by rememberUpdatedState(newValue = { state.reset() })
        when (orientation) {
            ContentOrientation.Vertical -> VerticalScreenContent(
                time = time,
                hasStarted = hasStarted,
                isRunning = isRunning,
                laps = laps,
                currentLap = currentLap,
                onToggleState = toggleStateCallback,
                onLapSetRequest = lapSetRequest,
                onResetRequest = resetRequest,
                modifier = Modifier.fillMaxSize()
            )
            ContentOrientation.Horizontal -> HorizontalScreenContent(
                time = time,
                hasStarted = hasStarted,
                isRunning = isRunning,
                laps = laps,
                currentLap = currentLap,
                onToggleState = toggleStateCallback,
                onLapSetRequest = lapSetRequest,
                onResetRequest = resetRequest,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalUnitApi::class)
@Composable
private fun VerticalScreenContent(
    time: Time,
    hasStarted: Boolean,
    isRunning: Boolean,
    laps: List<String>,
    currentLap: Pair<Int, String>,
    onToggleState: () -> Unit,
    onLapSetRequest: () -> Unit,
    onResetRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        ProgressWithTime(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            time = time,
            orientation = ContentOrientation.Vertical,
            blinkTime = hasStarted && !isRunning,
            lapNumber = currentLap.first,
            fontSize = TextUnit(48f, TextUnitType.Sp)
        )
        val listTransition = updateTransition(targetState = laps.isNotEmpty(), label = null)
        val weight by listTransition.animateFloat(label = ListWeightAnimation, transitionSpec = {
            when {
                false isTransitioningTo true -> tween(durationMillis = EnterExpansionDuration)
                else -> tween(
                    durationMillis = ExitShrinkDuration,
                    delayMillis = ExitFadeOutDuration,
                    easing = FastOutSlowInEasing
                )
            }
        }) { if (it) 1f else 0.01f }
        val alpha by listTransition.animateFloat(label = ListAlphaAnimation, transitionSpec = {
            when {
                false isTransitioningTo true -> tween(
                    delayMillis = EnterExpansionDuration,
                    durationMillis = EnterFadeInDuration
                )
                else -> tween(durationMillis = ExitFadeOutDuration)
            }
        }) { if (it) 1f else 0f }
        if (listTransition.currentState || listTransition.targetState) {
            LazyColumn(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxSize()
                    .weight(weight)
                    .graphicsLayer {
                        this.alpha = alpha
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                item(key = currentLap.first) {
                    Text(
                        currentLap.second,
                        modifier = Modifier.animateItemPlacement(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                items(items = laps, key = { it }) {
                    Text(
                        it,
                        modifier = Modifier.animateItemPlacement(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalControlButtons(
            modifier = Modifier
                .fillMaxWidth(),
            hasStarted = hasStarted,
            isRunning = isRunning,
            onResetRequest = onResetRequest,
            onLapSetRequest = onLapSetRequest,
            onToggleState = onToggleState
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalUnitApi::class)
@Composable
private fun HorizontalScreenContent(
    time: Time,
    hasStarted: Boolean,
    isRunning: Boolean,
    laps: List<String>,
    currentLap: Pair<Int, String>,
    onToggleState: () -> Unit,
    onLapSetRequest: () -> Unit,
    onResetRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        ProgressWithTime(
            time = time,
            orientation = ContentOrientation.Horizontal,
            blinkTime = hasStarted && !isRunning,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            lapNumber = currentLap.first,
            fontSize = TextUnit(30f, TextUnitType.Sp)
        )
        val listTransition = updateTransition(targetState = laps.isNotEmpty(), label = null)
        val weight by listTransition.animateFloat(label = ListWeightAnimation, transitionSpec = {
            when {
                false isTransitioningTo true -> tween(durationMillis = EnterExpansionDuration)
                else -> tween(
                    durationMillis = ExitShrinkDuration,
                    delayMillis = ExitFadeOutDuration,
                    easing = FastOutSlowInEasing
                )
            }
        }) { if (it) 1f else 0.01f }
        val alpha by listTransition.animateFloat(label = ListAlphaAnimation, transitionSpec = {
            when {
                false isTransitioningTo true -> tween(
                    delayMillis = EnterExpansionDuration,
                    durationMillis = EnterFadeInDuration
                )
                else -> tween(durationMillis = ExitFadeOutDuration)
            }
        }) { if (it) 1f else 0f }
        if (listTransition.currentState || listTransition.targetState) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(weight)
                    .graphicsLayer {
                        this.alpha = alpha
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item(key = currentLap.first) {
                    Text(
                        currentLap.second,
                        modifier = Modifier.animateItemPlacement(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                items(items = laps, key = { it }) {
                    Text(
                        it,
                        modifier = Modifier.animateItemPlacement(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        VerticalControlButtons(
            modifier = Modifier
                .fillMaxHeight(),
            hasStarted = hasStarted,
            isRunning = isRunning,
            onToggleState = onToggleState,
            onLapSetRequest = onLapSetRequest,
            onResetRequest = onResetRequest
        )
    }
}

@OptIn(ExperimentalUnitApi::class)
@Composable
fun ProgressWithTime(
    time: Time,
    fontSize: TextUnit,
    orientation: ContentOrientation,
    blinkTime: Boolean,
    lapNumber: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(lapNumber) {
            progress.snapTo(0f)
            if (lapNumber > 1) {
                progress.animateTo(1f, animationSpec = tween(durationMillis = 1000))
            }
        }
        CircularProgressBar(
            modifier = Modifier
                .then(
                    when (orientation) {
                        ContentOrientation.Vertical -> Modifier.fillMaxWidth(0.75f)
                        ContentOrientation.Horizontal -> Modifier.fillMaxHeight(0.95f)
                    }
                )
                .align(Alignment.Center),
            progress = progress.value
        ) {
            var visible by remember { mutableStateOf(true) }
            LaunchedEffect(blinkTime) {
                while (blinkTime) {
                    visible = false
                    delay(500)
                    visible = true
                    delay(500)
                }
                visible = true
            }
            if (visible) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        modifier = Modifier.animateContentSize(),
                        text = formatTime(time),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = getPrependedString(time.millisecond),
                        fontSize = TextUnit(value = (fontSize.value * 2) / 3, type = fontSize.type),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun HorizontalControlButtons(
    hasStarted: Boolean,
    isRunning: Boolean,
    onToggleState: () -> Unit,
    onLapSetRequest: () -> Unit,
    onResetRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
    ) {
        val leftButtonAlpha by animateFloatAsState(targetValue = if (hasStarted) 1f else 0f)
        IconButton(
            modifier = Modifier
                .size(2 * ButtonSizeVertical / 3)
                .clip(CircleShape)
                .alpha(leftButtonAlpha),
            onClick = onResetRequest,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_reset_24),
                contentDescription = stringResource(id = R.string.reset_button_content_desc)
            )
        }
        val transition = updateTransition(targetState = isRunning, label = null)
        val aspectRatio by transition.animateFloat(label = ButtonAspectRatioAnimation) {
            if (it) 1.5f else 1f
        }
        val cornerRadius by transition.animateDp(label = ButtonCornerRadiusAnimation) {
            ButtonSizeVertical / (if (it) 4 else 2)
        }
        IconButton(
            modifier = Modifier
                .height(ButtonSizeVertical)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(cornerRadius)),
            onClick = onToggleState,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            AnimatedContent(targetState = isRunning) {
                Icon(
                    painter = painterResource(id = if (it) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24),
                    contentDescription = stringResource(id = R.string.stopwatch_start_stop_button_content_desc)
                )
            }
        }
        val rightButtonAlpha by animateFloatAsState(targetValue = if (hasStarted && isRunning) 1f else 0f)
        IconButton(
            modifier = Modifier
                .size(2 * ButtonSizeVertical / 3)
                .clip(CircleShape)
                .alpha(rightButtonAlpha),
            onClick = onLapSetRequest,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.outline_stopwatch_24),
                contentDescription = stringResource(id = R.string.lap_button_content_desc)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun VerticalControlButtons(
    hasStarted: Boolean,
    isRunning: Boolean,
    onToggleState: () -> Unit,
    onLapSetRequest: () -> Unit,
    onResetRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(1.5 * ButtonSizeHorizontal),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val topButtonAlpha by animateFloatAsState(targetValue = if (hasStarted && isRunning) 1f else 0f)
        IconButton(
            modifier = Modifier
                .alpha(topButtonAlpha)
                .size(4 * ButtonSizeHorizontal / 5)
                .clip(CircleShape),
            onClick = onLapSetRequest,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.outline_stopwatch_24),
                contentDescription = stringResource(id = R.string.lap_button_content_desc)
            )
        }
        val transition = updateTransition(targetState = isRunning, label = null)
        val aspectRatio by transition.animateFloat(label = ButtonAspectRatioAnimation) {
            if (it) 1.25f else 1f
        }
        val cornerRadius by transition.animateDp(label = ButtonCornerRadiusAnimation) {
            ButtonSizeHorizontal / (if (it) 4 else 2)
        }
        IconButton(
            modifier = Modifier
                .height(ButtonSizeHorizontal)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(cornerRadius)),
            onClick = onToggleState,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            AnimatedContent(targetState = isRunning) {
                Icon(
                    painter = painterResource(id = if (it) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24),
                    contentDescription = stringResource(id = R.string.stopwatch_start_stop_button_content_desc)
                )
            }
        }
        val bottomButtonAlpha by animateFloatAsState(targetValue = if (hasStarted) 1f else 0f)
        IconButton(
            modifier = Modifier
                .size(4 * ButtonSizeHorizontal / 5)
                .clip(CircleShape)
                .alpha(bottomButtonAlpha),
            onClick = onResetRequest,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_reset_24),
                contentDescription = stringResource(id = R.string.reset_button_content_desc)
            )
        }
    }
}