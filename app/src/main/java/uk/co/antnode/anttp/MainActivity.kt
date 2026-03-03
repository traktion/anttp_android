package uk.co.antnode.anttp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var startStopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        startStopButton = findViewById(R.id.startStopButton)

        updateUi()

        startStopButton.setOnClickListener {
            if (ProxyForegroundService.isRunning()) {
                stopProxyService()
            } else {
                startProxyService()
            }
            // Give it a moment to update state, ideally we'd use a more robust way to track state
            statusTextView.postDelayed({ updateUi() }, 100)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun startProxyService() {
        val intent = Intent(this, ProxyForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopProxyService() {
        val intent = Intent(this, ProxyForegroundService::class.java).apply {
            action = ProxyForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    private fun updateUi() {
        if (ProxyForegroundService.isRunning()) {
            statusTextView.text = "Proxy is running"
            startStopButton.text = getString(R.string.stop_proxy)
        } else {
            statusTextView.text = "Proxy is stopped"
            startStopButton.text = getString(R.string.start_proxy)
        }
    }
}
