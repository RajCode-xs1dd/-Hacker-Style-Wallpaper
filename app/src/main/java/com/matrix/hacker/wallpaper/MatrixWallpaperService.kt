package com.matrix.hacker.wallpaper

import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.random.Random

class MatrixWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return MatrixEngine()
    }

    inner class MatrixEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = Runnable { draw() }
        private var visible = false
        private lateinit var prefs: SharedPreferences

        // Configurable properties
        private var rainColor = Color.GREEN
        private var charType = "BINARY"
        private var speedBase = 15f
        private var densityScale = 1.0f
        private var fontSizeValue = 45f
        private var glowIntensity = 12f
        private var showClock = false
        private var clockColor = Color.WHITE
        private var bgBlur = 0f

        // Paints
        private val matrixPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }
        private val glowPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }
        private val headPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE; color = Color.WHITE }
        private val clockPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

        // State
        private var width = 0
        private var height = 0
        private var columnCount = 0
        private lateinit var columnOffsets: FloatArray
        private lateinit var speeds: FloatArray
        
        private var speedMultiplier = 1.0f
        private var lastTouchTime = 0L

        private var hackerBitmap: Bitmap? = null
        private val hackerRect = RectF()
        private val bgPaint = Paint().apply { isFilterBitmap = true }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            prefs = getSharedPreferences("matrix_prefs", MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            loadSettings()
            loadHackerBitmap()
            Log.d("MatrixEngine", "Engine created and listener registered")
        }

        private fun loadSettings() {
            try {
                rainColor = prefs.getInt("rain_color", Color.GREEN)
                charType = prefs.getString("char_type", "BINARY") ?: "BINARY"
                speedBase = prefs.getFloat("rain_speed", 15f)
                densityScale = prefs.getFloat("rain_density", 1.0f)
                fontSizeValue = prefs.getFloat("rain_font_size", 45f)
                glowIntensity = prefs.getFloat("glow_intensity", 12f)
                showClock = prefs.getBoolean("show_clock", false)
                clockColor = prefs.getInt("clock_color", Color.WHITE)
                bgBlur = prefs.getFloat("bg_blur", 0f)

                matrixPaint.color = rainColor
                glowPaint.color = rainColor
                glowPaint.maskFilter = BlurMaskFilter(glowIntensity.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
                headPaint.setShadowLayer(glowIntensity + 3f, 0f, 0f, rainColor)
                
                clockPaint.color = clockColor
                clockPaint.textSize = 150f
                clockPaint.setShadowLayer(20f, 0f, 0f, Color.BLACK)
                
                if (bgBlur > 0) {
                    bgPaint.maskFilter = BlurMaskFilter(bgBlur.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
                } else {
                    bgPaint.maskFilter = null
                }

                if (width > 0) initColumns()
                Log.d("MatrixEngine", "Settings loaded: Speed=$speedBase, Density=$densityScale, Size=$fontSizeValue")
            } catch (e: Exception) {
                Log.e("MatrixEngine", "Error loading settings", e)
            }
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            Log.d("MatrixEngine", "onSharedPreferenceChanged: $key")
            if (key == "background_type" || key == "custom_bg_uri") {
                loadHackerBitmap()
            }
            loadSettings()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                draw()
            } else {
                handler.removeCallbacks(drawRunner)
            }
        }

        private fun loadHackerBitmap() {
            val bgType = prefs.getString("background_type", "hacker_1") ?: "hacker_1"
            
            if (bgType == "custom") {
                val uriStr = prefs.getString("custom_bg_uri", null)
                if (uriStr != null) {
                    try {
                        val uri = Uri.parse(uriStr)
                        contentResolver.openInputStream(uri).use { inputStream ->
                            hackerBitmap?.recycle()
                            hackerBitmap = BitmapFactory.decodeStream(inputStream)
                        }
                        setupHackerRect()
                        return
                    } catch (e: Exception) {
                        Log.e("MatrixWallpaper", "Error loading custom URI: $uriStr", e)
                    }
                }
            }

            val fileName = when (bgType) {
                "logo_bg" -> "hacker_wallpaper_logo.png"
                "hacker_1" -> "hacker_silhouette.png"
                "hacker_2" -> "hacker_hood_2.png"
                "anonymous" -> "anonymous_mask.png"
                "city" -> "matrix_city.png"
                "skull" -> "cyber_skull.png"
                "terminal" -> "terminal_screen.png"
                else -> "hacker_silhouette.png"
            }
            
            try {
                assets.open(fileName).use { inputStream ->
                    hackerBitmap?.recycle()
                    hackerBitmap = BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                Log.e("MatrixWallpaper", "Error loading bitmap: $fileName", e)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            initColumns()
            setupHackerRect()
        }

        private fun initColumns() {
            val baseColumns = (width / fontSizeValue).toInt() + 1
            columnCount = (baseColumns * densityScale).toInt().coerceAtLeast(5)
            
            val effectiveFontSize = width.toFloat() / columnCount
            matrixPaint.textSize = effectiveFontSize
            glowPaint.textSize = effectiveFontSize
            headPaint.textSize = effectiveFontSize

            columnOffsets = FloatArray(columnCount) { Random.nextFloat() * height }
            speeds = FloatArray(columnCount) { Random.nextFloat() * speedBase + (speedBase / 2) }
        }

        private fun setupHackerRect() {
            hackerBitmap?.let { bmp ->
                val bmpWidth = bmp.width.toFloat()
                val bmpHeight = bmp.height.toFloat()
                val screenWidth = width.toFloat()
                val screenHeight = height.toFloat()
                
                // Scale to fit screen width
                val scale = screenWidth / bmpWidth
                val drawWidth = screenWidth
                val drawHeight = bmpHeight * scale
                
                // Position at the bottom
                hackerRect.set(0f, screenHeight - drawHeight, drawWidth, screenHeight)
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                speedMultiplier = 6.0f
                lastTouchTime = System.currentTimeMillis()
            }
            super.onTouchEvent(event)
        }

        private fun draw() {
            if (System.currentTimeMillis() - lastTouchTime > 1500) {
                speedMultiplier = 1.0f
            }

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)

                    // 1. Draw Hacker Silhouette Overlay
                    hackerBitmap?.let { bmp ->
                        canvas.drawBitmap(bmp, null, hackerRect, bgPaint)
                    }

                    // 2. Draw Matrix Rain
                    drawMatrixRain(canvas)

                    // 3. Draw Clock if enabled
                    if (showClock) {
                        drawClock(canvas)
                    }
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            
            handler.removeCallbacks(drawRunner)
            if (visible) {
                handler.postDelayed(drawRunner, 25)
            }
        }

        private fun drawClock(canvas: Canvas) {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val time = sdf.format(java.util.Date())
            canvas.drawText(time, width / 2f, height / 3f, clockPaint)
        }

        private fun getChar(): String {
            return when (charType) {
                "BINARY" -> if (Random.nextBoolean()) "0" else "1"
                "HEX" -> "0123456789ABCDEF"[Random.nextInt(16)].toString()
                "MATRIX" -> "日ሀ十廿卅卌一二三四五六七八九十ABCDEFGHIJKLMNOPQRSTUVWXYZ"[Random.nextInt(40)].toString()
                "MIXED" -> {
                    val pool = "010101日ሀ十廿卅卌ABCDEF"
                    pool[Random.nextInt(pool.length)].toString()
                }
                else -> if (Random.nextBoolean()) "0" else "1"
            }
        }

        private fun drawMatrixRain(canvas: Canvas) {
            val fontSize = matrixPaint.textSize
            val trailLength = 25
            
            for (i in 0 until columnCount) {
                val x = i * fontSize
                val y = columnOffsets[i]
                
                for (j in 0 until trailLength) {
                    val charY = y - (j * fontSize)
                    
                    if (charY > -fontSize && charY < height + fontSize) {
                        val alpha = (255 * (1 - j / trailLength.toFloat())).toInt()
                        val char = getChar()
                        
                        if (j == 0) {
                            headPaint.alpha = 255
                            canvas.drawText(char, x, charY, headPaint)
                        } else {
                            matrixPaint.alpha = alpha
                            glowPaint.alpha = alpha / 2
                            canvas.drawText(char, x, charY, glowPaint)
                            canvas.drawText(char, x, charY, matrixPaint)
                        }
                    }
                }

                columnOffsets[i] += speeds[i] * speedMultiplier
                
                if (columnOffsets[i] - (trailLength * fontSize) > height) {
                    columnOffsets[i] = -fontSize * (1 + Random.nextInt(10))
                    speeds[i] = Random.nextFloat() * speedBase + (speedBase / 2)
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            hackerBitmap?.recycle()
            hackerBitmap = null
        }
    }
}
