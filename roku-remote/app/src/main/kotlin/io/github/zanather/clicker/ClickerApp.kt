package io.github.zanather.clicker

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.zanather.clicker.data.DeviceStore
import io.github.zanather.clicker.ecp.EcpClient
import io.github.zanather.clicker.net.LanNetwork
import io.github.zanather.clicker.session.RokuSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.dataStore by preferencesDataStore(name = "clicker")

/** Process-wide singletons. Hand-wired: a remote doesn't need a DI framework slowing its cold start. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val lan = LanNetwork(context)
    val http = EcpClient.defaultHttpClient(lan.socketFactory)
    val ecp = EcpClient(http)
    val store = DeviceStore(context.dataStore)
    val session = RokuSession(scope, ecp, store, lan)
}

class ClickerApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // Channel icons come from the TV over the same Wi-Fi-pinned client as everything else.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .build()
}

val Context.container: AppContainer get() = (applicationContext as ClickerApp).container
