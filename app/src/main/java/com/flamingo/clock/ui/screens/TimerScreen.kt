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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.DialogProperties

import com.flamingo.clock.R
import com.flamingo.clock.data.getPrependedString
import com.flamingo.clock.ui.ContentOrientation
import com.flamingo.clock.ui.states.Button
import com.flamingo.clock.ui.states.TimerInfo
import com.flamingo.clock.ui.states.TimerScreenState
import com.flamingo.clock.ui.states.rememberTimerScreenState
import com.flamingo.clock.ui.theme.ButtonSizeHorizontal
import com.flamingo.clock.ui.theme.ButtonSizeVertical
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.VerticalPager
import com.google.accompanist.pager.VerticalPagerIndicator
import com.google.accompanist.pager.rememberPagerState

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ButtonAspectRatioAnimation = "Button aspect ratio animation"
private const val ButtonCornerRadiusAnimation = "Button corner radius animation"

private const val TimerViewFractionVertical = 0.6f
private const val TimerViewFractionHorizontal = 0.75f

@OptIn(ExperimentalPagerApi::class)
@Composable
fun TimerScreen(
    orientation: ContentOrientation,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    state: TimerScreenState = rememberTimerScreenState(snackbarHostState = snackbarHostState)
) {
    val timers by state.timers.collectAsState(initial = emptyList())
    var userWantsToAddTimer by rememberSaveable { mutableStateOf(false) }
    val showAddTimerScreen by remember {
        derivedStateOf {
            userWantsToAddTimer || timers.isEmpty()
        }
    }
    val buttonPressedCallback by rememberUpdatedState(newValue = { button: Button ->
        state.handleButtonPress(button)
    })
    val hasUserInput = state.userDurationInput != TimerScreenState.DEFAULT_USER_INPUT_TEXT
    val incrementTimerCallback by rememberUpdatedState(newValue = { id: Int ->
        state.incrementTimer(id)
    })
    val resetCallback by rememberUpdatedState(newValue = { id: Int ->
        state.resetTimer(id)
    })
    val showPlayOrPauseButton by remember(hasUserInput) {
        derivedStateOf {
            (showAddTimerScreen && hasUserInput) || (!showAddTimerScreen && timers.isNotEmpty())
        }
    }
    val showDeleteButton by remember {
        derivedStateOf {
            timers.isNotEmpty()
        }
    }
    val pagerState = rememberPagerState()
    val centerButtonState by remember(hasUserInput) {
        derivedStateOf {
            when {
                showAddTimerScreen && hasUserInput -> CenterButtonState.PLAY
                timers.isNotEmpty() -> {
                    val currentTimer =
                        if (
                            timers.size < pagerState.pageCount &&
                            pagerState.currentPage == (pagerState.pageCount - 1)
                        ) {
                            // Last timer was deleted, to prevent oob exception,
                            // use last element in actual list.
                            timers.last()
                        } else {
                            timers[pagerState.currentPage]
                        }
                    if (currentTimer.hasStarted) {
                        if (currentTimer.isNegative) {
                            CenterButtonState.STOP
                        } else if (currentTimer.isPaused) {
                            CenterButtonState.PLAY
                        } else {
                            CenterButtonState.PAUSE
                        }
                    } else {
                        CenterButtonState.PLAY
                    }
                }
                else -> CenterButtonState.PLAY
            }
        }
    }
    val deleteButtonCallback by rememberUpdatedState(newValue = {
        if (showAddTimerScreen) {
            userWantsToAddTimer = false
            state.resetInput()
        } else {
            val timerId = timers[pagerState.currentPage].id
            state.deleteTimer(timerId)
        }
    })
    val coroutineScope = rememberCoroutineScope()
    val playOrPauseButtonCallback by rememberUpdatedState(newValue = {
        if (showAddTimerScreen) {
            coroutineScope.launch {
                pagerState.scrollToPage(0)
            }
            state.addTimer()
            userWantsToAddTimer = false
        } else {
            val currentTimer = timers[pagerState.currentPage]
            if (currentTimer.hasStarted) {
                if (currentTimer.isNegative) {
                    state.resetTimer(currentTimer.id)
                } else if (currentTimer.isPaused) {
                    state.resumeTimer(currentTimer.id)
                } else {
                    state.pauseTimer(currentTimer.id)
                }
            } else {
                state.startTimer(currentTimer.id)
            }
        }
    })
    val addButtonCallback by rememberUpdatedState(newValue = {
        userWantsToAddTimer = true
    })
    val setLabelCallback by rememberUpdatedState(newValue = { id: Int, label: String ->
        state.setTimerLabel(id, label)
    })
    when (orientation) {
        ContentOrientation.Horizontal -> HorizontalContent(
            modifier = modifier,
            timers = timers,
            showAddTimerScreen = showAddTimerScreen,
            userDurationInput = state.userDurationInput,
            onButtonPressed = buttonPressedCallback,
            onIncrementTimerRequest = incrementTimerCallback,
            onResetRequest = resetCallback,
            pagerState = pagerState,
            showDeleteButton = showDeleteButton,
            showPlayOrPauseButton = showPlayOrPauseButton,
            centerButtonState = centerButtonState,
            onDeleteClicked = deleteButtonCallback,
            onPlayOrPauseClicked = playOrPauseButtonCallback,
            onAddClicked = addButtonCallback,
            onSetLabelRequest = setLabelCallback,
        )
        ContentOrientation.Vertical -> VerticalContent(
            modifier = modifier,
            timers = timers,
            showAddTimerScreen = showAddTimerScreen,
            userDurationInput = state.userDurationInput,
            onButtonPressed = buttonPressedCallback,
            onIncrementTimerRequest = incrementTimerCallback,
            onResetRequest = resetCallback,
            pagerState = pagerState,
            showDeleteButton = showDeleteButton,
            showPlayOrPauseButton = showPlayOrPauseButton,
            centerButtonState = centerButtonState,
            onDeleteClicked = deleteButtonCallback,
            onPlayOrPauseClicked = playOrPauseButtonCallback,
            onAddClicked = addButtonCallback,
            onSetLabelRequest = setLabelCallback,
        )
    }
}

@OptIn(ExperimentalPagerApi::class, ExperimentalAnimationApi::class)
@Composable
private fun HorizontalContent(
    timers: List<TimerInfo>,
    showAddTimerScreen: Boolean,
    userDurationInput: String,
    onButtonPressed: (Button) -> Unit,
    onIncrementTimerRequest: (Int) -> Unit,
    onResetRequest: (Int) -> Unit,
    pagerState: PagerState,
    showDeleteButton: Boolean,
    showPlayOrPauseButton: Boolean,
    centerButtonState: CenterButtonState,
    onDeleteClicked: () -> Unit,
    onPlayOrPauseClicked: () -> Unit,
    onAddClicked: () -> Unit,
    onSetLabelRequest: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = showAddTimerScreen,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { showTimerScreen ->
            if (showTimerScreen) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DurationInputText(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(0.5f),
                        text = userDurationInput
                    )
                    ButtonPad(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.75f),
                        onButtonPressed = onButtonPressed
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    VerticalPager(
                        count = timers.size,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(),
                        state = pagerState
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val timer = timers[it]
                            TimerLabel(
                                label = timer.label,
                                onConfirmRequest = {
                                    onSetLabelRequest(timer.id, it)
                                },
                            )
                            TimerView(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxHeight()
                                    .fillMaxWidth(TimerViewFractionHorizontal),
                                timer = timer,
                                onIncrementTimerRequest = onIncrementTimerRequest,
                                onResetRequest = onResetRequest,
                                progressBarHeightRatio = 0.95f
                            )
                        }
                    }
                    if (timers.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .fillMaxWidth((1 - TimerViewFractionHorizontal) / 2)
                        ) {
                            VerticalPagerIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                pagerState = pagerState,
                                activeColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        VerticalControlButtons(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 24.dp),
            showDeleteButton = showDeleteButton,
            showPlayOrPauseButton = showPlayOrPauseButton,
            centerButtonState = centerButtonState,
            showAddButton = !showAddTimerScreen,
            onDeleteClicked = onDeleteClicked,
            onPlayOrPauseClicked = onPlayOrPauseClicked,
            onAddClicked = onAddClicked
        )
    }
}

@OptIn(ExperimentalPagerApi::class, ExperimentalAnimationApi::class)
@Composable
private fun VerticalContent(
    timers: List<TimerInfo>,
    showAddTimerScreen: Boolean,
    userDurationInput: String,
    onButtonPressed: (Button) -> Unit,
    onIncrementTimerRequest: (Int) -> Unit,
    onResetRequest: (Int) -> Unit,
    pagerState: PagerState,
    showDeleteButton: Boolean,
    showPlayOrPauseButton: Boolean,
    centerButtonState: CenterButtonState,
    onDeleteClicked: () -> Unit,
    onPlayOrPauseClicked: () -> Unit,
    onAddClicked: () -> Unit,
    onSetLabelRequest: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = showAddTimerScreen,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { showTimerScreen ->
            if (showTimerScreen) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    DurationInputText(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxHeight(0.175f),
                        text = userDurationInput
                    )
                    ButtonPad(
                        modifier = Modifier
                            .fillMaxHeight(0.9f)
                            .aspectRatio(0.75f),
                        onButtonPressed = onButtonPressed
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    VerticalPager(
                        count = timers.size,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(),
                        state = pagerState
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val timer = timers[it]
                            TimerLabel(
                                label = timer.label,
                                onConfirmRequest = {
                                    onSetLabelRequest(timer.id, it)
                                },
                            )
                            TimerView(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxSize(TimerViewFractionVertical),
                                timer = timer,
                                onIncrementTimerRequest = onIncrementTimerRequest,
                                onResetRequest = onResetRequest
                            )
                        }
                    }
                    if (timers.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .fillMaxWidth((1 - TimerViewFractionVertical) / 2)
                        ) {
                            VerticalPagerIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                pagerState = pagerState,
                                activeColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        HorizontalControlButtons(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            showDeleteButton = showDeleteButton,
            showPlayOrPauseButton = showPlayOrPauseButton,
            centerButtonState = centerButtonState,
            showAddButton = !showAddTimerScreen,
            onDeleteClicked = onDeleteClicked,
            onPlayOrPauseClicked = onPlayOrPauseClicked,
            onAddClicked = onAddClicked
        )
    }
}

@OptIn(ExperimentalUnitApi::class)
@Composable
fun DurationInputText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit(20f, TextUnitType.Sp)
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val defaultColor = MaterialTheme.colorScheme.onSurface
        val highlightColor = MaterialTheme.colorScheme.primary
        Text(
            text = buildAnnotatedString {
                val hoursText = text.substring(0..1)
                val hours = hoursText.toInt()
                appendStyledTime(
                    time = hoursText,
                    unit = "h",
                    color = if (hours > 0) highlightColor else defaultColor,
                    fontSize = fontSize
                )
                append(' ')
                val minutesText = text.substring(2..3)
                val minutes = minutesText.toInt()
                appendStyledTime(
                    time = minutesText,
                    unit = "m",
                    color = if (minutes > 0 || hours > 0) highlightColor else defaultColor,
                    fontSize = fontSize
                )
                append(' ')
                val secondsText = text.substring(4..5)
                val seconds = secondsText.toInt()
                appendStyledTime(
                    time = secondsText,
                    unit = "s",
                    color = if (seconds > 0 || minutes > 0 || hours > 0) highlightColor else defaultColor,
                    fontSize = fontSize
                )
            },
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val scale = minOf(maxWidthPx / placeable.width, maxHeightPx / placeable.height)
                layout(placeable.width, placeable.height) {
                    placeable.placeWithLayer(0, 0, 0f) {
                        scaleX = scale
                        scaleY = scale
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalUnitApi::class)
fun AnnotatedString.Builder.appendStyledTime(
    time: String,
    unit: String,
    color: Color,
    fontSize: TextUnit
) {
    withStyle(
        style = SpanStyle(
            fontSize = fontSize,
            color = color
        )
    ) {
        append(time)
    }
    withStyle(
        style = SpanStyle(
            fontSize = TextUnit(
                fontSize.value / 2,
                fontSize.type
            ),
            color = color
        )
    ) {
        append(unit)
    }
}

@Composable
fun ButtonPad(
    onButtonPressed: (Button) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val numbers = remember { (1..9).map { Button.Number(it) } }
        numbers.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                row.forEach {
                    Button(
                        button = it,
                        onClick = {
                            onButtonPressed(it)
                        },
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            val buttons = remember { listOf(Button.DoubleZero, Button.Number(0), Button.Backspace) }
            buttons.forEach {
                Button(
                    button = it,
                    onClick = {
                        onButtonPressed(it)
                    },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewButtonPad() {
    ButtonPad(onButtonPressed = {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Button(button: Button, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = if (button is Button.Backspace)
            MaterialTheme.colorScheme.surfaceVariant
        else
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        shape = CircleShape
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val maxWidthPx = with(density) { 2 * maxWidth.toPx() } / 3
            val maxHeightPx = with(density) { 2 * maxHeight.toPx() } / 3
            when (button) {
                Button.Backspace -> Icon(
                    painter = painterResource(id = R.drawable.outline_backspace_24),
                    contentDescription = stringResource(id = R.string.backspace_button_content_desc),
                    modifier = Modifier
                        .height(maxHeight / 2)
                        .width(maxWidth / 2)
                )
                Button.DoubleZero -> Text(
                    text = "00",
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val scale =
                            minOf(maxWidthPx / placeable.width, maxHeightPx / placeable.height)
                        layout(placeable.width, placeable.height) {
                            placeable.placeWithLayer(0, 0, 0f) {
                                scaleX = scale
                                scaleY = scale
                            }
                        }
                    }
                )
                is Button.Number -> Text(
                    text = button.number.toString(),
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val scale =
                            minOf(maxWidthPx / placeable.width, maxHeightPx / placeable.height)
                        layout(placeable.width, placeable.height) {
                            placeable.placeWithLayer(0, 0, 0f) {
                                scaleX = scale
                                scaleY = scale
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TimerLabel(
    label: String?,
    onConfirmRequest: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    LabelEditDialog(
        showDialog = showDialog,
        currentLabel = label,
        onDismissRequest = {
            showDialog = false
        },
        onConfirmRequest = onConfirmRequest,
    )
    Text(
        text = label ?: stringResource(id = R.string.label),
        modifier = Modifier
            .padding(start = 24.dp)
            .clickable {
                showDialog = true
            },
        color = if (label?.isNotBlank() == true)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        style = MaterialTheme.typography.titleLarge
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelEditDialog(
    showDialog: Boolean,
    currentLabel: String?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    AnimatedVisibility(visible = showDialog) {
        var label by remember { mutableStateOf(currentLabel ?: "") }
        val confirmCallback by rememberUpdatedState(newValue = {
            onConfirmRequest(label)
            onDismissRequest()
        })
        AlertDialog(
            properties = DialogProperties(
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            ),
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(onClick = confirmCallback) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                    },
                    label = {
                        Text(text = stringResource(id = R.string.label))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            confirmCallback()
                        }
                    )
                )
            }
        )
    }
}

@OptIn(
    ExperimentalUnitApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class
)
@Composable
fun TimerView(
    timer: TimerInfo,
    onIncrementTimerRequest: (Int) -> Unit,
    onResetRequest: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit(40f, TextUnitType.Sp),
    progressBarHeightRatio: Float = 0.8f
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        var progressBarAlpha by remember { mutableStateOf(1f) }
        LaunchedEffect(timer.isNegative) {
            progressBarAlpha = 1f
            while (timer.isNegative) {
                progressBarAlpha = 0f
                delay(500)
                progressBarAlpha = 1f
                delay(500)
            }
        }
        CircularProgressBar(
            progress = timer.progress.coerceAtLeast(0f),
            alpha = progressBarAlpha,
            modifier = Modifier.fillMaxHeight(progressBarHeightRatio),
        ) {
            var timeAlpha by remember { mutableStateOf(1f) }
            LaunchedEffect(timer.isPaused) {
                timeAlpha = 1f
                while (timer.isPaused) {
                    timeAlpha = 0f
                    delay(500)
                    timeAlpha = 1f
                    delay(500)
                }
            }
            Text(
                text = buildString {
                    if (timer.isNegative) {
                        append("-")
                    }
                    if (timer.remainingHours > 0) {
                        append(timer.remainingHours)
                        append(":")
                    }
                    if (timer.remainingMinutes > 0 || timer.remainingHours > 0) {
                        if (timer.remainingHours > 0) {
                            append(getPrependedString(timer.remainingMinutes))
                        } else {
                            append(timer.remainingMinutes)
                        }
                        append(":")
                        append(getPrependedString(timer.remainingSeconds))
                    } else {
                        append(timer.remainingSeconds)
                    }
                },
                fontSize = fontSize,
                modifier = Modifier
                    .align(Alignment.Center)
                    .animateContentSize()
                    .alpha(timeAlpha),
            )
            val secondaryButtonAlpha by animateFloatAsState(targetValue = if (timer.hasStarted) 1f else 0f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(0.3f)
            ) {
                Surface(
                    onClick = {
                        if (timer.isPaused) {
                            onResetRequest(timer.id)
                        } else {
                            onIncrementTimerRequest(timer.id)
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .alpha(secondaryButtonAlpha)
                ) {
                    AnimatedContent(targetState = timer.isPaused) {
                        Text(
                            text = stringResource(id = if (it) R.string.reset else R.string.plus_one_minute),
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalControlButtons(
    showDeleteButton: Boolean,
    showPlayOrPauseButton: Boolean,
    centerButtonState: CenterButtonState,
    showAddButton: Boolean,
    onDeleteClicked: () -> Unit,
    onPlayOrPauseClicked: () -> Unit,
    onAddClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
    ) {
        val leftButtonAlpha by animateFloatAsState(targetValue = if (showDeleteButton) 1f else 0f)
        DeleteButton(
            modifier = Modifier
                .size(2 * ButtonSizeVertical / 3)
                .clip(CircleShape)
                .alpha(leftButtonAlpha),
            onClick = onDeleteClicked,
        )
        val transition = updateTransition(targetState = centerButtonState, label = null)
        val aspectRatio by transition.animateFloat(label = ButtonAspectRatioAnimation) {
            when (it) {
                CenterButtonState.PLAY -> 1f
                CenterButtonState.PAUSE,
                CenterButtonState.STOP -> 1.5f
            }
        }
        val cornerRadius by transition.animateDp(label = ButtonCornerRadiusAnimation) {
            val ratio = when (it) {
                CenterButtonState.PLAY -> 2
                CenterButtonState.PAUSE,
                CenterButtonState.STOP -> 4
            }
            ButtonSizeVertical / ratio
        }
        val centerButtonAlpha by animateFloatAsState(targetValue = if (showPlayOrPauseButton) 1f else 0f)
        CenterButton(
            modifier = Modifier
                .height(ButtonSizeVertical)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(cornerRadius))
                .alpha(centerButtonAlpha),
            onClick = onPlayOrPauseClicked,
            state = centerButtonState
        )
        val rightButtonAlpha by animateFloatAsState(targetValue = if (showAddButton) 1f else 0f)
        AddButton(
            modifier = Modifier
                .size(2 * ButtonSizeVertical / 3)
                .clip(CircleShape)
                .alpha(rightButtonAlpha),
            onClick = onAddClicked
        )
    }
}

@Composable
private fun VerticalControlButtons(
    showDeleteButton: Boolean,
    showPlayOrPauseButton: Boolean,
    centerButtonState: CenterButtonState,
    showAddButton: Boolean,
    onDeleteClicked: () -> Unit,
    onPlayOrPauseClicked: () -> Unit,
    onAddClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(1.5 * ButtonSizeHorizontal),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val topButtonAlpha by animateFloatAsState(targetValue = if (showAddButton) 1f else 0f)
        AddButton(
            modifier = Modifier
                .size(4 * ButtonSizeHorizontal / 5)
                .clip(CircleShape)
                .alpha(topButtonAlpha),
            onClick = onAddClicked
        )
        val transition = updateTransition(targetState = centerButtonState, label = null)
        val aspectRatio by transition.animateFloat(label = ButtonAspectRatioAnimation) {
            when (it) {
                CenterButtonState.PLAY -> 1f
                CenterButtonState.PAUSE,
                CenterButtonState.STOP -> 1.25f
            }
        }
        val cornerRadius by transition.animateDp(label = ButtonCornerRadiusAnimation) {
            val ratio = when (it) {
                CenterButtonState.PLAY -> 2
                CenterButtonState.PAUSE,
                CenterButtonState.STOP -> 4
            }
            ButtonSizeHorizontal / ratio
        }
        val centerButtonAlpha by animateFloatAsState(targetValue = if (showPlayOrPauseButton) 1f else 0f)
        CenterButton(
            modifier = Modifier
                .height(ButtonSizeHorizontal)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(cornerRadius))
                .alpha(centerButtonAlpha),
            onClick = onPlayOrPauseClicked,
            state = centerButtonState
        )
        val bottomButtonAlpha by animateFloatAsState(targetValue = if (showDeleteButton) 1f else 0f)
        DeleteButton(
            modifier = Modifier
                .size(4 * ButtonSizeHorizontal / 5)
                .clip(CircleShape)
                .alpha(bottomButtonAlpha),
            onClick = onDeleteClicked,
        )
    }
}

private enum class CenterButtonState {
    PLAY,
    PAUSE,
    STOP
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CenterButton(
    state: CenterButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        modifier = modifier,
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        AnimatedContent(targetState = state) {
            Icon(
                painter = painterResource(
                    id = when (it) {
                        CenterButtonState.PLAY -> R.drawable.baseline_play_arrow_24
                        CenterButtonState.PAUSE -> R.drawable.baseline_pause_24
                        CenterButtonState.STOP -> R.drawable.baseline_stop_24
                    }
                ),
                contentDescription = stringResource(id = R.string.timer_start_stop_button_content_desc)
            )
        }
    }
}

@Composable
private fun AddButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(id = R.string.add_timer_button_content_desc)
        )
    }
}

@Composable
fun DeleteButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(id = R.string.timer_delete_button_content_desc)
        )
    }
}