/*
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.flamingo.clock.koin

import android.media.RingtoneManager

import com.flamingo.clock.data.tz.CityTimeZonesData
import com.flamingo.clock.data.tz.CityTimeZoneRepository
import com.flamingo.clock.data.settings.SettingsRepository
import com.flamingo.clock.data.user.UserDataRepository

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val clockModule = module {
    single {
        UserDataRepository(context = androidContext())
    }
    single {
        CityTimeZonesData(context = androidContext())
    }
    single {
        CityTimeZoneRepository(cityTimeZonesData = get())
    }
    single {
        SettingsRepository(context = androidContext())
    }
    single {
        RingtoneManager(androidContext()).apply {
            setType(RingtoneManager.TYPE_ALARM)
        }
    }
}