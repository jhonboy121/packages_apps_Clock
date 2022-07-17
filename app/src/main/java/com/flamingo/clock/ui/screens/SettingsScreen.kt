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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController

import com.flamingo.clock.R
import com.flamingo.clock.data.ClockStyle
import com.flamingo.clock.data.DEFAULT_CLOCK_STYLE
import com.flamingo.clock.data.DEFAULT_SHOW_SECONDS
import com.flamingo.clock.data.DEFAULT_TIME_FORMAT
import com.flamingo.clock.data.TimeFormat
import com.flamingo.clock.ui.states.SettingsScreenState
import com.flamingo.clock.ui.states.rememberSettingsScreenState
import com.flamingo.support.compose.ui.layout.CollapsingToolbarLayout
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
        title = stringResource(id = R.string.settings),
        onBackButtonPressed = { navController.popBackStack() }
    ) {
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
}