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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

import com.flamingo.clock.ui.screens.AddCityTimeScreen
import com.flamingo.clock.ui.screens.MainScreen
import com.flamingo.clock.ui.screens.SettingsScreen
import com.flamingo.clock.ui.screens.TimerSoundScreen
import com.flamingo.clock.ui.theme.ClockTheme
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController

private const val TransitionAnimationDuration = 500

class ClockActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ClockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val windowSizeClass = calculateWindowSizeClass(activity = this)
                    val navController = rememberAnimatedNavController()
                    AnimatedNavHost(navController = navController, startDestination = Main.path) {
                        mainGraph(windowSizeClass = windowSizeClass, navController = navController)
                        settingsGraph(navController = navController)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    private fun NavGraphBuilder.mainGraph(
        windowSizeClass: WindowSizeClass,
        navController: NavHostController
    ) {
        slidingComposable(
            route = Main.path,
            startingRoutes = emptyList(),
            destinationRoutes = listOf(Settings.path, AddCityTime.path)
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
                if (windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact ||
                    windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
                ) {
                    ContentOrientation.Horizontal
                } else {
                    ContentOrientation.Vertical
                }
            MainScreen(
                modifier = Modifier
                    .systemBarsPadding()
                    .fillMaxSize(),
                navigationType = navigationType,
                orientation = contentOrientation,
                navController = navController
            )
        }
        slidingComposable(
            route = AddCityTime.path,
            startingRoutes = listOf(Main.path),
            destinationRoutes = emptyList()
        ) {
            val sideNavigationBarPadding =
                with(LocalDensity.current) {
                    WindowInsets.navigationBars.getLeft(
                        this,
                        LocalLayoutDirection.current
                    ).toDp()
                }
            AddCityTimeScreen(
                navController = navController,
                modifier = Modifier
                    .statusBarsPadding()
                    .then(
                        if (sideNavigationBarPadding.value != 0f) {
                            Modifier.navigationBarsPadding()
                        } else {
                            Modifier
                        }
                    )
                    .fillMaxSize(),
                isEnterAnimationRunning = transition.currentState == EnterExitState.PreEnter
            )
        }
    }

    private fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
        slidingComposable(
            route = Settings.path,
            startingRoutes = listOf(Main.path),
            destinationRoutes = listOf(TimerSound.path)
        ) {
            SettingsScreen(navController = navController, modifier = Modifier.fillMaxSize())
        }
        slidingComposable(
            route = TimerSound.path,
            startingRoutes = listOf(Settings.path),
            destinationRoutes = emptyList()
        ) {
            TimerSoundScreen(navController = navController, modifier = Modifier.fillMaxSize())
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    fun NavGraphBuilder.slidingComposable(
        route: String,
        startingRoutes: List<String>,
        destinationRoutes: List<String>,
        content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
    ) {
        composable(
            route = route,
            enterTransition = {
                if (startingRoutes.contains(initialState.destination.route)) {
                    slideIntoContainer(
                        AnimatedContentScope.SlideDirection.Start,
                        tween(TransitionAnimationDuration)
                    )
                } else {
                    null
                }
            },
            exitTransition = {
                if (destinationRoutes.contains(targetState.destination.route)) {
                    slideOutOfContainer(
                        AnimatedContentScope.SlideDirection.Start,
                        tween(TransitionAnimationDuration)
                    )
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (destinationRoutes.contains(initialState.destination.route)) {
                    slideIntoContainer(
                        AnimatedContentScope.SlideDirection.End,
                        tween(TransitionAnimationDuration)
                    )
                } else {
                    null
                }
            },
            popExitTransition = {
                if (startingRoutes.contains(targetState.destination.route)) {
                    slideOutOfContainer(
                        AnimatedContentScope.SlideDirection.End,
                        tween(TransitionAnimationDuration)
                    )
                } else {
                    null
                }
            },
            content = content
        )
    }
}

sealed class Route(val path: String)
object Main : Route("main")
object AddCityTime : Route("add_city_time")
object Settings : Route("settings")
object TimerSound : Route("timer_sound")

sealed interface NavigationType {
    object BottomBar : NavigationType
    object SideRail : NavigationType
    object PermanentDrawer : NavigationType
}

sealed interface ContentOrientation {
    object Vertical : ContentOrientation
    object Horizontal : ContentOrientation
}
