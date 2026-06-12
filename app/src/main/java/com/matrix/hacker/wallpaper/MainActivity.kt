package com.matrix.hacker.wallpaper

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import java.util.Random

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("matrix_prefs", Context.MODE_PRIVATE) }
    private val themePrefs by lazy { getSharedPreferences("matrix_themes", Context.MODE_PRIVATE) }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("custom_bg_uri", uri.toString()).putString("background_type", "custom").apply()
                Toast.makeText(this, "Custom Background Applied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadLogo()
        setupButtons()
    }

    private fun loadLogo() {
        try {
            assets.open("hacker_wallpaper_logo.png").use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                findViewById<ImageView>(R.id.imgLogo).setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            showAboutDialog()
        }

        findViewById<Button>(R.id.btnSetting).setOnClickListener {
            showCustomizeDialog()
        }

        findViewById<Button>(R.id.btnShare).setOnClickListener {
            val shareLink = "https://github.com/rajcode_xs1dd/MatrixWallpaper/releases/download/v2.0.0/app-debug.apk"
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Check out the 'Hacker Style Wallpaper'! Download the Wallpaper here: $shareLink")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Share App Via"))
        }

        findViewById<Button>(R.id.btnRate).setOnClickListener {
            Toast.makeText(this, "Rating feature is under development. Coming soon!", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnSetWallpaper).setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, MatrixWallpaperService::class.java)
            )
            startActivity(intent)
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("About")
            .setMessage("How to use:\n1. Open Setting to customize your rain.\n2. Go to My Themes to save or load presets.\n3. Click 'Set/Activate wallpaper' to apply.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCharacterDialog(onChanged: () -> Unit) {
        val types = arrayOf("Binary (0101)", "Hexadecimal (A F 9)", "Matrix Symbols", "Mixed Mode")
        val typeKeys = arrayOf("BINARY", "HEX", "MATRIX", "MIXED")
        
        val current = prefs.getString("char_type", "BINARY")
        val checkedItem = typeKeys.indexOf(current)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Character Type")
            .setSingleChoiceItems(types, checkedItem) { dialog, which ->
                prefs.edit().putString("char_type", typeKeys[which]).apply()
                onChanged()
                dialog.dismiss()
            }
            .show()
    }

    private fun showColorDialog(onChanged: (Int) -> Unit) {
        val colors = arrayOf("Matrix Green", "Neon Blue", "Red", "Cyan", "Purple", "White", "Gold")
        val colorValues = intArrayOf(
            Color.GREEN, 
            Color.parseColor("#00E5FF"), 
            Color.RED, 
            Color.CYAN, 
            Color.parseColor("#D500F9"), 
            Color.WHITE, 
            Color.parseColor("#FFD600")
        )

        val currentColor = prefs.getInt("rain_color", Color.GREEN)
        var checkedItem = colorValues.indexOf(currentColor)
        if (checkedItem == -1) checkedItem = 0

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Rain Color")
            .setSingleChoiceItems(colors, checkedItem) { dialog, which ->
                prefs.edit().putInt("rain_color", colorValues[which]).apply()
                onChanged(colorValues[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showBackgroundDialog(onChanged: () -> Unit) {
        val bgs = arrayOf("New Hacker Logo", "Hacker Hood 1", "Hacker Hood 2", "Anonymous Mask", "Matrix City", "Cyber Skull", "Terminal Screen", "Choose from Gallery")
        val bgKeys = arrayOf("logo_bg", "hacker_1", "hacker_2", "anonymous", "city", "skull", "terminal", "custom")

        val currentBg = prefs.getString("background_type", "hacker_1")
        val checkedItem = bgKeys.indexOf(currentBg)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Background")
            .setSingleChoiceItems(bgs, checkedItem) { dialog, which ->
                if (bgKeys[which] == "custom") {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    pickImageLauncher.launch(intent)
                    dialog.dismiss()
                } else {
                    prefs.edit().putString("background_type", bgKeys[which]).apply()
                    onChanged()
                    dialog.dismiss()
                }
            }
            .show()
    }

    private fun showThemesDialog(onChanged: () -> Unit) {
        val themes = themePrefs.all.keys.toMutableList()
        val options = themes.toMutableList()
        options.add(0, "+ Save Current Settings as Theme")

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Saved Themes")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    showSaveThemeDialog()
                } else {
                    val themeName = themes[which - 1]
                    loadTheme(themeName)
                    onChanged()
                }
            }
            .show()
    }

    private fun showSaveThemeDialog() {
        val input = EditText(this)
        input.hint = "Theme Name"
        
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Save Theme")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) {
                    saveCurrentAsTheme(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveCurrentAsTheme(name: String) {
        val themeObj = JSONObject()
        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            themeObj.put(key, value)
        }
        themePrefs.edit().putString(name, themeObj.toString()).apply()
        Toast.makeText(this, "Theme '$name' Saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadTheme(name: String) {
        val themeStr = themePrefs.getString(name, null) ?: return
        val themeObj = JSONObject(themeStr)
        val editor = prefs.edit()
        
        val keys = themeObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                when (val value = themeObj.get(key)) {
                    is Int -> editor.putInt(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Long -> editor.putLong(key, value)
                }
            } catch (e: Exception) {}
        }
        editor.apply()
        Toast.makeText(this, "Theme '$name' Loaded!", Toast.LENGTH_SHORT).show()
    }

    private fun showCustomizeDialog() {
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_customize, null)
        
        val previewBg = view.findViewById<ImageView>(R.id.previewBg)
        val previewText = view.findViewById<TextView>(R.id.previewText)
        
        val speedSeek = view.findViewById<SeekBar>(R.id.speedSeekBar)
        val densitySeek = view.findViewById<SeekBar>(R.id.densitySeekBar)
        val fontSizeSeek = view.findViewById<SeekBar>(R.id.fontSizeSeekBar)
        val glowSeek = view.findViewById<SeekBar>(R.id.glowSeekBar)

        fun updatePreview() {
            val color = prefs.getInt("rain_color", Color.GREEN)
            previewText.setTextColor(color)
            
            val density = (densitySeek.progress / 100f) * 2.0f + 0.5f
            previewText.alpha = (density / 2.5f).coerceIn(0.2f, 1.0f)
            
            val fontSize = (fontSizeSeek.progress / 100f) * 40f + 15f
            previewText.textSize = fontSize
            
            val glow = (glowSeek.progress / 100f) * 10f
            previewText.setShadowLayer(glow, 0f, 0f, color)

            val charType = prefs.getString("char_type", "BINARY")
            val previewString = when (charType) {
                "HEX" -> "A F 9 2 E 4 1 B\nD 3 C 8 5 0 F 7\n2 9 A E 1 4 B D"
                "MATRIX" -> "日 ﾊ ﾐ ﾋ ｰ ｳ ｼ\nﾅ ﾓ ﾆ ｻ ﾜ ﾂ ｵ\nﾘ ｱ ﾎ ﾃ ﾏ ｹ ﾒ"
                "MIXED" -> "0 ﾊ F 2 ｳ 9 B\nD ﾅ 0 8 ﾜ 3 7\n日 9 A ｰ 1 ﾂ B"
                else -> "0101010101\n1010101010\n0101010101\n1100110011"
            }
            previewText.text = previewString

            val bgType = prefs.getString("background_type", "hacker_1") ?: "hacker_1"
            try {
                if (bgType == "custom") {
                    val uriStr = prefs.getString("custom_bg_uri", null)
                    if (uriStr != null) {
                        contentResolver.openInputStream(Uri.parse(uriStr)).use {
                            previewBg.setImageBitmap(BitmapFactory.decodeStream(it))
                        }
                    }
                } else {
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
                    assets.open(fileName).use {
                        previewBg.setImageBitmap(BitmapFactory.decodeStream(it))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { updatePreview() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }

        speedSeek.setOnSeekBarChangeListener(seekListener)
        densitySeek.setOnSeekBarChangeListener(seekListener)
        fontSizeSeek.setOnSeekBarChangeListener(seekListener)
        glowSeek.setOnSeekBarChangeListener(seekListener)

        speedSeek.progress = ((prefs.getFloat("rain_speed", 15f) - 5f) / 30f * 100).toInt()
        densitySeek.progress = ((prefs.getFloat("rain_density", 1.0f) - 0.5f) / 2.0f * 100).toInt()
        fontSizeSeek.progress = ((prefs.getFloat("rain_font_size", 45f) - 20f) / 80f * 100).toInt()
        glowSeek.progress = (prefs.getFloat("glow_intensity", 12f) / 25f * 100).toInt()

        updatePreview()

        view.findViewById<Button>(R.id.dlgBtnBg).setOnClickListener { showBackgroundDialog { updatePreview() } }
        view.findViewById<Button>(R.id.dlgBtnColor).setOnClickListener { showColorDialog { updatePreview() } }
        view.findViewById<Button>(R.id.dlgBtnChar).setOnClickListener { showCharacterDialog { updatePreview() } }
        view.findViewById<Button>(R.id.dlgBtnTheme).setOnClickListener { showThemesDialog { updatePreview() } }
        view.findViewById<Button>(R.id.dlgBtnAbout).setOnClickListener { showAboutDialog() }

        builder.setView(view)
        builder.setTitle("Pro Dashboard")
        builder.setPositiveButton("Apply All") { _, _ ->
            val speed = (speedSeek.progress / 100f) * 30f + 5f
            val density = (densitySeek.progress / 100f) * 2.0f + 0.5f
            val fontSize = (fontSizeSeek.progress / 100f) * 80f + 20f
            val glow = (glowSeek.progress / 100f) * 25f
            
            prefs.edit()
                .putFloat("rain_speed", speed)
                .putFloat("rain_density", density)
                .putFloat("rain_font_size", fontSize)
                .putFloat("glow_intensity", glow)
                .apply()
            
            Toast.makeText(this, "Settings Applied!", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Close", null)
        builder.show()
    }
}
