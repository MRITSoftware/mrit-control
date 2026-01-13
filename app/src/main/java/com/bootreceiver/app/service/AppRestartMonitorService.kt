package com.bootreceiver.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bootreceiver.app.R
import com.bootreceiver.app.ui.AppSelectionActivity
import com.bootreceiver.app.utils.AppLauncher
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.PreferenceManager
import com.bootreceiver.app.utils.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço que monitora periodicamente o Supabase para verificar
 * se há comandos de reiniciar o app configurado
 * 
 * Este serviço:
 * 1. Verifica a cada 30 segundos se há um comando de reiniciar app
 * 2. Se encontrar, fecha e reabre o app configurado
 * 3. Marca o comando como executado após reiniciar
 */
class AppRestartMonitorService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isRunning = false
    private val supabaseManager = SupabaseManager()
    private lateinit var deviceId: String
    private var lastRestartTime: Long = 0 // Timestamp do último reinício
    private var isRestarting = false // Flag para evitar múltiplos reinícios simultâneos
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppRestartMonitorService criado")
        deviceId = DeviceIdManager.getDeviceId(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            Log.d(TAG, "Serviço já está rodando")
            return START_STICKY
        }
        
        try {
            isRunning = true
            Log.d(TAG, "AppRestartMonitorService iniciado para dispositivo: $deviceId")
            
            // Garante que o canal de notificação existe
            createNotificationChannel()
            
            // Inicia como Foreground Service
            try {
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "Foreground Service iniciado com sucesso")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar Foreground Service: ${e.message}", e)
            }
            
            // Inicia o monitoramento em uma coroutine
            serviceScope.launch {
                startMonitoring()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico ao iniciar serviço: ${e.message}", e)
            isRunning = false
        }
        
        return START_STICKY
    }
    
    /**
     * Cria o canal de notificação (necessário para Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoramento de App",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitora comandos de reiniciar app do Supabase"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Cria a notificação para o Foreground Service
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, AppSelectionActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )
        
        val smallIcon = android.R.drawable.ic_dialog_info
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MRIT Control - Monitorando")
            .setContentText("Monitorando comandos de reiniciar app...")
            .setSmallIcon(smallIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * Inicia o monitoramento periódico do banco de dados
     */
    private suspend fun startMonitoring() {
        while (isRunning) {
            try {
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "🔍 Ciclo de verificação #${System.currentTimeMillis() / CHECK_INTERVAL_MS}")
                Log.d(TAG, "Device ID: $deviceId")
                
                // Verifica cooldown (evita reiniciar múltiplas vezes seguidas)
                val timeSinceLastRestart = System.currentTimeMillis() - lastRestartTime
                if (timeSinceLastRestart < COOLDOWN_AFTER_RESTART_MS) {
                    val remainingSeconds = (COOLDOWN_AFTER_RESTART_MS - timeSinceLastRestart) / 1000
                    Log.d(TAG, "⏳ Cooldown ativo: ${remainingSeconds}s restantes (evita loop de reinício)")
                    delay(CHECK_INTERVAL_MS)
                    continue
                }
                
                // Verifica se já está reiniciando (evita múltiplos reinícios simultâneos)
                if (isRestarting) {
                    Log.d(TAG, "⏳ Reinício já em andamento, aguardando...")
                    delay(CHECK_INTERVAL_MS)
                    continue
                }
                
                val hasCommand = supabaseManager.checkRestartAppCommand(deviceId)
                
                if (hasCommand) {
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "⚠️⚠️⚠️ COMANDO DE REINICIAR APP ENCONTRADO! ⚠️⚠️⚠️")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    
                    // Marca que está reiniciando
                    isRestarting = true
                    
                    // Obtém o app configurado
                    val preferenceManager = PreferenceManager(this@AppRestartMonitorService)
                    val targetPackageName = preferenceManager.getTargetPackageName()
                    
                    if (targetPackageName.isNullOrEmpty()) {
                        Log.w(TAG, "Nenhum app configurado. Não é possível reiniciar.")
                        // Marca como executado mesmo assim para não ficar em loop
                        val marked = supabaseManager.markCommandAsExecuted(deviceId, "restart_app")
                        if (marked) {
                            Log.d(TAG, "✅ Comando marcado como executado (sem app configurado)")
                        } else {
                            Log.e(TAG, "❌ Falha ao marcar comando como executado!")
                        }
                        isRestarting = false
                    } else {
                        Log.d(TAG, "App configurado: $targetPackageName")
                        
                        // Marca como executado ANTES de reiniciar (importante!)
                        Log.d(TAG, "📝 Marcando comando como executado no Supabase...")
                        val marked = supabaseManager.markCommandAsExecuted(deviceId, "restart_app")
                        
                        if (!marked) {
                            Log.e(TAG, "❌ FALHA CRÍTICA: Não foi possível marcar comando como executado!")
                            Log.e(TAG, "⚠️ Isso pode causar loop de reinício. Verifique o banco de dados.")
                            // Aguarda mais tempo antes de tentar novamente
                            delay(ERROR_RETRY_DELAY_MS)
                            isRestarting = false
                            continue
                        }
                        
                        Log.d(TAG, "✅ Comando marcado como executado com sucesso!")
                        
                        // Verifica novamente se ainda há comando pendente (double-check)
                        delay(2000) // Aguarda 2 segundos para garantir que foi salvo
                        val stillHasCommand = supabaseManager.checkRestartAppCommand(deviceId)
                        if (stillHasCommand) {
                            Log.w(TAG, "⚠️ Ainda há comando pendente após marcar como executado!")
                            Log.w(TAG, "⚠️ Pode haver múltiplos comandos ou problema no banco.")
                            // Tenta marcar todos como executados
                            var attempts = 0
                            while (attempts < 3 && supabaseManager.checkRestartAppCommand(deviceId)) {
                                supabaseManager.markCommandAsExecuted(deviceId, "restart_app")
                                delay(1000)
                                attempts++
                            }
                        }
                        
                        // Atualiza timestamp do último reinício
                        lastRestartTime = System.currentTimeMillis()
                        
                        // Reinicia o app
                        Log.d(TAG, "🔄 Reiniciando app: $targetPackageName")
                        val appLauncher = AppLauncher(this@AppRestartMonitorService)
                        val success = appLauncher.restartApp(targetPackageName)
                        
                        if (success) {
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.d(TAG, "✅✅✅ APP REINICIADO COM SUCESSO! ✅✅✅")
                            Log.d(TAG, "⏳ Cooldown de ${COOLDOWN_AFTER_RESTART_MS / 1000}s ativado")
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        } else {
                            Log.e(TAG, "❌ Falha ao reiniciar app: $targetPackageName")
                        }
                        
                        // Libera flag de reinício após um tempo
                        delay(5000) // Aguarda 5 segundos antes de liberar
                        isRestarting = false
                    }
                } else {
                    Log.d(TAG, "ℹ️ Nenhum comando de reiniciar app pendente")
                    // Se não há comando, reseta flag de reinício (caso tenha ficado travada)
                    if (isRestarting) {
                        Log.w(TAG, "⚠️ Flag de reinício estava travada, resetando...")
                        isRestarting = false
                    }
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
        Log.d(TAG, "AppRestartMonitorService destruído")
    }
    
    companion object {
        private const val TAG = "AppRestartMonitor"
        private const val CHANNEL_ID = "app_restart_monitor_channel"
        private const val NOTIFICATION_ID = 1
        private const val CHECK_INTERVAL_MS = 30000L // Verifica a cada 30 segundos
        private const val ERROR_RETRY_DELAY_MS = 60000L // Em caso de erro, aguarda 1 minuto
        private const val COOLDOWN_AFTER_RESTART_MS = 300000L // 5 minutos de cooldown após reiniciar
    }
}
