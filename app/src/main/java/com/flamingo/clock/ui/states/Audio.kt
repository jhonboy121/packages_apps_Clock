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

import android.net.Uri
import androidx.annotation.DrawableRes
import com.flamingo.clock.R

sealed interface Audio {
    val title: String
    val uri: Uri
    val iconRes: Int

    data class Silent(
        override val title: String,
        override val uri: Uri = Uri.EMPTY,
        @DrawableRes override val iconRes: Int = R.drawable.outline_ringer_mute_24
    ) : Audio
}

data class DeviceAudio(
    override val title: String,
    override val uri: Uri,
    @DrawableRes override val iconRes: Int = R.drawable.outline_ringer_24
) : Audio

data class UserAudio(
    override val title: String,
    override val uri: Uri,
    @DrawableRes override val iconRes: Int = R.drawable.baseline_library_music_24
) : Audio