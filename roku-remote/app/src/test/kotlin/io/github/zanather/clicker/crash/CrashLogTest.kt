package io.github.zanather.clicker.crash

import android.app.Application
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ClickerApp
import io.github.zanather.clicker.MainActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = ClickerApp::class)
class CrashLogTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        CrashLog.clear(app)
    }

    @Test
    fun aRecordedCrashSendsTheNextLaunchToTheReport() {
        CrashLog.record(IllegalStateException("boom"))

        ActivityScenario.launch(MainActivity::class.java)

        val next: Intent = shadowOf(app).nextStartedActivity
        assertThat(next.component?.className).isEqualTo(CrashActivity::class.java.name)
    }

    @Test
    fun theReportShowsTheTraceAndDeviceDetails() {
        CrashLog.record(IllegalStateException("boom"))

        ActivityScenario.launch(CrashActivity::class.java).onActivity { activity ->
            val texts = mutableListOf<String>()
            fun collect(view: android.view.View) {
                if (view is TextView) texts += view.text.toString()
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            collect(activity.window.decorView)
            val report = texts.joinToString("\n")
            assertThat(report).contains("java.lang.IllegalStateException: boom")
            assertThat(report).contains("Android")
            assertThat(report).contains("Clicker ")
        }
    }

    @Test
    fun noCrashMeansANormalLaunch() {
        ActivityScenario.launch(MainActivity::class.java)

        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }
}
