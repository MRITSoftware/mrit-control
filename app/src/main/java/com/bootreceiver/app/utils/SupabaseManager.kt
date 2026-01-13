package com.bootreceiver.app.utils

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Gerenciador para comunicação com Supabase
 * 
 * Esta classe gerencia a conexão com o Supabase e registra
 * dispositivos na tabela 'devices'
 */
class SupabaseManager {
    
    private val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
    }
    
    /**
     * Verifica se há um comando de reiniciar app pendente
     * 
     * @param deviceId ID único do dispositivo
     * @return true se houver comando pendente, false caso contrário
     */
    suspend fun checkRestartAppCommand(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Verificando comando de reiniciar app para dispositivo: $deviceId")
            
            val response = client.from("device_commands")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("command", "restart_app")
                        eq("executed", false)
                    }
                }
                .decodeSingle<DeviceCommand>()
            
            Log.d(TAG, "✅ Comando encontrado! ID: ${response.id}, Command: ${response.command}")
            true
        } catch (e: Exception) {
            if (e.message?.contains("No rows") == true || 
                e.message?.contains("not found") == true ||
                e.message?.contains("No value") == true) {
                Log.d(TAG, "ℹ️ Nenhum comando de reiniciar app pendente")
                false
            } else {
                Log.e(TAG, "❌ Erro ao verificar comando: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Marca um comando como executado
     * 
     * @param deviceId ID único do dispositivo
     * @param command Tipo de comando (ex: "restart_app")
     */
    suspend fun markCommandAsExecuted(deviceId: String, command: String = "restart_app"): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Marcando comando como executado: device_id=$deviceId, command=$command")
            
            // Busca o comando
            val cmd = client.from("device_commands")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("command", command)
                        eq("executed", false)
                    }
                }
                .decodeSingle<DeviceCommand>()
            
            // Atualiza como executado
            if (cmd.id != null) {
                val updateData = mapOf(
                    "executed" to true,
                    "executed_at" to java.time.Instant.now().toString()
                )
                
                client.from("device_commands")
                    .update(updateData) {
                        filter {
                            eq("id", cmd.id)
                        }
                    }
                
                Log.d(TAG, "✅ Comando marcado como executado: ${cmd.id}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao marcar comando como executado: ${e.message}", e)
            false
        }
    }
    
    /**
     * Registra ou atualiza um dispositivo na tabela devices
     * 
     * @param deviceId ID único do dispositivo (Android ID)
     * @param unitName Nome da unidade (email ou nome personalizado)
     * @return true se o registro foi bem-sucedido
     */
    suspend fun registerDevice(deviceId: String, unitName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Registrando dispositivo: $deviceId com nome: $unitName")
            
            // Verifica se o dispositivo já existe
            var existingDevice: Device? = null
            try {
                existingDevice = client.from("devices")
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("device_id", deviceId)
                        }
                    }
                    .decodeSingle<Device>()
                Log.d(TAG, "Dispositivo encontrado no banco")
            } catch (e: Exception) {
                if (e.message?.contains("No rows") == true || 
                    e.message?.contains("not found") == true) {
                    Log.d(TAG, "Dispositivo não encontrado, será criado novo registro")
                } else {
                    Log.w(TAG, "Erro ao verificar dispositivo existente: ${e.message}")
                }
            }
            
            if (existingDevice != null) {
                // Dispositivo já existe, atualiza last_seen e unit_name se fornecido
                Log.d(TAG, "Dispositivo já existe. Atualizando...")
                val updateData = mutableMapOf<String, Any>(
                    "last_seen" to java.time.Instant.now().toString()
                )
                
                if (unitName != null && unitName.isNotBlank()) {
                    updateData["unit_name"] = unitName
                }
                
                client.from("devices")
                    .update(updateData) {
                        filter {
                            eq("device_id", deviceId)
                        }
                    }
                
                Log.d(TAG, "Dispositivo atualizado com sucesso")
            } else {
                // Novo dispositivo, cria registro
                Log.d(TAG, "Criando novo registro de dispositivo...")
                val newDevice = Device(
                    device_id = deviceId,
                    unit_name = unitName,
                    is_active = true
                )
                
                client.from("devices")
                    .insert(newDevice)
                
                Log.d(TAG, "Dispositivo registrado com sucesso!")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar dispositivo: ${e.message}", e)
            false
        }
    }
    
    companion object {
        private const val TAG = "SupabaseManager"
        private const val SUPABASE_URL = "https://kihyhoqbrkwbfudttevo.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtpaHlob3Ficmt3YmZ1ZHR0ZXZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MTU1NTUwMjcsImV4cCI6MjAzMTEzMTAyN30.XtBTlSiqhsuUIKmhAMEyxofV-dRst7240n912m4O4Us"
    }
}

/**
 * Modelo de dados para dispositivo
 * Estrutura corresponde à tabela devices no Supabase
 */
@Serializable
data class Device(
    val id: String? = null,  // UUID
    val device_id: String,
    val unit_name: String? = null,
    val registered_at: String? = null,
    val last_seen: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)

/**
 * Modelo de dados para comando de dispositivo
 * Estrutura corresponde à tabela device_commands no Supabase
 */
@Serializable
data class DeviceCommand(
    val id: String? = null,  // UUID
    val device_id: String,
    val command: String,  // "restart_app" para reiniciar o app
    val executed: Boolean = false,
    val created_at: String? = null,  // TIMESTAMP WITH TIME ZONE
    val executed_at: String? = null  // TIMESTAMP WITH TIME ZONE
)

