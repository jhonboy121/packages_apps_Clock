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

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController

import com.flamingo.clock.R
import com.flamingo.clock.data.settings.ClockStyle
import com.flamingo.clock.data.settings.DEFAULT_CLOCK_STYLE
import com.flamingo.clock.data.settings.DEFAULT_SHOW_SECONDS
import com.flamingo.clock.data.settings.DEFAULT_TIMER_VOLUME_RISE_DURATION
import com.flamingo.clock.data.settings.DEFAULT_TIME_FORMAT
import com.flamingo.clock.data.settings.DEFAULT_VIBRATE_FOR_TIMERS
import com.flamingo.clock.data.settings.TimeFormat
import com.flamingo.clock.ui.TimerSound
import com.flamingo.clock.ui.states.SettingsScreenState
import com.flamingo.clock.ui.states.rememberSettingsScreenState
import com.flamingo.support.compose.ui.layout.CollapsingToolbarLayout
import com.flamingo.support.compose.ui.preferences.DiscreteSeekBarPreference
import com.flamingo.support.compose.ui.preferences.Entry
import com.flamingo.support.compose.ui.preferences.ListPreference
import com.flamingo.support.compose.ui.preferences.Preference
import com.flamingo.support.compose.ui.preferences.PreferenceGroupHeader
import com.flamingo.support.compose.ui.preferences.SwitchPreference

@Composable
fun SettingsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    state: SettingsScreenState = rememberSettingsScreenState()
) {
    CollapsingToolbarLayout(
        modifier = modifier,
        title = stringResource(id = R.string.settings),
        onBackButtonPressed = { navController.popBackStack() }
    ) {
        clockSettings(state = state)
        timerSettings(
            state = state,
            openTimerSoundScreen = {
                navController.navigate(TimerSound.path)
            }
        )
    }
}

fun LazyListScope.clockSettings(state: SettingsScreenState) {
    item(key = R.string.clock) {
        PreferenceGroupHeader(title = stringResource(id = R.string.clock))
    }
    item(key = R.string.clock_style) {
        val style by state.clockStyle.collectAsState(initial = DEFAULT_CLOCK_STYLE)
        ListPreference(
            title = stringResource(id = R.string.clock_style),
            entries = listOf(
                Entry(stringResource(id = R.string.analog), ClockStyle.ANALOG),
                Entry(stringResource(id = R.string.digital), ClockStyle.DIGITAL)
            ),
            value = style,
            onEntrySelected = {
                state.setClockStyle(it)
            }
        )
    }
    item(key = R.string.show_seconds) {
        val showSeconds by state.showSeconds.collectAsState(initial = DEFAULT_SHOW_SECONDS)
        SwitchPreference(
            title = stringResource(id = R.string.show_seconds),
            checked = showSeconds,
            onCheckedChange = {
                state.setShowSeconds(it)
            }
        )
    }
    item(key = R.string.time_format) {
        val timeFormat by state.timeFormat.collectAsState(initial = DEFAULT_TIME_FORMAT)
        ListPreference(
            title = stringResource(id = R.string.time_format),
            entries = listOf(
                Entry(stringResource(id = R.string.twelve_hour), TimeFormat.TWELVE_HOUR),
                Entry(
                    stringResource(id = R.string.twenty_four_hour),
                    TimeFormat.TWENTY_FOUR_HOUR
                )
            ),
            value = timeFormat,
            onEntrySelected = {
                state.setTimeFormat(it)
            }
        )
    }
    item(key = R.string.home_time_zone) {
        val allTimeZones by state.timeZoneEntries.collectAsState(emptyList())
        val homeTimeZone by state.homeTimeZone.collectAsState(initial = null)
        ListPreference(
            title = stringResource(id = R.string.home_time_zone),
            entries = allTimeZones,
            value = homeTimeZone,
            onEntrySelected = {
                state.setHomeTimeZone(it)
            }
        )
    }
    item(key = R.string.change_date_and_time) {
        Preference(
            title = stringResource(id = R.string.change_date_and_time),
            onClick = {
                state.openDateAndTimeSettings()
            },
        )
    }
}

fun LazyListScope.timerSettings(state: SettingsScreenState, openTimerSoundScreen: () -> Unit) {
    item(key = R.string.timer) {
        PreferenceGroupHeader(title = stringResource(id = R.string.timer))
    }
    item(key = R.string.timer_sound) {
        Preference(
            title = stringResource(id = R.string.timer_sound),
            onClick = {
                openTimerSoundScreen()
            }
        )
    }
    item(key = R.string.gradually_increase_volume_title) {
        val settingDuration by state.timerVolumeRiseDuration.collectAsState(
            DEFAULT_TIMER_VOLUME_RISE_DURATION
        )
        var duration by remember(settingDuration) { mutableStateOf(settingDuration) }
        DiscreteSeekBarPreference(
            title = stringResource(id = R.string.gradually_increase_volume_title),
            summary = stringResource(id = R.string.gradually_increase_volume_summary),
            min = 0,
            max = 60,
            value = duration,
            onProgressChanged = {
                duration = it
            },
            onProgressChangeFinished = {
                state.setTimerVolumeRiseDuration(duration)
            },
            showProgressText = true
        )
    }
    item(key = R.string.vibrate_for_timers) {
        val vibrateForTimers by state.vibrateForTimers.collectAsState(initial = DEFAULT_VIBRATE_FOR_TIMERS)
        SwitchPreference(
            title = stringResource(id = R.string.vibrate_for_timers),
            summary = stringResource(id = R.string.vibrate_for_timers_summary),
            checked = vibrateForTimers,
            onCheckedChange = {
                state.setVibrateForTimers(it)
            }
        )
    }
}