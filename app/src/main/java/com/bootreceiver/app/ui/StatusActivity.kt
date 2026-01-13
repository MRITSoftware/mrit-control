package com.bootreceiver.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bootreceiver.app.R
import com.bootreceiver.app.service.RebootMonitorService
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.RebootManager
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
 * Mostra informações sobre Device Admin, serviço, comandos e permite testes
 */
class StatusActivity : AppCompatActivity() {
    
    private lateinit var statusDeviceAdmin: TextView
    private lateinit var statusService: TextView
    private lateinit var statusCommand: TextView
    private lateinit var statusDeviceInfo: TextView
    private lateinit var statusLastUpdate: TextView
    private lateinit var btnRequestDeviceAdmin: Button
    private lateinit var btnCheckCommand: Button
    private lateinit var btnTestReboot: Button
    
    private lateinit var rebootManager: RebootManager
    private val supabaseManager = SupabaseManager()
    private lateinit var deviceId: String
    private val updateScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var isServiceBound = false
    private var serviceConnection: ServiceConnection? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)
        
        // Inicializa managers e device ID (após onCreate)
        rebootManager = RebootManager(this)
        deviceId = DeviceIdManager.getDeviceId(this)
        
        // Inicializa views
        statusDeviceAdmin = findViewById(R.id.statusDeviceAdmin)
        statusService = findViewById(R.id.statusService)
        statusCommand = findViewById(R.id.statusCommand)
        statusDeviceInfo = findViewById(R.id.statusDeviceInfo)
        statusLastUpdate = findViewById(R.id.statusLastUpdate)
        btnRequestDeviceAdmin = findViewById(R.id.btnRequestDeviceAdmin)
        btnCheckCommand = findViewById(R.id.btnCheckCommand)
        btnTestReboot = findViewById(R.id.btnTestReboot)
        
        // Configura botões
        btnRequestDeviceAdmin.setOnClickListener {
            rebootManager.requestDeviceAdmin()
            updateStatus()
        }
        
        btnCheckCommand.setOnClickListener {
            checkCommandNow()
        }
        
        btnTestReboot.setOnClickListener {
            showTestRebootDialog()
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
    
    override fun onDestroy() {
        super.onDestroy()
        serviceConnection?.let {
            if (isServiceBound) {
                try {
                    unbindService(it)
                } catch (e: Exception) {
                    // Ignora
                }
            }
        }
    }
    
    private fun updateStatus() {
        updateDeviceAdminStatus()
        updateServiceStatus()
        updateCommandStatus()
        updateDeviceInfo()
        statusLastUpdate.text = "Última atualização: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
    }
    
    private fun updateDeviceAdminStatus() {
        val isActive = rebootManager.isDeviceAdminActive()
        val apiLevel = Build.VERSION.SDK_INT
        val minApiLevel = android.os.Build.VERSION_CODES.N // API 24
        
        if (isActive) {
            if (apiLevel >= minApiLevel) {
                statusDeviceAdmin.text = "✅ Device Admin ATIVO\n✅ API Level OK (${apiLevel})\nO dispositivo pode ser reiniciado remotamente."
                statusDeviceAdmin.setTextColor(0xFF10B981.toInt())
            } else {
                statusDeviceAdmin.text = "✅ Device Admin ATIVO\n⚠️ API Level muito antigo (${apiLevel}, precisa ${minApiLevel}+)\nReboot remoto pode não funcionar."
                statusDeviceAdmin.setTextColor(0xFFFBBF24.toInt())
            }
            btnRequestDeviceAdmin.text = "Device Admin Ativo"
            btnRequestDeviceAdmin.isEnabled = false
        } else {
            statusDeviceAdmin.text = "❌ Device Admin INATIVO\n⚠️ Ative para permitir reboot remoto.\n⚠️ IMPORTANTE: Reinstale o app após ativar!"
            statusDeviceAdmin.setTextColor(0xFFEF4444.toInt())
            btnRequestDeviceAdmin.text = "Ativar Device Admin"
            btnRequestDeviceAdmin.isEnabled = true
        }
    }
    
    private fun updateServiceStatus() {
        val isRunning = isServiceRunning()
        if (isRunning) {
            statusService.text = "✅ Serviço RODANDO\nMonitorando comandos a cada 30 segundos."
            statusService.setTextColor(0xFF10B981.toInt())
        } else {
            statusService.text = "❌ Serviço PARADO\nInicie o app para ativar o serviço."
            statusService.setTextColor(0xFFEF4444.toInt())
        }
    }
    
    private fun isServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        return services.any { it.service.className == RebootMonitorService::class.java.name }
    }
    
    private fun updateCommandStatus() {
        updateScope.launch {
            try {
                val hasCommand = supabaseManager.checkRebootCommand(deviceId)
                if (hasCommand) {
                    statusCommand.text = "⚠️ COMANDO PENDENTE!\nHá um comando de reboot aguardando execução."
                    statusCommand.setTextColor(0xFFFBBF24.toInt())
                } else {
                    statusCommand.text = "✅ Nenhum comando pendente\nNão há comandos de reboot no Supabase."
                    statusCommand.setTextColor(0xFF10B981.toInt())
                }
            } catch (e: Exception) {
                statusCommand.text = "❌ Erro ao verificar: ${e.message}"
                statusCommand.setTextColor(0xFFEF4444.toInt())
            }
        }
    }
    
    private fun checkCommandNow() {
        btnCheckCommand.isEnabled = false
        btnCheckCommand.text = "Verificando..."
        statusCommand.text = "🔄 Verificando comando no Supabase..."
        
        updateScope.launch {
            try {
                delay(1000)
                val hasCommand = supabaseManager.checkRebootCommand(deviceId)
                if (hasCommand) {
                    statusCommand.text = "⚠️ COMANDO ENCONTRADO!\nHá um comando de reboot pendente."
                    statusCommand.setTextColor(0xFFFBBF24.toInt())
                    Toast.makeText(this@StatusActivity, "Comando encontrado! O dispositivo deve reiniciar em breve.", Toast.LENGTH_LONG).show()
                } else {
                    statusCommand.text = "✅ Nenhum comando pendente"
                    statusCommand.setTextColor(0xFF10B981.toInt())
                    Toast.makeText(this@StatusActivity, "Nenhum comando encontrado.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                statusCommand.text = "❌ Erro: ${e.message}"
                statusCommand.setTextColor(0xFFEF4444.toInt())
                Toast.makeText(this@StatusActivity, "Erro ao verificar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnCheckCommand.isEnabled = true
                btnCheckCommand.text = "Verificar Comando Agora"
            }
        }
    }
    
    private fun showTestRebootDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Teste de Reboot")
            .setMessage(
                "Isso vai reiniciar o dispositivo AGORA!\n\n" +
                "Certifique-se de que:\n" +
                "• Device Admin está ativo\n" +
                "• Você salvou seu trabalho\n\n" +
                "Deseja continuar?"
            )
            .setPositiveButton("SIM, REINICIAR") { _, _ ->
                testReboot()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun testReboot() {
        btnTestReboot.isEnabled = false
        btnTestReboot.text = "Reiniciando..."
        
        updateScope.launch {
            try {
                val isDeviceAdminActive = rebootManager.isDeviceAdminActive()
                if (!isDeviceAdminActive) {
                    Toast.makeText(this@StatusActivity, "❌ Device Admin não está ativo!", Toast.LENGTH_LONG).show()
                    btnTestReboot.isEnabled = true
                    btnTestReboot.text = "Testar Reboot Agora"
                    return@launch
                }
                
                Toast.makeText(this@StatusActivity, "🚀 Reiniciando dispositivo...", Toast.LENGTH_SHORT).show()
                delay(500)
                
                val success = rebootManager.reboot()
                if (success) {
                    Toast.makeText(this@StatusActivity, "✅ Comando enviado! Reiniciando...", Toast.LENGTH_SHORT).show()
                    delay(2000)
                } else {
                    val apiLevel = Build.VERSION.SDK_INT
                    val minApiLevel = android.os.Build.VERSION_CODES.N
                    val apiLevelOk = apiLevel >= minApiLevel
                    
                    // Monta mensagem de erro detalhada
                    val message = buildString {
                        append("O dispositivo não foi reiniciado.\n\n")
                        append("Possíveis causas:\n")
                        append("• Device Admin não está realmente ativo\n")
                        append("• App não foi reinstalado após adicionar política <reboot />\n")
                        append("• Política <reboot /> não foi aplicada corretamente\n")
                        append("• Fabricante bloqueou reboot remoto\n")
                        if (!apiLevelOk) {
                            append("• API Level muito antigo ($apiLevel, precisa $minApiLevel+)\n")
                        } else {
                            append("• API Level OK ($apiLevel)\n")
                        }
                        append("\n")
                        append("Soluções:\n")
                        append("1. Desative Device Admin\n")
                        append("2. Desinstale o app completamente\n")
                        append("3. Reinstale o app (versão mais recente)\n")
                        append("4. Ative Device Admin novamente\n")
                        append("5. Reinicie o dispositivo manualmente uma vez\n")
                        append("6. Teste novamente\n\n")
                        append("⚠️ IMPORTANTE: A política <reboot /> só é aplicada quando o Device Admin é ativado. Se você ativou antes de reinstalar, ela não foi aplicada!\n\n")
                        append("Verifique os logs para mais detalhes.")
                    }
                    
                    // Mostra diálogo com informações detalhadas
                    AlertDialog.Builder(this@StatusActivity)
                        .setTitle("❌ Falha ao Reiniciar")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                    btnTestReboot.isEnabled = true
                    btnTestReboot.text = "Testar Reboot Agora"
                }
            } catch (e: Exception) {
                Toast.makeText(this@StatusActivity, "❌ Erro: ${e.message}", Toast.LENGTH_LONG).show()
                btnTestReboot.isEnabled = true
                btnTestReboot.text = "Testar Reboot Agora"
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
