package uk.co.antnode.anttp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var startStopButton: Button

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkStoragePermission()) {
            Toast.makeText(this, "Storage access granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage access denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "Storage access granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage access denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        startStopButton = findViewById(R.id.startStopButton)

        updateUi()

        if (!checkStoragePermission()) {
            requestStoragePermission()
        }

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

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${packageName}")
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                manageStorageLauncher.launch(intent)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}
