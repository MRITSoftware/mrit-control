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
                var isTrackingBackGesture = false
                var isBackGesture = false
                
                setOnTouchListener { _, event ->
                    val screenWidth = resources.displayMetrics.widthPixels
                    val edgeThreshold = 30f // Área de 30px nas bordas laterais para detectar gesto de voltar
                    
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            isTrackingBackGesture = false
                            isBackGesture = false
                            
                            // Verifica se o toque começou na borda lateral (esquerda ou direita)
                            val isLeftEdge = startX < edgeThreshold
                            val isRightEdge = startX > screenWidth - edgeThreshold
                            
                            // Só começa a rastrear se for na borda lateral (gesto de voltar)
                            if (isLeftEdge || isRightEdge) {
                                isTrackingBackGesture = true
                                Log.d(TAG, "🔍 Rastreando possível gesto de voltar (borda ${if (isLeftEdge) "esquerda" else "direita"})")
                            }
                            
                            // NÃO bloqueia o ACTION_DOWN - permite que o app receba o toque
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!isTrackingBackGesture) {
                                return@setOnTouchListener false // Não rastreia se não começou na borda lateral
                            }
                            
                            val deltaX = event.x - startX
                            val deltaY = event.y - startY
                            val absDeltaX = Math.abs(deltaX)
                            val absDeltaY = Math.abs(deltaY)
                            
                            val isLeftEdge = startX < edgeThreshold
                            val isRightEdge = startX > screenWidth - edgeThreshold
                            
                            // Gesto de voltar: swipe da borda lateral para dentro da tela
                            // Deve ser principalmente horizontal (deltaX > deltaY)
                            if ((isLeftEdge || isRightEdge) && absDeltaX > 50f) {
                                // Verifica se está se movendo para dentro da tela (direção correta do gesto)
                                val isMovingInward = (isLeftEdge && deltaX > 0) || (isRightEdge && deltaX < 0)
                                
                                // Só bloqueia se for movimento horizontal para dentro
                                if (isMovingInward && absDeltaX > absDeltaY) {
                                    isBackGesture = true
                                    Log.d(TAG, "🔒 Gesto de VOLTAR detectado e bloqueado! (swipe da borda ${if (isLeftEdge) "esquerda" else "direita"})")
                                    return@setOnTouchListener true // Bloqueia o gesto
                                }
                            }
                            
                            false // Permite movimento se não for gesto de voltar
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isBackGesture) {
                                // Se foi um gesto de voltar, bloqueia o ACTION_UP também
                                Log.d(TAG, "🔒 Finalizando bloqueio de gesto de voltar")
                                isTrackingBackGesture = false
                                isBackGesture = false
                                return@setOnTouchListener true
                            }
                            
                            isTrackingBackGesture = false
                            isBackGesture = false
                            false // Permite ACTION_UP normal passar
                        }
                    }
                    false // Por padrão, permite eventos passarem
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
