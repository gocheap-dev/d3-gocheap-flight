package com.gocheap.travel

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.gocheap.travel.navigation.AppNavHost
import com.gocheap.travel.ui.theme.FlightTheme

@Composable
@Preview
fun App() {
    // Named explicitly rather than left to Coil's default, so images go over the Ktor engine this
    // app already ships on each target instead of pulling in a second HTTP stack behind them.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                // The supplier serves its brand logos as SVG — airline marks on a result card and
                // the advertiser's logo on an ad card both. Coil decodes nothing vector by
                // default, so without this the fetch succeeds, the decode finds no handler, and
                // the image silently resolves to nothing: a blank box where a logo should be,
                // with no error on screen and none in the log.
                add(SvgDecoder.Factory())
            }
            // The supplier's photos arrive long after the card does; a fade is the difference
            // between a card filling in and a card flickering.
            .crossfade(true)
            .build()
    }

    FlightTheme {
        AppNavHost()
    }
}
