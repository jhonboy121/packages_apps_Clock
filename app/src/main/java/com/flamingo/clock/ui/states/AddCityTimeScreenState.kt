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

package com.flamingo.clock.ui.states

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

import com.flamingo.clock.data.CityTimeZone
import com.flamingo.clock.data.TimeFormat
import com.flamingo.clock.repositories.CityTimeZoneRepository
import com.flamingo.clock.repositories.SettingsRepository
import com.flamingo.clock.repositories.UserDataRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class AddCityTimeScreenState(
    private val coroutineScope: CoroutineScope,
    private val cityTimezoneRepository: CityTimeZoneRepository,
    private val userDataRepository: UserDataRepository,
    private val settingsRepository: SettingsRepository,
    private val context: Context
) {

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                time.value = System.currentTimeMillis()
            }
        }
    }

    private val time = MutableStateFlow(System.currentTimeMillis())

    private val _filteredCityTimes = MutableStateFlow<List<CityTimeZone>>(emptyList())
    val cityTimes: Flow<List<CityTime>>
        get() = _filteredCityTimes.combine(userDataRepository.cityTimeZones) { list, savedList ->
            list.filter {
                !savedList.contains(it)
            }
        }.combine(time) { list, _ ->
            list.map {
                CityTime.fromCityTimeZone(it)
            }
        }.flowOn(Dispatchers.Default)

    val timeFormat: Flow<TimeFormat>
        get() = settingsRepository.timeFormat

    fun findCityTime(keyword: String?) {
        coroutineScope.launch {
            cityTimezoneRepository.findCityTimezoneInfo(keyword).onSuccess {
                _filteredCityTimes.value = it
            }
        }
    }

    fun saveCityTime(cityTime: CityTime) {
        coroutineScope.launch {
            userDataRepository.saveCityTime(cityTime.toCityTimeZone())
        }
    }

    internal fun registerReceiver() {
        context.registerReceiver(
            broadcastReceiver,
            IntentFilter(Intent.ACTION_TIME_TICK)
        )
    }

    internal fun unregisterReceiver() {
        context.unregisterReceiver(broadcastReceiver)
    }
}

@Composable
fun rememberAddCityTimeScreenState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    cityTimezoneRepository: CityTimeZoneRepository = get(),
    userDataRepository: UserDataRepository = get(),
    settingsRepository: SettingsRepository = get(),
    context: Context = LocalContext.current
): AddCityTimeScreenState {
    val state = remember(
        coroutineScope,
        cityTimezoneRepository,
        userDataRepository,
        settingsRepository,
        context
    ) {
        AddCityTimeScreenState(
            coroutineScope = coroutineScope,
            cityTimezoneRepository = cityTimezoneRepository,
            userDataRepository = userDataRepository,
            settingsRepository = settingsRepository,
            context = context
        )
    }
    DisposableEffect(state) {
        state.registerReceiver()
        onDispose {
            state.unregisterReceiver()
        }
    }
    return state
}
