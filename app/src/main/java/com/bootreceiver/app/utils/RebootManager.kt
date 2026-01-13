package com.bootreceiver.app.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.bootreceiver.app.receiver.DeviceAdminReceiver

/**
 * Gerenciador para reiniciar o dispositivo
 * 
 * IMPORTANTE: Para reiniciar o dispositivo, o app precisa ser
 * configurado como Device Admin. Isso requer ação do usuário.
 */
class RebootManager(private val context: Context) {
    
    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    
    private val deviceAdminComponent: ComponentName =
        ComponentName(context, DeviceAdminReceiver::class.java)
    
    /**
     * Verifica se o app está configurado como Device Admin
     */
    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager?.isAdminActive(deviceAdminComponent) == true
    }
    
    /**
     * Solicita ao usuário que configure o app como Device Admin
     * Retorna true se já está ativo, false caso contrário
     */
    fun requestDeviceAdmin(): Boolean {
        if (isDeviceAdminActive()) {
            Log.d(TAG, "Device Admin já está ativo")
            return true
        }
        
        Log.d(TAG, "Solicitando permissão de Device Admin...")
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Este app precisa de permissão de Device Admin para reiniciar o dispositivo remotamente.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar Device Admin: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Tenta reiniciar o dispositivo
     * 
     * @return true se o comando foi enviado com sucesso, false caso contrário
     */
    fun reboot(): Boolean {
        if (!isDeviceAdminActive()) {
            Log.w(TAG, "Device Admin não está ativo. Não é possível reiniciar.")
            return false
        }
        
        Log.d(TAG, "=== INICIANDO PROCESSO DE REINÍCIO ===")
        Log.d(TAG, "Device Admin ativo: ${isDeviceAdminActive()}")
        Log.d(TAG, "API Level: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "Device Admin Component: $deviceAdminComponent")
        
        try {
            // Tenta usar DevicePolicyManager.reboot() (requer API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Log.d(TAG, "Tentando reiniciar via DevicePolicyManager.reboot()...")
                
                if (devicePolicyManager == null) {
                    Log.e(TAG, "DevicePolicyManager é null!")
                    return false
                }
                
                if (!devicePolicyManager!!.isAdminActive(deviceAdminComponent)) {
                    Log.e(TAG, "Device Admin não está ativo no DevicePolicyManager!")
                    return false
                }
                
                Log.d(TAG, "Chamando devicePolicyManager.reboot()...")
                
                try {
                    devicePolicyManager!!.reboot(deviceAdminComponent)
                    Log.d(TAG, "✅ devicePolicyManager.reboot() chamado com sucesso")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao chamar reboot(): ${e.message}", e)
                    // Tenta método alternativo
                    return tryAlternativeReboot()
                }
                
                Log.d(TAG, "⚠️ Se o dispositivo não reiniciar em 10 segundos, pode ser bloqueado pelo fabricante")
                Log.d(TAG, "⚠️ Alguns fabricantes (Samsung, Xiaomi, etc.) bloqueiam reboot mesmo com Device Admin")
                
                // Aguarda um pouco para ver se reinicia
                Thread.sleep(2000)
                
                return true
            } else {
                Log.w(TAG, "API level muito antigo (${Build.VERSION.SDK_INT}). Reiniciar pode não funcionar.")
                return false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException ao reiniciar: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            return false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ IllegalStateException ao reiniciar: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao reiniciar: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            return false
        }
    }
    
    /**
     * Tenta método alternativo de reinício (pode não funcionar sem root)
     */
    private fun tryAlternativeReboot(): Boolean {
        Log.d(TAG, "Tentando método alternativo de reinício...")
        
        try {
            // Tenta via PowerManager (pode não funcionar sem permissões especiais)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                Log.d(TAG, "PowerManager disponível, tentando reboot...")
                // Nota: PowerManager.reboot() requer permissões de sistema
                // Isso geralmente não funciona em apps normais
            }
            
            // Tenta via Runtime (requer root na maioria dos casos)
            try {
                Log.d(TAG, "Tentando via Runtime.exec('reboot')...")
                val process = Runtime.getRuntime().exec("su -c reboot")
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    Log.d(TAG, "✅ Reinício via Runtime.exec bem-sucedido")
                    return true
                } else {
                    Log.w(TAG, "Runtime.exec retornou código: $exitCode (provavelmente sem root)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Runtime.exec falhou (esperado sem root): ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Erro no método alternativo: ${e.message}", e)
            return false
        }
    }
    
    companion object {
        private const val TAG = "RebootManager"
    }
}
