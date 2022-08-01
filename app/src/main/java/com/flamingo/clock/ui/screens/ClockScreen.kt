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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeableState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.swipeable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.navigation.NavController

import com.flamingo.clock.R
import com.flamingo.clock.data.Date
import com.flamingo.clock.data.Time
import com.flamingo.clock.data.settings.ClockStyle
import com.flamingo.clock.data.settings.DEFAULT_CLOCK_STYLE
import com.flamingo.clock.data.settings.DEFAULT_SHOW_SECONDS
import com.flamingo.clock.data.settings.DEFAULT_TIME_FORMAT
import com.flamingo.clock.data.settings.TimeFormat
import com.flamingo.clock.data.TimeZoneDifference
import com.flamingo.clock.data.getPrependedString
import com.flamingo.clock.ui.AddCityTime
import com.flamingo.clock.ui.ContentOrientation
import com.flamingo.clock.ui.states.CityTime
import com.flamingo.clock.ui.states.ClockScreenState
import com.flamingo.clock.ui.states.rememberClockScreenState
import com.flamingo.clock.ui.theme.ButtonSizeVertical
import com.flamingo.clock.ui.theme.ButtonSizeHorizontal

import kotlin.math.roundToInt

@Composable
fun ClockScreen(
    orientation: ContentOrientation,
    navController: NavController,
    nestedScrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier,
    state: ClockScreenState = rememberClockScreenState()
) {
    val time by state.time.collectAsState(Time.Zero)
    val clockStyle by state.clockStyle.collectAsState(initial = DEFAULT_CLOCK_STYLE)
    val showSeconds by state.showSeconds.collectAsState(initial = DEFAULT_SHOW_SECONDS)
    val timeFormat by state.timeFormat.collectAsState(initial = DEFAULT_TIME_FORMAT)
    val date by state.date.collectAsState(Date.Unspecified)
    val selectedCityTimes by state.cityTimes.collectAsState(initial = emptyList())
    val addCityTimeCallback by rememberUpdatedState(newValue = {
        navController.navigate(AddCityTime.path)
    })
    val deleteCallback by rememberUpdatedState(newValue = { cityTime: CityTime ->
        state.removeSavedCityTime(cityTime)
    })
    val homeTime by state.homeTime.collectAsState(initial = null)
    val deleteHomeTimeCallback by rememberUpdatedState(newValue = { state.removeHomeTimeZone() })
    when (orientation) {
        ContentOrientation.Horizontal -> HorizontalContent(
            modifier = modifier.padding(horizontal = 24.dp),
            time = time,
            showSeconds = showSeconds,
            clockStyle = clockStyle,
            timeFormat = timeFormat,
            date = date,
            selectedCityTimes = selectedCityTimes,
            onShowAddCityTimePageRequest = addCityTimeCallback,
            onDeleteRequest = deleteCallback,
            nestedScrollConnection = nestedScrollConnection,
            homeTime = homeTime,
            onDeleteHomeTimeRequest = deleteHomeTimeCallback
        )
        ContentOrientation.Vertical -> VerticalContent(
            modifier = modifier.padding(horizontal = 24.dp),
            time = time,
            showSeconds = showSeconds,
            clockStyle = clockStyle,
            timeFormat = timeFormat,
            date = date,
            selectedCityTimes = selectedCityTimes,
            onShowAddCityTimePageRequest = addCityTimeCallback,
            onDeleteRequest = deleteCallback,
            nestedScrollConnection = nestedScrollConnection,
            homeTime = homeTime,
            onDeleteHomeTimeRequest = deleteHomeTimeCallback
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HorizontalContent(
    time: Time,
    date: Date,
    showSeconds: Boolean,
    clockStyle: ClockStyle,
    timeFormat: TimeFormat,
    selectedCityTimes: List<CityTime>,
    onShowAddCityTimePageRequest: () -> Unit,
    onDeleteRequest: (CityTime) -> Unit,
    nestedScrollConnection: NestedScrollConnection,
    homeTime: CityTime?,
    onDeleteHomeTimeRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Time(
            time = time,
            showSeconds = showSeconds,
            style = clockStyle,
            timeFormat = timeFormat,
            date = date,
            modifier = Modifier
                .fillMaxSize()
                .weight(0.35f)
        )
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxSize()
                .weight(0.65f)
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (homeTime != null) {
                item(key = homeTime.id) {
                    CityTimeItem(
                        cityTime = homeTime,
                        timeFormat = timeFormat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement(),
                        onDeleteRequest = onDeleteHomeTimeRequest
                    )
                }
            }
            items(selectedCityTimes, key = { it.id }) {
                CityTimeItem(
                    cityTime = it,
                    timeFormat = timeFormat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement(),
                    onDeleteRequest = {
                        onDeleteRequest(it)
                    }
                )
            }
        }
        AddButton(
            onClick = onShowAddCityTimePageRequest,
            modifier = Modifier.size(ButtonSizeHorizontal)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerticalContent(
    time: Time,
    date: Date,
    showSeconds: Boolean,
    clockStyle: ClockStyle,
    timeFormat: TimeFormat,
    selectedCityTimes: List<CityTime>,
    onShowAddCityTimePageRequest: () -> Unit,
    onDeleteRequest: (CityTime) -> Unit,
    nestedScrollConnection: NestedScrollConnection,
    homeTime: CityTime?,
    onDeleteHomeTimeRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = ButtonSizeVertical + 2 * 24.dp)
        ) {
            item {
                Time(
                    time = time,
                    showSeconds = showSeconds,
                    style = clockStyle,
                    timeFormat = timeFormat,
                    date = date,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
            if (homeTime != null) {
                item(key = homeTime.id) {
                    CityTimeItem(
                        cityTime = homeTime,
                        timeFormat = timeFormat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement(),
                        onDeleteRequest = onDeleteHomeTimeRequest
                    )
                }
            }
            items(selectedCityTimes, key = { it.id }) {
                CityTimeItem(
                    cityTime = it,
                    timeFormat = timeFormat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement(),
                    onDeleteRequest = {
                        onDeleteRequest(it)
                    }
                )
            }
        }
        AddButton(
            onClick = onShowAddCityTimePageRequest,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(ButtonSizeVertical)
        )
    }
}

@Composable
fun Time(
    time: Time,
    showSeconds: Boolean,
    style: ClockStyle,
    timeFormat: TimeFormat,
    date: Date,
    modifier: Modifier = Modifier
) {
    when (style) {
        ClockStyle.DIGITAL -> DigitalTime(
            time = time,
            showSeconds = showSeconds,
            timeFormat = timeFormat,
            date = date,
            modifier = modifier
        )
        ClockStyle.ANALOG -> {}
        else -> throw IllegalArgumentException("Unrecognised clock style $style")
    }
}

@OptIn(ExperimentalUnitApi::class)
@Composable
fun DigitalTime(
    time: Time,
    showSeconds: Boolean,
    timeFormat: TimeFormat,
    date: Date?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit(72f, TextUnitType.Sp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontSize = fontSize,
                        letterSpacing = TextUnit(3f, TextUnitType.Sp)
                    )
                ) {
                    append(
                        when (timeFormat) {
                            TimeFormat.TWELVE_HOUR -> time.withTwelveHourFormat().hour
                            TimeFormat.TWENTY_FOUR_HOUR -> time.hour
                            else -> throw IllegalArgumentException("Unrecognised time format $timeFormat")
                        }.toString()
                    )
                    append(":")
                    append(getPrependedString(time.minute))
                    if (showSeconds) {
                        append(":")
                        append(getPrependedString(time.second))
                    }
                }
                if (timeFormat == TimeFormat.TWELVE_HOUR) {
                    append(' ')
                    withStyle(
                        style = SpanStyle(
                            fontSize = TextUnit(
                                fontSize.value / 3,
                                fontSize.type
                            )
                        )
                    ) {
                        if (time.hour < 12) {
                            append("AM")
                        } else {
                            append("PM")
                        }
                    }
                }
            },
            maxLines = 1
        )
        if (date != null) {
            Text(
                text = "${date.day}, ${date.month} ${date.date}",
                fontSize = TextUnit(18f, TextUnitType.Sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.clip(CircleShape),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(id = R.string.add_city_time_content_desc)
        )
    }
}

enum class SwipeState {
    LEFT,
    NEUTRAL,
    RIGHT
}

@OptIn(ExperimentalUnitApi::class, ExperimentalMaterialApi::class)
@Composable
private fun CityTimeItem(
    cityTime: CityTime,
    timeFormat: TimeFormat,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorColor = MaterialTheme.colorScheme.error
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 32.dp.toPx() }
    val painter = rememberVectorPainter(image = Icons.Filled.Delete)
    val swipeableState = remember(cityTime.id) { SwipeableState(initialValue = SwipeState.NEUTRAL) }
    val isSwipedOff by remember {
        derivedStateOf {
            swipeableState.currentValue == SwipeState.LEFT ||
                    swipeableState.currentValue == SwipeState.RIGHT
        }
    }
    val offset by remember {
        derivedStateOf {
            IntOffset(
                x = if (isSwipedOff) {
                    Int.MAX_VALUE
                } else {
                    swipeableState.offset.value.roundToInt()
                },
                y = 0
            )
        }
    }
    LaunchedEffect(isSwipedOff) {
        if (isSwipedOff) {
            onDeleteRequest()
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = errorColor,
                    cornerRadius = CornerRadius(
                        x = cornerRadiusPx,
                        y = cornerRadiusPx
                    ),
                    style = Fill,
                    alpha = if (swipeableState.offset.value == 0f) 0f else 1f
                )
                translate(
                    cornerRadiusPx,
                    (size.height - painter.intrinsicSize.height) / 2
                ) {
                    with(painter) {
                        draw(
                            size = intrinsicSize,
                            alpha = if (swipeableState.progress.to == SwipeState.RIGHT) 1f else 0f
                        )
                    }
                }
                translate(
                    size.width - (2 * cornerRadiusPx),
                    (size.height - painter.intrinsicSize.height) / 2
                ) {
                    with(painter) {
                        draw(
                            size = intrinsicSize,
                            alpha = if (swipeableState.progress.to == SwipeState.LEFT) 1f else 0f
                        )
                    }
                }
            }
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .swipeable(
                    state = swipeableState,
                    anchors = mapOf(
                        -maxWidthPx to SwipeState.LEFT,
                        0f to SwipeState.NEUTRAL,
                        maxWidthPx to SwipeState.RIGHT
                    ),
                    orientation = Orientation.Horizontal,
                    thresholds = { _, _ ->
                        FractionalThreshold(0.4f)
                    }
                )
                .offset {
                    offset
                },
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            shape = RoundedCornerShape(32.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    Text(text = cityTime.city)
                    TimeZoneDifference(diff = cityTime.time.timeZoneDifference)
                }
                DigitalTime(
                    time = cityTime.time,
                    showSeconds = false,
                    timeFormat = timeFormat,
                    date = null,
                    fontSize = TextUnit(48f, TextUnitType.Sp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun TimeZoneDifference(diff: TimeZoneDifference) {
    when (diff) {
        is TimeZoneDifference.Today -> {
            Text(text = buildString {
                if (diff.sign > 0)
                    append("+")
                else if (diff.sign < 0)
                    append("-")

                if (diff.hour > 0) {
                    append(diff.hour)
                    append("h ")
                }
                if (diff.minute > 0) {
                    append(diff.minute)
                    append("m ")
                }
            })
        }
        is TimeZoneDifference.Tomorrow -> {
            Text(text = buildString {
                append("+")
                if (diff.hour > 0) {
                    append(diff.hour)
                    append("h ")
                }
                if (diff.minute > 0) {
                    append(diff.minute)
                    append("m ")
                }
                append(stringResource(id = R.string.tomorrow))
            })
        }
        is TimeZoneDifference.Yesterday -> {
            Text(text = buildString {
                append("-")
                if (diff.hour > 0) {
                    append(diff.hour)
                    append("h ")
                }
                if (diff.minute > 0) {
                    append(diff.minute)
                    append("m ")
                }
                append(stringResource(id = R.string.yesterday))
            })
        }
        else -> {}
    }
}