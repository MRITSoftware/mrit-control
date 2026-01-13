package com.bootreceiver.app.utils

import android.app.admin.DevicePolicyManager
import com.bootreceiver.app.receiver.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

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
     * Tenta reiniciar o dispositivo usando múltiplos métodos
     * 
     * @return true se o comando foi enviado com sucesso, false caso contrário
     */
    fun reboot(): Boolean {
        Log.d(TAG, "🔄 ========== INICIANDO TENTATIVA DE REBOOT ==========")
        Log.d(TAG, "Device Admin ativo: ${isDeviceAdminActive()}")
        Log.d(TAG, "API Level: ${Build.VERSION.SDK_INT} (N = ${Build.VERSION_CODES.N})")
        Log.d(TAG, "Device Admin Component: $deviceAdminComponent")
        
        // Método 1: DevicePolicyManager.reboot() (requer Device Admin e API 24+)
        if (isDeviceAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Log.d(TAG, "🔧 Método 1: Tentando reiniciar via DevicePolicyManager.reboot()...")
                Log.d(TAG, "   DevicePolicyManager: ${devicePolicyManager != null}")
                Log.d(TAG, "   DeviceAdminComponent: $deviceAdminComponent")
                
                devicePolicyManager?.reboot(deviceAdminComponent)
                
                // Se chegou aqui sem exceção, o comando foi enviado
                Log.d(TAG, "✅ Comando de reiniciar enviado via DevicePolicyManager.reboot()")
                Log.d(TAG, "   NOTA: O método não lança exceção, mas pode não funcionar em alguns dispositivos")
                Log.d(TAG, "   Se o dispositivo não reiniciar, pode ser limitação do fabricante")
                return true
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ DevicePolicyManager.reboot() falhou por segurança: ${e.message}")
                Log.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
            } catch (e: UnsupportedOperationException) {
                Log.e(TAG, "❌ DevicePolicyManager.reboot() não suportado: ${e.message}")
                Log.e(TAG, "   Este dispositivo/fabricante não suporta reboot via DevicePolicyManager")
            } catch (e: Exception) {
                Log.e(TAG, "❌ DevicePolicyManager.reboot() falhou: ${e.message}")
                Log.e(TAG, "   Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
            }
        } else {
            if (!isDeviceAdminActive()) {
                Log.w(TAG, "⚠️ Device Admin não está ativo - método 1 não disponível")
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.w(TAG, "⚠️ API level ${Build.VERSION.SDK_INT} é muito antigo para DevicePolicyManager.reboot()")
            }
        }
        
        // Método 2: PowerManager.reboot() (requer permissão REBOOT - apenas para apps de sistema)
        // Nota: Este método geralmente não funciona em apps normais, apenas em apps de sistema
        try {
            Log.d(TAG, "Tentando reiniciar via PowerManager...")
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null) {
                // PowerManager.reboot() requer app de sistema ou permissão especial
                // Na maioria dos casos, isso não funcionará em apps normais
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    powerManager.reboot(null)
                    Log.d(TAG, "✅ Comando de reiniciar enviado via PowerManager")
                    return true
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "PowerManager.reboot() falhou por segurança (esperado em apps normais): ${e.message}")
        } catch (e: NoSuchMethodError) {
            Log.w(TAG, "PowerManager.reboot() não disponível nesta versão do Android")
        } catch (e: Exception) {
            Log.w(TAG, "PowerManager.reboot() falhou: ${e.message}")
        }
        
        // Método 3: Runtime.exec com su (requer root)
        try {
            Log.d(TAG, "Tentando reiniciar via su (requer root)...")
            val process = Runtime.getRuntime().exec("su -c reboot")
            process.waitFor()
            if (process.exitValue() == 0) {
                Log.d(TAG, "✅ Comando de reiniciar enviado via su")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Runtime.exec('su -c reboot') falhou (dispositivo pode não ter root): ${e.message}")
        }
        
        // Método 4: Runtime.exec com reboot direto (pode funcionar em alguns dispositivos)
        try {
            Log.d(TAG, "Tentando reiniciar via Runtime.exec('reboot')...")
            val process = Runtime.getRuntime().exec("reboot")
            process.waitFor()
            if (process.exitValue() == 0) {
                Log.d(TAG, "✅ Comando de reiniciar enviado via Runtime.exec")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Runtime.exec('reboot') falhou: ${e.message}")
        }
        
        Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.e(TAG, "❌ TODOS OS MÉTODOS DE REINICIAR FALHARAM")
        Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.e(TAG, "📋 DIAGNÓSTICO:")
        Log.e(TAG, "  1. Device Admin ativo? ${isDeviceAdminActive()}")
        Log.e(TAG, "  2. API Level: ${Build.VERSION.SDK_INT} (mínimo: ${Build.VERSION_CODES.N})")
        Log.e(TAG, "  3. Device Admin Component: $deviceAdminComponent")
        Log.e(TAG, "  4. DevicePolicyManager disponível? ${devicePolicyManager != null}")
        Log.e(TAG, "")
        Log.e(TAG, "🔧 POSSÍVEIS SOLUÇÕES:")
        Log.e(TAG, "  1. Verifique se Device Admin está realmente ativo")
        Log.e(TAG, "     → Configurações → Segurança → Administradores do dispositivo")
        Log.e(TAG, "  2. Reinstale o app após ativar Device Admin")
        Log.e(TAG, "     → Isso aplica a política <reboot /> do device_admin.xml")
        Log.e(TAG, "  3. Verifique se device_admin.xml tem <reboot />")
        Log.e(TAG, "  4. Alguns fabricantes bloqueiam reboot remoto")
        Log.e(TAG, "  5. Dispositivo pode precisar de root para reboot remoto")
        Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        return false
    }
    
    companion object {
        private const val TAG = "RebootManager"
    }
}
