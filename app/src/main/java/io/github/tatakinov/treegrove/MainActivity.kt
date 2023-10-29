package io.github.tatakinov.treegrove

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.tatakinov.treegrove.ui.theme.TreeGroveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val networkViewModel = NetworkViewModel()
        lifecycle.addObserver(networkViewModel)
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val isWifi = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (isWifi == null || !isWifi) {
            networkViewModel.setNetworkState(NetworkState.Other)
        }
        else {
            networkViewModel.setNetworkState(NetworkState.Wifi)
        }
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val isWifi = connectivityManager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (isWifi == null || !isWifi) {
                    networkViewModel.setNetworkState(NetworkState.Other)
                }
                else {
                    networkViewModel.setNetworkState(NetworkState.Wifi)
                }
            }
        })
        /*
        with(WindowCompat.getInsetsController(window, window.decorView)) {
            systemBarsBehavior  = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
            hide(WindowInsetsCompat.Type.systemBars())
        }
         */
        setContent {
            val coroutineScope  = rememberCoroutineScope()
            val navController   = rememberNavController()
            val context : Context = this
            TreeGroveTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "loading") {
                        composable("loading") {
                            LaunchedEffect(Unit) {
                                if (!Config.isLoaded()) {
                                    Config.load(context, onLoad = {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            if (it.relayList.isEmpty()) {
                                                if (navController.currentDestination?.route == "loading") {
                                                    navController.popBackStack()
                                                    navController.navigate("post")
                                                    navController.navigate("setting")
                                                }
                                            }
                                            else {
                                                if (navController.currentDestination?.route == "loading") {
                                                    navController.popBackStack()
                                                    navController.navigate("post")
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                        }
                        composable("post") {
                            MainView(onNavigate = {
                                navController.navigate("setting")
                            }, networkViewModel)
                        }
                        composable("setting") {
                            SettingView(onUpdated = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    Config.save(context)
                                }
                                navController.popBackStack()
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

