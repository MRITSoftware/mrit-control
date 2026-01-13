package com.bootreceiver.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.bootreceiver.app.R

/**
 * Serviço que cria um overlay invisível para interceptar gestos de minimização
 * quando o modo kiosk está ativo
 * 
 * O overlay cobre toda a tela e intercepta eventos de toque que poderiam minimizar o app
 */
class KioskOverlayService : Service() {
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KioskOverlayService criado")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kioskEnabled = intent?.getBooleanExtra("kiosk_enabled", false) ?: false
        
        if (kioskEnabled) {
            showOverlay()
        } else {
            hideOverlay()
        }
        
        return START_NOT_STICKY
    }
    
    private fun showOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay já está visível")
            return
        }
        
        try {
            Log.d(TAG, "🔒 Mostrando overlay de kiosk...")
            
            // Cria uma view invisível que cobre toda a tela
            overlayView = FrameLayout(this).apply {
                var startX = 0f
                var startY = 0f
                var startTime = 0L
                
                setOnTouchListener { _, event ->
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val edgeThreshold = 50f // Área de 50px nas bordas para interceptar gestos
                    
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            startTime = System.currentTimeMillis()
                            
                            // Bloqueia toques nas bordas (onde ficam os gestos de navegação)
                            val isLeftEdge = startX < edgeThreshold
                            val isRightEdge = startX > screenWidth - edgeThreshold
                            val isBottomEdge = startY > screenHeight - edgeThreshold
                            
                            if (isLeftEdge || isRightEdge || isBottomEdge) {
                                Log.d(TAG, "🔒 Toque na borda detectado (${if (isLeftEdge) "esquerda" else if (isRightEdge) "direita" else "inferior"}) - bloqueando!")
                                return@setOnTouchListener true // Consome o evento
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = Math.abs(event.x - startX)
                            val deltaY = Math.abs(event.y - startY)
                            
                            // Detecta gestos de swipe
                            val isLeftEdge = startX < edgeThreshold
                            val isRightEdge = startX > screenWidth - edgeThreshold
                            val isBottomEdge = startY > screenHeight - edgeThreshold
                            
                            // Gesto de voltar: swipe da borda esquerda ou direita para dentro
                            if ((isLeftEdge || isRightEdge) && deltaX > 30f) {
                                Log.d(TAG, "🔒 Gesto de VOLTAR detectado e bloqueado! (swipe da borda ${if (isLeftEdge) "esquerda" else "direita"})")
                                return@setOnTouchListener true // Consome o evento
                            }
                            
                            // Gesto de Home: swipe de baixo para cima
                            if (isBottomEdge && deltaY > 30f && event.y < startY) {
                                Log.d(TAG, "🔒 Gesto de HOME detectado e bloqueado! (swipe de baixo para cima)")
                                return@setOnTouchListener true // Consome o evento
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            val duration = System.currentTimeMillis() - startTime
                            val deltaX = Math.abs(event.x - startX)
                            val deltaY = Math.abs(event.y - startY)
                            
                            val isLeftEdge = startX < edgeThreshold
                            val isRightEdge = startX > screenWidth - edgeThreshold
                            val isBottomEdge = startY > screenHeight - edgeThreshold
                            
                            // Se foi um gesto rápido nas bordas, bloqueia
                            if (duration < 300 && (deltaX > 50f || deltaY > 50f)) {
                                if (isLeftEdge || isRightEdge) {
                                    Log.d(TAG, "🔒 Gesto de voltar rápido bloqueado!")
                                    return@setOnTouchListener true
                                }
                                if (isBottomEdge) {
                                    Log.d(TAG, "🔒 Gesto de home rápido bloqueado!")
                                    return@setOnTouchListener true
                                }
                            }
                        }
                    }
                    false // Permite outros eventos passarem
                }
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSPARENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "✅ Overlay de kiosk mostrado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao mostrar overlay: ${e.message}", e)
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                overlayView = null
                Log.d(TAG, "🔓 Overlay de kiosk removido")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao remover overlay: ${e.message}", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        Log.d(TAG, "KioskOverlayService destruído")
    }
    
    companion object {
        private const val TAG = "KioskOverlayService"
    }
}
