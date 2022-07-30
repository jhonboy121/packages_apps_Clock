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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.flamingo.clock.R
import com.flamingo.clock.ui.states.TimerSoundScreenState
import com.flamingo.clock.ui.states.rememberTimerSoundScreenState
import com.flamingo.support.compose.ui.layout.CollapsingToolbarLayout
import com.flamingo.support.compose.ui.preferences.Preference
import com.flamingo.support.compose.ui.preferences.PreferenceGroupHeader

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerSoundScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    state: TimerSoundScreenState = rememberTimerSoundScreenState()
) {
    val userSounds by state.userAudio.collectAsState(emptyList())
    val deviceSounds by state.deviceAudio.collectAsState(emptyList())
    val selectedTimerSound by state.selectedAudio.collectAsState(initial = null)
    CollapsingToolbarLayout(
        modifier = modifier,
        title = stringResource(id = R.string.timer_sound),
        onBackButtonPressed = { navController.popBackStack() },
    ) {
        item(key = R.string.your_sounds) {
            PreferenceGroupHeader(
                title = stringResource(id = R.string.your_sounds),
                modifier = Modifier.animateItemPlacement()
            )
        }
        items(userSounds, key = { it.uri }) { userSound ->
            AudioPreference(
                modifier = Modifier.then(Modifier.animateItemPlacement()),
                title = userSound.title,
                iconRes = userSound.iconRes,
                isSelected = selectedTimerSound == userSound,
                onClick = {
                    state.setAsTimerSoundAndPlay(userSound)
                },
                isPlaying = state.nowPlayingSound == userSound
            )
        }
        item(key = R.string.add_new) {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = {
                    if (it != null) {
                        state.saveUserSound(it)
                    }
                }
            )
            Preference(
                title = stringResource(id = R.string.add_new),
                modifier = Modifier.animateItemPlacement(),
                startWidget = {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                },
                onClick = {
                    launcher.launch(arrayOf("audio/*"))
                }
            )
        }
        item(key = R.string.device_sounds) {
            PreferenceGroupHeader(
                title = stringResource(id = R.string.device_sounds),
                modifier = Modifier.animateItemPlacement()
            )
        }
        if (deviceSounds.isEmpty()) {
            item(key = R.string.loading_progress_indicator_key) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .size(64.dp)
                        .animateItemPlacement()
                )
            }
        } else {
            items(deviceSounds, key = { it.title }) { deviceAudio ->
                AudioPreference(
                    modifier = Modifier.then(Modifier.animateItemPlacement()),
                    title = deviceAudio.title,
                    iconRes = deviceAudio.iconRes,
                    isSelected = selectedTimerSound == deviceAudio,
                    onClick = {
                        state.setAsTimerSoundAndPlay(deviceAudio)
                    },
                    isPlaying = state.nowPlayingSound == deviceAudio
                )
            }
        }
    }
}

@Composable
fun AudioPreference(
    title: String,
    @DrawableRes iconRes: Int,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Preference(
        modifier = modifier,
        title = title,
        onClick = onClick,
        startWidget = {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        },
        endWidget = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    MusicEqualizer(Modifier.height(32.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
fun MusicEqualizer(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.music_equalizer))
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = MaterialTheme.colorScheme.primary.toArgb(),
            keyPath = arrayOf("**")
        )
    )
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier,
        alignment = Alignment.CenterEnd,
        dynamicProperties = dynamicProperties
    )
}