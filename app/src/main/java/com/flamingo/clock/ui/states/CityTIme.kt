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

package com.flamingo.clock.ui.states

import com.flamingo.clock.data.Resolution
import com.flamingo.clock.data.Time
import com.flamingo.clock.data.tz.CityTimeZone

import java.time.ZoneId

data class CityTime(
    val city: String,
    val country: String,
    val timezone: String,
    val id: String = city + country + timezone,
    val time: Time
) {
    fun toCityTimeZone(): CityTimeZone {
        return CityTimeZone.newBuilder()
            .setCity(city)
            .setCountry(country)
            .setTimezone(timezone)
            .build()
    }

    companion object {
        fun fromCityTimeZone(cityTimeZone: CityTimeZone): CityTime {
            return CityTime(
                city = cityTimeZone.city,
                country = cityTimeZone.country,
                timezone = cityTimeZone.timezone,
                time = Time.now(
                    ZoneId.of(cityTimeZone.timezone),
                    resolution = Resolution.MINUTE,
                    ignoreTimeZoneDifference = false
                )
            )
        }
    }
}