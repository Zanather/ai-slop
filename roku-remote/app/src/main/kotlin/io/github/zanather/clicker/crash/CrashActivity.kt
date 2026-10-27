package io.github.zanather.clicker.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.zanather.clicker.MainActivity
import io.github.zanather.clicker.R

/**
 * Shows the last crash (if any) and the recent lifecycle trail, with buttons to copy or share them. Opened after
 * a crash, or from the "Clicker log" launcher icon when the app misbehaves without crashing. Plain platform views
 * on purpose: whatever went wrong may be in Compose or the app's own wiring, so this screen depends on neither.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.breadcrumb("CrashActivity shown")
        val crash = CrashLog.read(this)
        val report = buildString {
            if (crash != null) {
                appendLine(crash)
                appendLine()
            }
            appendLine("Recent activity:")
            append(CrashLog.trail(this@CrashActivity).lines().takeLast(TRAIL_LINES).joinToString("\n"))
        }
        val pad = dp(16)

        val title = TextView(this).apply {
            setText(if (crash != null) R.string.crash_title else R.string.crash_log_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val body = TextView(this).apply {
            setText(if (crash != null) R.string.crash_body else R.string.crash_log_body)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(8), 0, dp(8))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button(R.string.crash_copy) { copy(report) })
            addView(button(R.string.crash_share) { share(report) })
            addView(button(R.string.crash_continue) { openApp() })
        }
        val text = TextView(this).apply {
            this.text = report
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(body)
            addView(buttons)
            addView(HorizontalScrollView(context).apply { addView(text) })
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column, MATCH_PARENT, WRAP_CONTENT)
        })
    }

    private fun copy(report: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Clicker report", report))
        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
    }

    private fun share(report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, getString(R.string.crash_share)))
    }

    private fun openApp() {
        CrashLog.clear(this)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
        setText(label)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val TRAIL_LINES = 120
    }
}
