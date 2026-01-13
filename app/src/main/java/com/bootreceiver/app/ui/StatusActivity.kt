package com.bootreceiver.app.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bootreceiver.app.R
import com.bootreceiver.app.service.BootService
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity de status e debug do sistema
 * Mostra informações sobre o serviço e dispositivo
 */
class StatusActivity : AppCompatActivity() {
    
    private lateinit var statusService: TextView
    private lateinit var statusDeviceInfo: TextView
    private lateinit var statusLastUpdate: TextView
    
    private val supabaseManager = SupabaseManager()
    private lateinit var deviceId: String
    private val updateScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)
        
        // Inicializa device ID
        deviceId = DeviceIdManager.getDeviceId(this)
        
        // Inicializa views
        statusService = findViewById(R.id.statusService)
        statusDeviceInfo = findViewById(R.id.statusDeviceInfo)
        statusLastUpdate = findViewById(R.id.statusLastUpdate)
        
        // Atualiza status inicial
        updateStatus()
        
        // Atualiza status a cada 5 segundos
        updateScope.launch {
            while (true) {
                delay(5000)
                updateStatus()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun updateStatus() {
        updateServiceStatus()
        updateDeviceInfo()
        statusLastUpdate.text = "Última atualização: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
    }
    
    private fun updateServiceStatus() {
        val isRunning = isServiceRunning()
        if (isRunning) {
            statusService.text = "✅ Serviço RODANDO\nO app está monitorando e pronto para abrir o app configurado no boot."
            statusService.setTextColor(0xFF10B981.toInt())
        } else {
            statusService.text = "❌ Serviço PARADO\nInicie o app para ativar o serviço."
            statusService.setTextColor(0xFFEF4444.toInt())
        }
    }
    
    private fun isServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        return services.any { it.service.className == BootService::class.java.name }
    }
    
    private fun updateDeviceInfo() {
        val info = buildString {
            append("Device ID: $deviceId\n")
            append("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Manufacturer: ${Build.MANUFACTURER}\n")
            append("Model: ${Build.MODEL}\n")
            append("Device: ${Build.DEVICE}\n")
        }
        statusDeviceInfo.text = info
    }
    
    companion object {
        private const val TAG = "StatusActivity"
    }
}
