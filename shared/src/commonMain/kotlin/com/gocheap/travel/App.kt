package com.gocheap.travel

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.gocheap.travel.navigation.AppNavHost
import com.gocheap.travel.ui.theme.FlightTheme

@Composable
@Preview
fun App() {
    // Named explicitly rather than left to Coil's default, so images go over the Ktor engine this
    // app already ships on each target instead of pulling in a second HTTP stack behind them.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            // The supplier's photos arrive long after the card does; a fade is the difference
            // between a card filling in and a card flickering.
            .crossfade(true)
            .build()
    }

    FlightTheme {
        AppNavHost()
    }
}
