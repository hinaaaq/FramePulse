package com.shin.framepulse

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var shizukuStatusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var lockSwitch: Switch

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku aktif: True Game FPS siap!", Toast.LENGTH_SHORT).show()
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(32))
        }

        val title = TextView(this).apply {
            text = "FramePulse HUD"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val subtitle = TextView(this).apply {
            text = "Kustomisasi lengkap overlay anti-ganggu"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(14))
        }

        fun makeCard(): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(12).toFloat()
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
                layoutParams = lp
            }
        }

        fun sectionHeader(textStr: String): TextView {
            return TextView(this).apply {
                text = textStr
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(0, 0, 0, dp(8))
            }
        }

        fun styledBtn(label: String, bgHex: String, weight: Float = 0f, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                textSize = 12.5f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(10).toFloat()
                }
                val lp = if (weight > 0f) {
                    LinearLayout.LayoutParams(0, dp(40), weight).apply {
                        marginEnd = dp(6)
                    }
                } else {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                }
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        val statusCard = makeCard()
        statusText = TextView(this).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 0, 0, dp(4))
        }
        shizukuStatusText = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(6))
        }
        statusCard.addView(statusText)
        statusCard.addView(shizukuStatusText)

        statusCard.addView(styledBtn("1. Izinkan Tampil di Atas App Lain", "#334155") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            } else {
                Toast.makeText(this, "Izin Overlay sudah aktif", Toast.LENGTH_SHORT).show()
            }
        })

        statusCard.addView(styledBtn("2. Hubungkan Shizuku (True Game FPS)", "#0284C7") {
            try {
                if (!Shizuku.pingBinder()) {
                    Toast.makeText(this, "Aktifkan Shizuku dulu via Wireless Debugging / aShell", Toast.LENGTH_LONG).show()
                } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(100)
                } else {
                    Toast.makeText(this, "Shizuku sudah terhubung!", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) {
                Toast.makeText(this, "Aplikasi Shizuku belum terpasang", Toast.LENGTH_SHORT).show()
            }
        })

        toggleButton = styledBtn("MULAI OVERLAY HUD", "#16A34A") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan izin Overlay dulu!", Toast.LENGTH_SHORT).show()
                return@styledBtn
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (OverlayService.isRunning) {
                startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
            } else {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                })
            }
            toggleButton.postDelayed({ refreshStatus() }, 300)
        }
        statusCard.addView(toggleButton)

        val layoutCard = makeCard()
        layoutCard.addView(sectionHeader("Bentuk HUD & Kecepatan Update"))

        val layoutRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        layoutRow.addView(styledBtn("Horizontal", "#334155", 1f) {
            Prefs.layoutMode = Prefs.LAYOUT_HORIZONTAL
            notifyOverlayConfigChanged()
        })
        layoutRow.addView(styledBtn("Vertikal", "#334155", 1f) {
            Prefs.layoutMode = Prefs.LAYOUT_VERTICAL
            notifyOverlayConfigChanged()
        })
        layoutRow.addView(styledBtn("Mini", "#334155", 1f) {
            Prefs.layoutMode = Prefs.LAYOUT_MINI
            notifyOverlayConfigChanged()
        })
        layoutCard.addView(layoutRow)

        val intervalLabel = TextView(this).apply {
            text = "Interval Refresh: ${Prefs.updateIntervalMs} ms"
            textSize = 12.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(10), 0, dp(6))
        }
        layoutCard.addView(intervalLabel)

        val intervalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(250L to "0.25s", 500L to "0.5s", 1000L to "1.0s").forEach { (ms, label) ->
            intervalRow.addView(styledBtn(label, "#334155", 1f) {
                Prefs.updateIntervalMs = ms
                intervalLabel.text = "Interval Refresh: ${ms} ms"
                notifyOverlayConfigChanged()
            })
        }
        layoutCard.addView(intervalRow)

        val metricCard = makeCard()
        metricCard.addView(sectionHeader("Pilih Metrik yang Ditampilkan"))

        fun addMetricCheckbox(label: String, initial: Boolean, onChanged: (Boolean) -> Unit): CheckBox {
            return CheckBox(this).apply {
                text = label
                isChecked = initial
                textSize = 13f
                setTextColor(Color.parseColor("#E2E8F0"))
                setOnCheckedChangeListener { _, isChecked ->
                    onChanged(isChecked)
                    notifyOverlayConfigChanged()
                }
            }
        }

        metricCard.addView(addMetricCheckbox("FPS (Frame Per Second)", Prefs.showFps) { Prefs.showFps = it })
        metricCard.addView(addMetricCheckbox("CPU Load (%)", Prefs.showCpu) { Prefs.showCpu = it })
        metricCard.addView(addMetricCheckbox("CPU Peak Clock (GHz)", Prefs.showClock) { Prefs.showClock = it })
        metricCard.addView(addMetricCheckbox("GPU Load (%)", Prefs.showGpu) { Prefs.showGpu = it })
        metricCard.addView(addMetricCheckbox("RAM Terpakai (GB)", Prefs.showRam) { Prefs.showRam = it })
        metricCard.addView(addMetricCheckbox("Suhu Baterai (°C)", Prefs.showTemp) { Prefs.showTemp = it })
        metricCard.addView(addMetricCheckbox("Konsumsi Daya (Watt)", Prefs.showWatt) { Prefs.showWatt = it })

        val visualCard = makeCard()
        visualCard.addView(sectionHeader("Ukuran & Transparansi (Biar Tidak Ganggu)"))

        val alphaPercentInit = ((Prefs.bgAlpha / 255f) * 100).roundToInt()
        val alphaLabel = TextView(this).apply {
            text = "Opacity Kotak Background: $alphaPercentInit% (0% = Teks Melayang Saja)"
            textSize = 12.5f
            setTextColor(Color.parseColor("#CBD5E1"))
        }
        val alphaSeek = SeekBar(this).apply {
            max = 255
            progress = Prefs.bgAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    Prefs.bgAlpha = progress
                    val pct = ((progress / 255f) * 100).roundToInt()
                    alphaLabel.text = "Opacity Kotak Background: $pct% (0% = Teks Melayang Saja)"
                    notifyOverlayConfigChanged()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val sizeLabel = TextView(this).apply {
            text = "Ukuran Teks HUD: ${Prefs.textSizeSp} sp"
            textSize = 12.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(10), 0, 0)
        }
        val sizeSeek = SeekBar(this).apply {
            max = 16
            progress = ((Prefs.textSizeSp - 8f) * 2f).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val spVal = 8f + (progress * 0.5f)
                    Prefs.textSizeSp = spVal
                    sizeLabel.text = "Ukuran Teks HUD: $spVal sp"
                    notifyOverlayConfigChanged()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        lockSwitch = Switch(this).apply {
            text = "Kunci Sentuhan HUD (Touch-Through ke Game)"
            textSize = 13f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(12), 0, 0)
            isChecked = OverlayService.isTouchLocked
            setOnCheckedChangeListener { _, checked ->
                if (OverlayService.isRunning && OverlayService.isTouchLocked != checked) {
                    startService(Intent(this@MainActivity, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_TOGGLE_LOCK
                    })
                }
            }
        }

        visualCard.addView(alphaLabel)
        visualCard.addView(alphaSeek)
        visualCard.addView(sizeLabel)
        visualCard.addView(sizeSeek)
        visualCard.addView(lockSwitch)

        val posCard = makeCard()
        posCard.addView(sectionHeader("Preset Posisi Cepat (Atau Geser Manual)"))

        val dm = resources.displayMetrics
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(styledBtn("Kiri Atas", "#334155", 1f) {
            setPresetPosition(dp(12), dp(36))
        })
        topRow.addView(styledBtn("Tengah Atas", "#334155", 1f) {
            setPresetPosition((dm.widthPixels * 0.36f).toInt(), dp(36))
        })
        topRow.addView(styledBtn("Kanan Atas", "#334155", 1f) {
            setPresetPosition((dm.widthPixels * 0.65f).toInt(), dp(36))
        })

        val botRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        botRow.addView(styledBtn("Kiri Bawah", "#334155", 1f) {
            setPresetPosition(dp(12), (dm.heightPixels * 0.85f).toInt())
        })
        botRow.addView(styledBtn("Tengah Bawah", "#334155", 1f) {
            setPresetPosition((dm.widthPixels * 0.36f).toInt(), (dm.heightPixels * 0.85f).toInt())
        })
        botRow.addView(styledBtn("Kanan Bawah", "#334155", 1f) {
            setPresetPosition((dm.widthPixels * 0.65f).toInt(), (dm.heightPixels * 0.85f).toInt())
        })

        posCard.addView(topRow)
        posCard.addView(botRow)

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusCard)
        root.addView(layoutCard)
        root.addView(metricCard)
        root.addView(visualCard)
        root.addView(posCard)

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun setPresetPosition(x: Int, y: Int) {
        Prefs.posX = x
        Prefs.posY = y
        notifyOverlayConfigChanged()
    }

    private fun notifyOverlayConfigChanged() {
        if (OverlayService.isRunning) {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_CONFIG
            })
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    private fun refreshStatus() {
        val canOverlay = Settings.canDrawOverlays(this)
        val running = OverlayService.isRunning
        statusText.text = buildString {
            append("Izin Overlay : ${if (canOverlay) "AKTIF" else "BELUM AKTIF"}\n")
            append("Status HUD   : ${if (running) "BERJALAN" else "MATI"}")
        }

        val shizukuState = try {
            when {
                !Shizuku.pingBinder() -> "Shizuku      : Tidak aktif (Mode Standar)"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    "Shizuku      : TERHUBUNG (True Game FPS)"
                else -> "Shizuku      : Terdeteksi (Klik tombol 2)"
            }
        } catch (_: Throwable) {
            "Shizuku      : Tidak terpasang (Mode Standar)"
        }
        shizukuStatusText.text = shizukuState

        lockSwitch.isChecked = OverlayService.isTouchLocked
        toggleButton.text = if (running) "MATIKAN OVERLAY HUD" else "MULAI OVERLAY HUD"
        (toggleButton.background as? GradientDrawable)?.setColor(
            Color.parseColor(if (running) "#DC2626" else "#16A34A")
        )
    }
}
