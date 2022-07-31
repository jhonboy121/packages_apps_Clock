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

package com.flamingo.clock.data.user

import android.content.Context

import com.flamingo.clock.data.tz.CityTimeZone

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserDataRepository(context: Context) {

    private val userData = context.userData

    val cityTimeZones: Flow<List<CityTimeZone>> = userData.data.map { it.cityTimeZonesList }

    suspend fun saveCityTime(cityTimeZone: CityTimeZone) {
        userData.updateData {
            it.toBuilder()
                .addCityTimeZones(cityTimeZone)
                .build()
        }
    }

    suspend fun removeCityTime(cityTimeZone: CityTimeZone) {
        userData.updateData { data ->
            val index = data.cityTimeZonesList.indexOf(cityTimeZone).takeIf { it >= 0 } ?: return@updateData data
            data.toBuilder()
                .removeCityTimeZones(index)
                .build()
        }
    }
}