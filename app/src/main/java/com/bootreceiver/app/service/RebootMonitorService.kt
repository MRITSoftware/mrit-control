package com.bootreceiver.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.RebootManager
import com.bootreceiver.app.utils.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço que monitora periodicamente o Supabase para verificar
 * se há comandos de reiniciar o dispositivo
 * 
 * Este serviço:
 * 1. Verifica a cada X segundos se há um comando de reiniciar
 * 2. Se encontrar, tenta reiniciar o dispositivo
 * 3. Marca o comando como executado após reiniciar
 */
class RebootMonitorService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isRunning = false
    private val supabaseManager = SupabaseManager()
    private lateinit var deviceId: String
    private lateinit var rebootManager: RebootManager
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== RebootMonitorService CRIADO ===")
        deviceId = DeviceIdManager.getDeviceId(this)
        Log.d(TAG, "Device ID obtido: $deviceId")
        rebootManager = RebootManager(this)
        Log.d(TAG, "Device Admin ativo: ${rebootManager.isDeviceAdminActive()}")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            Log.d(TAG, "Serviço já está rodando - reiniciando monitoramento...")
            // Reinicia o monitoramento mesmo se já estiver rodando
            serviceScope.launch {
                startMonitoring()
            }
            return START_STICKY
        }
        
        isRunning = true
        Log.d(TAG, "=== RebootMonitorService INICIADO ===")
        Log.d(TAG, "Device ID: $deviceId")
        Log.d(TAG, "Device Admin ativo: ${rebootManager.isDeviceAdminActive()}")
        
        // Verifica se Device Admin está ativo
        if (!rebootManager.isDeviceAdminActive()) {
            Log.w(TAG, "⚠️ Device Admin não está ativo. Solicitando permissão...")
            rebootManager.requestDeviceAdmin()
            // Continua mesmo assim, pois o usuário pode ativar depois
        } else {
            Log.d(TAG, "✅ Device Admin está ativo")
        }
        
        // Inicia o monitoramento em uma coroutine
        serviceScope.launch {
            Log.d(TAG, "Iniciando loop de monitoramento...")
            startMonitoring()
        }
        
        // Retorna START_STICKY para que o serviço seja reiniciado se for morto
        return START_STICKY
    }
    
    /**
     * Inicia o monitoramento periódico do banco de dados
     */
    private suspend fun startMonitoring() {
        var iteration = 0
        while (isRunning) {
            try {
                iteration++
                Log.d(TAG, "=== Verificação #$iteration ===")
                Log.d(TAG, "Device ID sendo verificado: $deviceId")
                Log.d(TAG, "Verificando comando de reiniciar no Supabase...")
                
                val hasRebootCommand = supabaseManager.checkRebootCommand(deviceId)
                Log.d(TAG, "Resultado da verificação: $hasRebootCommand")
                
                if (hasRebootCommand) {
                    Log.d(TAG, "Comando de reiniciar encontrado! Executando...")
                    
                    // Verifica se Device Admin está ativo antes de tentar reiniciar
                    if (!rebootManager.isDeviceAdminActive()) {
                        Log.w(TAG, "Device Admin não está ativo. Não é possível reiniciar.")
                        Log.w(TAG, "Por favor, ative o Device Admin nas configurações do Android.")
                        // Não marca como executado, para tentar novamente depois
                        delay(CHECK_INTERVAL_MS)
                        continue
                    }
                    
                    // Tenta reiniciar
                    val rebootSuccess = rebootManager.reboot()
                    
                    if (rebootSuccess) {
                        Log.d(TAG, "Comando de reiniciar enviado com sucesso!")
                        // Marca como executado apenas se o reinício foi bem-sucedido
                        supabaseManager.markCommandAsExecuted(deviceId)
                        // O dispositivo será reiniciado, então o serviço será parado
                        stopSelf()
                        return
                    } else {
                        Log.w(TAG, "Falha ao reiniciar. Verifique se Device Admin está ativo.")
                        // Se falhar, não marca como executado para tentar novamente
                        // Continua monitorando
                    }
                } else {
                    Log.d(TAG, "Nenhum comando de reiniciar pendente")
                }
                
                // Aguarda antes da próxima verificação
                delay(CHECK_INTERVAL_MS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro no monitoramento: ${e.message}", e)
                // Em caso de erro, aguarda um pouco antes de tentar novamente
                delay(ERROR_RETRY_DELAY_MS)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "RebootMonitorService destruído")
    }
    
    companion object {
        private const val TAG = "RebootMonitorService"
        private const val CHECK_INTERVAL_MS = 30000L // Verifica a cada 30 segundos
        private const val ERROR_RETRY_DELAY_MS = 60000L // Em caso de erro, aguarda 1 minuto
    }
}
