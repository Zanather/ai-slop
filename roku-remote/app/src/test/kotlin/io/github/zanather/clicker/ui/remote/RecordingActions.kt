package io.github.zanather.clicker.ui.remote

import io.github.zanather.clicker.ecp.RokuKey
import io.github.zanather.clicker.ui.model.RemoteActions

/** Records every call, in order, as "name argument". */
internal class RecordingActions : RemoteActions {
    val calls = mutableListOf<String>()

    override fun press(key: RokuKey) {
        calls += "press ${key.ecpName}"
    }

    override fun holdStart(key: RokuKey) {
        calls += "holdStart ${key.ecpName}"
    }

    override fun holdEnd(key: RokuKey) {
        calls += "holdEnd ${key.ecpName}"
    }

    override fun launch(appId: String) {
        calls += "launch $appId"
    }

    override fun sendKeys(keys: List<RokuKey>) {
        calls += "sendKeys ${keys.joinToString(",") { it.ecpName }}"
    }

    override fun wake() {
        calls += "wake"
    }

    override fun retry() {
        calls += "retry"
    }

    override fun setTouchpadMode(enabled: Boolean) {
        calls += "setTouchpadMode $enabled"
    }
}
