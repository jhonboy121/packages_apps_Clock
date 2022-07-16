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
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.flamingo.clock.ui.screens.AddCityTimeScreen

import com.flamingo.clock.ui.screens.MainScreen
import com.flamingo.clock.ui.theme.ClockTheme
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private const val TransitionAnimationDuration = 500

class ClockActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalAnimationApi::class)
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
                val navController = rememberAnimatedNavController()
                AnimatedNavHost(navController = navController, startDestination = Main.path) {
                    mainGraph(windowSizeClass = windowSizeClass, navController = navController)
                }
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    private fun NavGraphBuilder.mainGraph(
        windowSizeClass: WindowSizeClass,
        navController: NavHostController
    ) {
        composable(
            Main.path,
            exitTransition = {
                when (targetState.destination.route) {
                    AddCityTime.path -> slideOutOfContainer(
                        AnimatedContentScope.SlideDirection.Start,
                        tween(TransitionAnimationDuration)
                    )
                    else -> null
                }
            },
            popEnterTransition = {
                when (initialState.destination.route) {
                    AddCityTime.path -> slideIntoContainer(
                        AnimatedContentScope.SlideDirection.End,
                        tween(TransitionAnimationDuration)
                    )
                    else -> null
                }
            },
        ) {
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
            val contentOrientation =
                if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                    ContentOrientation.Horizontal
                } else {
                    ContentOrientation.Vertical
                }
            MainScreen(
                modifier = Modifier.fillMaxSize(),
                navigationType = navigationType,
                orientation = contentOrientation,
                navController = navController
            )
        }
        animatedComposable(
            route = AddCityTime.path,
            home = Main.path
        ) {
            AddCityTimeScreen(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                isEnterAnimationRunning = transition.currentState == EnterExitState.PreEnter
            )
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    fun NavGraphBuilder.animatedComposable(
        route: String,
        home: String,
        content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
    ) {
        composable(
            route,
            enterTransition = {
                when (initialState.destination.route) {
                    home -> slideIntoContainer(
                        AnimatedContentScope.SlideDirection.Start,
                        tween(TransitionAnimationDuration)
                    )
                    else -> null
                }
            },
            popExitTransition = {
                when (targetState.destination.route) {
                    home -> slideOutOfContainer(
                        AnimatedContentScope.SlideDirection.End,
                        tween(TransitionAnimationDuration)
                    )
                    else -> null
                }
            },
            content = content
        )
    }
}

sealed class Route(val path: String)
object Main : Route("main")
object AddCityTime : Route("add_city_time")

sealed interface NavigationType {
    object BottomBar : NavigationType
    object SideRail : NavigationType
    object PermanentDrawer : NavigationType
}

sealed interface ContentOrientation {
    object Vertical : ContentOrientation
    object Horizontal : ContentOrientation
}
