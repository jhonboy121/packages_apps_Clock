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

package com.flamingo.clock.ui

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

import com.flamingo.clock.ui.screens.MainScreen
import com.flamingo.clock.ui.theme.ClockTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController

class ClockActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClockTheme {
                val systemUiController = rememberSystemUiController()
                val surfaceColor = MaterialTheme.colorScheme.surface
                LaunchedEffect(Unit) {
                    systemUiController.setSystemBarsColor(surfaceColor)
                }
                val windowSizeClass = calculateWindowSizeClass(activity = this)
                val navigationType =
                    if (windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact) {
                        NavigationType.BottomBar
                    } else {
                        when (windowSizeClass.widthSizeClass) {
                            WindowWidthSizeClass.Compact -> NavigationType.BottomBar
                            WindowWidthSizeClass.Medium -> NavigationType.SideRail
                            WindowWidthSizeClass.Expanded -> NavigationType.PermanentDrawer
                            else -> NavigationType.BottomBar
                        }
                    }
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    windowSizeClass = windowSizeClass,
                    navigationType = navigationType
                )
            }
        }
    }
}

sealed interface NavigationType {
    object BottomBar : NavigationType
    object SideRail : NavigationType
    object PermanentDrawer : NavigationType
}
