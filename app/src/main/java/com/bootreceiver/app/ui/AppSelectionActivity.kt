package com.bootreceiver.app.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bootreceiver.app.R
import com.bootreceiver.app.utils.PermissionChecker
import com.bootreceiver.app.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity que permite ao usuário escolher qual app será aberto automaticamente
 * 
 * Esta tela aparece apenas na primeira vez que o app é aberto manualmente.
 * Após selecionar um app, ele será salvo e usado automaticamente nos próximos boots.
 */
class AppSelectionActivity : AppCompatActivity() {
    
    private lateinit var listView: ListView
    private lateinit var preferenceManager: PreferenceManager
    private val appsList = mutableListOf<AppInfo>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)
        
        preferenceManager = PreferenceManager(this)
        
        // Verifica permissões e otimizações
        checkPermissionsAndOptimizations()
        
        // Se já estiver configurado, fecha esta activity e abre o app configurado
        if (preferenceManager.isConfigured()) {
            Log.d(TAG, "App já configurado. Abrindo app configurado...")
            val targetPackage = preferenceManager.getTargetPackageName()
            if (targetPackage != null) {
                val appLauncher = com.bootreceiver.app.utils.AppLauncher(this)
                appLauncher.launchApp(targetPackage)
            }
            finish()
            return
        }
        
        listView = findViewById(R.id.listViewApps)
        
        // Carrega lista de apps em background
        loadInstalledApps()
        
        // Configura o click na lista
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedApp = appsList[position]
            selectApp(selectedApp.packageName, selectedApp.name)
        }
    }
    
    /**
     * Verifica permissões e otimizações e alerta o usuário se necessário
     */
    private fun checkPermissionsAndOptimizations() {
        val permissionChecker = PermissionChecker(this)
        val status = permissionChecker.getFullStatus()
        
        if (!status.isReady) {
            Log.w(TAG, "Problemas detectados: ${status.issues.joinToString()}")
            
            val message = buildString {
                append("Para garantir que o app funcione corretamente no boot, é necessário:\n\n")
                status.issues.forEach { issue ->
                    append("• $issue\n")
                }
                append("\nDeseja abrir as configurações agora?")
            }
            
            AlertDialog.Builder(this)
                .setTitle("Configurações Necessárias")
                .setMessage(message)
                .setPositiveButton("Abrir Configurações") { _, _ ->
                    // Abre configurações de otimização de bateria
                    if (status.batteryOptimized) {
                        permissionChecker.openBatteryOptimizationSettings()
                    }
                    // Abre configurações de overlay se necessário
                    if (status.issues.any { it.contains("SYSTEM_ALERT_WINDOW") }) {
                        permissionChecker.openOverlayPermissionSettings()
                    }
                }
                .setNegativeButton("Depois", null)
                .setCancelable(true)
                .show()
        } else {
            Log.d(TAG, "Todas as permissões e otimizações estão corretas")
        }
    }
    
    /**
     * Carrega lista de aplicativos instalados
     * Executa em background para não travar a UI
     */
    private fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packageManager = packageManager
                val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                
                val apps = installedPackages
                    .filter { 
                        // Filtra apenas apps que podem ser abertos (não são serviços do sistema)
                        val appInfo = it.applicationInfo
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || 
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    }
                    .map {
                        val appInfo = it.applicationInfo
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        AppInfo(appName, it.packageName)
                    }
                    .sortedBy { it.name }
                
                withContext(Dispatchers.Main) {
                    appsList.clear()
                    appsList.addAll(apps)
                    
                    val adapter = ArrayAdapter(
                        this@AppSelectionActivity,
                        R.layout.list_item_app,
                        appsList.map { "${it.name}\n${it.packageName}" }
                    )
                    listView.adapter = adapter
                    
                    if (appsList.isEmpty()) {
                        Toast.makeText(
                            this@AppSelectionActivity,
                            "Nenhum aplicativo encontrado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar apps", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AppSelectionActivity,
                        "Erro ao carregar aplicativos: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Salva o app selecionado e fecha a activity
     */
    private fun selectApp(packageName: String, appName: String) {
        Log.d(TAG, "App selecionado: $appName ($packageName)")
        
        // Salva o package name
        preferenceManager.saveTargetPackageName(packageName)
        
        Toast.makeText(
            this,
            "App configurado: $appName\nO app será aberto automaticamente no próximo boot.",
            Toast.LENGTH_LONG
        ).show()
        
        // Aguarda um pouco e fecha a activity
        listView.postDelayed({
            finish()
        }, 2000)
    }
    
    /**
     * Classe de dados para representar um app
     */
    data class AppInfo(
        val name: String,
        val packageName: String
    )
    
    companion object {
        private const val TAG = "AppSelectionActivity"
    }
}
