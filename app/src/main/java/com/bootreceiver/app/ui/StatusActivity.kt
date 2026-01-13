package com.bootreceiver.app.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bootreceiver.app.R
import com.bootreceiver.app.service.AppRestartMonitorService
import com.bootreceiver.app.service.BootService
import com.bootreceiver.app.utils.AppLauncher
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.PreferenceManager
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
    private lateinit var btnRestartApp: Button
    
    private val supabaseManager = SupabaseManager()
    private lateinit var deviceId: String
    private lateinit var preferenceManager: PreferenceManager
    private val updateScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)
        
        // Inicializa device ID e preference manager
        deviceId = DeviceIdManager.getDeviceId(this)
        preferenceManager = PreferenceManager(this)
        
        // Inicializa views
        statusService = findViewById(R.id.statusService)
        statusDeviceInfo = findViewById(R.id.statusDeviceInfo)
        statusLastUpdate = findViewById(R.id.statusLastUpdate)
        btnRestartApp = findViewById(R.id.btnRestartApp)
        
        // Configura botão de reiniciar app
        btnRestartApp.setOnClickListener {
            showRestartAppDialog()
        }
        
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
        val bootServiceRunning = services.any { it.service.className == BootService::class.java.name }
        val restartServiceRunning = services.any { it.service.className == AppRestartMonitorService::class.java.name }
        
        if (bootServiceRunning || restartServiceRunning) {
            statusService.text = "✅ Serviços RODANDO\n• BootService: Monitorando boot\n• AppRestartMonitorService: Monitorando comandos (a cada 30s)"
            statusService.setTextColor(0xFF10B981.toInt())
            return true
        } else {
            statusService.text = "❌ Serviços PARADOS\nInicie o app para ativar os serviços."
            statusService.setTextColor(0xFFEF4444.toInt())
            return false
        }
    }
    
    private fun showRestartAppDialog() {
        val targetPackage = preferenceManager.getTargetPackageName()
        
        if (targetPackage.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Nenhum App Configurado")
                .setMessage("Não há nenhum app configurado para reiniciar.\n\nConfigure um app primeiro na tela de seleção.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("🔄 Reiniciar App")
            .setMessage(
                "Isso vai fechar e reabrir o app configurado:\n\n" +
                "App: $targetPackage\n\n" +
                "Deseja continuar?"
            )
            .setPositiveButton("SIM, REINICIAR") { _, _ ->
                restartAppNow()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun restartAppNow() {
        btnRestartApp.isEnabled = false
        btnRestartApp.text = "Reiniciando..."
        
        updateScope.launch {
            try {
                val targetPackage = preferenceManager.getTargetPackageName()
                
                if (targetPackage.isNullOrEmpty()) {
                    Toast.makeText(this@StatusActivity, "❌ Nenhum app configurado!", Toast.LENGTH_LONG).show()
                    btnRestartApp.isEnabled = true
                    btnRestartApp.text = "Reiniciar App Agora"
                    return@launch
                }
                
                Toast.makeText(this@StatusActivity, "🔄 Reiniciando app...", Toast.LENGTH_SHORT).show()
                delay(500)
                
                val appLauncher = AppLauncher(this@StatusActivity)
                val success = appLauncher.restartApp(targetPackage)
                
                if (success) {
                    Toast.makeText(this@StatusActivity, "✅ App reiniciado com sucesso!", Toast.LENGTH_SHORT).show()
                    delay(2000)
                } else {
                    Toast.makeText(this@StatusActivity, "❌ Falha ao reiniciar app. Verifique os logs.", Toast.LENGTH_LONG).show()
                    btnRestartApp.isEnabled = true
                    btnRestartApp.text = "Reiniciar App Agora"
                }
            } catch (e: Exception) {
                Toast.makeText(this@StatusActivity, "❌ Erro: ${e.message}", Toast.LENGTH_LONG).show()
                btnRestartApp.isEnabled = true
                btnRestartApp.text = "Reiniciar App Agora"
            }
        }
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
