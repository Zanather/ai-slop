package io.github.zanather.clicker

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.zanather.clicker.crash.CrashLog
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
    private var started: AppContainer? = null

    val container: AppContainer
        get() = checkNotNull(started) { "App failed to start; the crash report has the cause" }

    // Earliest hook an app gets: runs before content providers, so crashes in library initializers are caught too.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        CrashLog.breadcrumb("app onCreate")
        // If startup itself throws, keep the process alive so MainActivity can show the report,
        // rather than dying the same way on every launch.
        started = try {
            AppContainer(this).also { CrashLog.breadcrumb("app ready") }
        } catch (e: Exception) {
            CrashLog.record(e)
            null
        }
    }

    // Channel icons come from the TV over the same Wi-Fi-pinned client as everything else.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .build()
}

val Context.container: AppContainer get() = (applicationContext as ClickerApp).container
