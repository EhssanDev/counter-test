package dev.ehssan.countertest

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Random
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val pickCode = 1
    private var sw = 0
    private var sh = 0
    private var inPreview = false
    private var batch = 0L
    private lateinit var gridView: View
    private lateinit var grid: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        sw = dm.widthPixels
        sh = dm.heightPixels
        batch = System.currentTimeMillis()
        gridView = buildGridScreen()
        setContentView(gridView)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- Generated wallpapers ----------

    private fun generate(seed: Long, w: Int, h: Int): Bitmap {
        val r = Random(seed * -7046029254386353131L)
        r.nextInt(); r.nextInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()

        val hue = r.nextInt(360).toFloat()
        val c1 = Color.HSVToColor(floatArrayOf(hue, 0.65f + r.nextFloat() * 0.3f, 0.35f + r.nextFloat() * 0.4f))
        val c2 = Color.HSVToColor(floatArrayOf((hue + 40 + r.nextInt(100)) % 360, 0.6f + r.nextFloat() * 0.4f, 0.5f + r.nextFloat() * 0.5f))
        val c3 = Color.HSVToColor(floatArrayOf((hue + 180 + r.nextInt(60)) % 360, 0.5f, 0.2f + r.nextFloat() * 0.3f))

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (r.nextInt(3)) {
            0 -> {
                p.shader = LinearGradient(0f, 0f, wf, hf, intArrayOf(c1, c2, c3), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, wf, hf, p)
            }
            1 -> {
                val cx = wf * (0.2f + r.nextFloat() * 0.6f)
                val cy = hf * (0.2f + r.nextFloat() * 0.6f)
                p.shader = RadialGradient(cx, cy, maxOf(wf, hf) * 0.9f, intArrayOf(c2, c1, c3), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, wf, hf, p)
            }
            else -> {
                p.shader = LinearGradient(0f, 0f, 0f, hf, intArrayOf(c1, c3), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, wf, hf, p)
                val q = Paint(Paint.ANTI_ALIAS_FLAG)
                val count = 5 + r.nextInt(3)
                for (i in 0 until count) {
                    val cx = wf * r.nextFloat()
                    val cy = hf * r.nextFloat()
                    val rad = wf * (0.25f + r.nextFloat() * 0.5f)
                    val col = Color.argb(
                        110 + r.nextInt(80),
                        Color.red(c2), Color.green(c2), Color.blue(c2)
                    )
                    q.shader = RadialGradient(cx, cy, rad, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
                    c.drawCircle(cx, cy, rad, q)
                }
            }
        }
        return bmp
    }

    // ---------- Grid screen ----------

    private fun buildGridScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101114"))
        }
        val title = TextView(this).apply {
            text = "خلفيات"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        val newBtn = Button(this).apply {
            text = "خلفيات جديدة"
            setOnClickListener {
                batch = System.nanoTime()
                fillGrid()
            }
        }
        val pickBtn = Button(this).apply {
            text = "من المعرض"
            setOnClickListener { pickFromGallery() }
        }
        bar.addView(newBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(pickBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val scroll = ScrollView(this)
        scroll.addView(grid)

        root.addView(title)
        root.addView(bar)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        fillGrid()
        return root
    }

    private fun fillGrid() {
        grid.removeAllViews()
        val tw = (sw - dp(8) * 4) / 2
        val th = tw * sh / sw
        for (row in 0 until 6) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0..1) {
                val seed = batch + row * 2 + col
                val iv = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(generate(seed, tw / 2, th / 2))
                    setOnClickListener { showPreview(generate(seed, sw, sh)) }
                }
                val lp = LinearLayout.LayoutParams(tw, th).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                line.addView(iv, lp)
            }
            grid.addView(line)
        }
    }

    // ---------- Preview screen ----------

    private fun showPreview(bmp: Bitmap) {
        inPreview = true

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }
        val lp = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bmp)
        }
        root.addView(iv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("الرئيسية") { setWall(bmp, WallpaperManager.FLAG_SYSTEM) }, lp())
        row.addView(btn("القفل") { setWall(bmp, WallpaperManager.FLAG_LOCK) }, lp())
        row.addView(btn("الاثنتين") {
            setWall(bmp, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        }, lp())

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        bar.addView(row)
        bar.addView(btn("رجوع") { goBack() })

        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))
        setContentView(root)
    }

    private fun goBack() {
        inPreview = false
        setContentView(gridView)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (inPreview) goBack() else super.onBackPressed()
    }

    private fun setWall(bmp: Bitmap, flag: Int) {
        thread {
            try {
                WallpaperManager.getInstance(this).setBitmap(bmp, null, true, flag)
                runOnUiThread { toast("تم تعيين الخلفية") }
            } catch (e: Exception) {
                runOnUiThread { toast("فشل: ${e.message}") }
            }
        }
    }

    // ---------- Gallery ----------

    @Suppress("DEPRECATION")
    private fun pickFromGallery() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(i, pickCode)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val bmp = decode(uri)
            if (bmp == null) {
                toast("تعذر فتح الصورة")
                return
            }
            showPreview(cropToScreen(bmp))
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= sw && o.outHeight / (s * 2) >= sh) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, o2)
            } ?: return null

            val rot = contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f
                    3 -> 180f
                    8 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rot == 0f) bmp else {
                val m = Matrix().apply { postRotate(rot) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cropToScreen(src: Bitmap): Bitmap {
        val scale = maxOf(sw.toFloat() / src.width, sh.toFloat() / src.height)
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val dw = src.width * scale
        val dh = src.height * scale
        val m = Matrix()
        m.postScale(scale, scale)
        m.postTranslate((sw - dw) / 2f, (sh - dh) / 2f)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
