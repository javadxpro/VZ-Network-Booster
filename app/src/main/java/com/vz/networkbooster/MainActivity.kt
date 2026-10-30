package com.vz.networkbooster

import android.Manifest
import android.app.Activity
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.vz.networkbooster.ui.VzViewModel
import com.vz.networkbooster.ui.screens.AppRoot
import com.vz.networkbooster.ui.theme.VzTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VzViewModel by viewModels()

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
        } else {
            viewModel.onVpnConsentDenied()
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the tunnel works without the notification permission; it is just less visible */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            VzTheme {
                AppRoot(viewModel = viewModel, onConnectRequested = ::requestConnect)
            }
        }
    }

    private fun requestConnect() {
        val consent = VpnService.prepare(this)
        if (consent != null) {
            vpnConsent.launch(consent)
        } else {
            viewModel.connect()
        }
    }
}
