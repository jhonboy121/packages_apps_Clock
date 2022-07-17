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

package com.flamingo.clock.repositories

import com.flamingo.clock.data.CityTimeZone
import com.flamingo.clock.data.CityTimeZonesData

class CityTimeZoneRepository(private val cityTimeZonesData: CityTimeZonesData) {

    suspend fun getAllTimeZoneInfo(): Result<List<CityTimeZone>> {
        return cityTimeZonesData.getAllTimeZones()
    }

    suspend fun findCityTimeZoneInfo(keyword: String?): Result<List<CityTimeZone>> {
        return cityTimeZonesData.findCityTimezoneInfo(keyword)
    }
}