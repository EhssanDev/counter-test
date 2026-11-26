package dev.ehssan.countertest

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val scanCode = 100
    private lateinit var listBox: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private var query = ""
    private val thumbs = LruCache<String, Bitmap>(60)
    private val pageCounts = ConcurrentHashMap<String, Int>()

    // ---------- Theme colors ----------

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val cBg: Int get() = Color.parseColor(if (isDark) "#0F1115" else "#F4F5F7")
    private val cCard: Int get() = Color.parseColor(if (isDark) "#1A1D23" else "#FFFFFF")
    private val cText: Int get() = Color.parseColor(if (isDark) "#F1F3F4" else "#202124")
    private val cSub: Int get() = Color.parseColor(if (isDark) "#9AA0A6" else "#5F6368")
    private val cAccent: Int get() = Color.parseColor("#4F7CFF")
    private val cAccentSoft: Int get() = Color.parseColor(if (isDark) "#1E2A4A" else "#E3EBFF")
    private val cChipBg: Int get() = Color.parseColor(if (isDark) "#262A33" else "#EEF0F3")
    private val cDanger: Int get() = Color.parseColor("#E5484D")
    private val cDangerSoft: Int get() = Color.parseColor(if (isDark) "#3A1D1F" else "#FDE8E8")
    private val cThumbBg: Int get() = Color.parseColor(if (isDark) "#2A2E37" else "#E4E6EA")

    private fun dialogTheme() =
        if (isDark) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    // ---------- Helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun chip(label: String, fg: Int, bg: Int, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(fg)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(bg, 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }

    private fun formatSize(b: Long): String =
        if (b >= 1048576L) String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else "${maxOf(1L, b / 1024)} KB"

    // ---------- UI ----------

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        window.decorView.systemUiVisibility =
            if (isDark) 0
            else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(20), dp(16), dp(8))
        }

        val title = TextView(this).apply {
            text = "Kiosks Scanner"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText)
        }

        countText = TextView(this).apply {
            textSize = 14f
            setTextColor(cSub)
            setPadding(0, dp(2), 0, 0)
        }

        val scanBtn = TextView(this).apply {
            text = "📷   مسح ورقة جديدة"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(cAccent, 18)
            elevation = dp(4).toFloat()
            setOnClickListener { startScan() }
        }

        val search = EditText(this).apply {
            hint = "بحث في الملفات"
            setHintTextColor(cSub)
            setTextColor(cText)
            textSize = 15f
            isSingleLine = true
            background = rounded(cCard, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    refreshList()
                }
            })
        }

        emptyText = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(cSub)
            setPadding(0, dp(32), 0, 0)
        }

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(listBox)

        root.addView(title)
        root.addView(countText)
        root.addView(scanBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(76)).apply {
            topMargin = dp(16)
        })
        root.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })
        root.addView(emptyText)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    // ---------- Scan ----------

    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                startIntentSenderForResult(sender, scanCode, null, 0, 0, 0)
            }
            .addOnFailureListener { toast("تعذر فتح السكانر: ${it.message}") }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == scanCode && resultCode == RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
            val pdf = result?.pdf
            if (pdf != null) {
                savePdf(pdf.uri)
            } else {
                toast("ما رجع ملف PDF")
            }
        }
    }

    // ---------- Save to Documents/Scans ----------

    private fun savePdf(src: Uri) {
        thread {
            try {
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val fileName = "Scan_$stamp.pdf"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Scans")
                }
                val dest = contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                ) ?: throw Exception("تعذر إنشاء الملف")
                val input = contentResolver.openInputStream(src)
                    ?: throw Exception("تعذر قراءة الملف")
                val out = contentResolver.openOutputStream(dest)
                    ?: throw Exception("تعذر الكتابة")
                input.use { i -> out.use { o -> i.copyTo(o) } }
                runOnUiThread {
                    toast("تم الحفظ في Documents/Scans")
                    refreshList()
                    showRename(dest, fileName)
                }
            } catch (e: Exception) {
                runOnUiThread { toast("فشل الحفظ: ${e.message}") }
            }
        }
    }

    // ---------- Saved files list ----------

    private fun refreshList() {
        listBox.removeAllViews()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        var selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayListOf("Documents/Scans%", "application/pdf")
        if (query.isNotEmpty()) {
            selection += " AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            args.add("%$query%")
        }

        var total = 0
        var totalSize = 0L
        contentResolver.query(
            collection, projection, selection, args.toTypedArray(),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                val size = c.getLong(sizeCol)
                total++
                totalSize += size
                addRow(c.getString(nameCol), uri, size, c.getLong(dateCol))
            }
        }

        countText.text = if (total == 0) "" else "$total ملف · ${formatSize(totalSize)}"
        if (total == 0) {
            emptyText.text = if (query.isEmpty())
                "ما في ملفات بعد\nاضغط «مسح ورقة جديدة» للبدء"
            else "لا نتائج للبحث"
            emptyText.visibility = View.VISIBLE
        } else {
            emptyText.visibility = View.GONE
        }
    }

    private fun addRow(name: String, uri: Uri, size: Long, dateSec: Long) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cCard, 16)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { openPdf(uri) }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(cThumbBg, 8)
            clipToOutline = true
        }

        val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(dateSec * 1000))
        val metaBase = "${formatSize(size)} · $dateStr"

        val nameView = TextView(this).apply {
            text = name.removeSuffix(".pdf")
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val meta = TextView(this).apply {
            text = metaBase
            textSize = 13f
            setTextColor(cSub)
            setPadding(0, dp(4), 0, 0)
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(nameView)
        info.addView(meta)

        top.addView(thumb, LinearLayout.LayoutParams(dp(64), dp(88)))
        top.addView(info, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })

        // thumbnail + page count (cached)
        val key = uri.toString()
        val cached = thumbs.get(key)
        if (cached != null) {
            thumb.setImageBitmap(cached)
            pageCounts[key]?.let { meta.text = "الصفحات: $it · $metaBase" }
        } else {
            thread {
                val res = renderFirstPage(uri)
                if (res != null) {
                    thumbs.put(key, res.first)
                    pageCounts[key] = res.second
                    runOnUiThread {
                        thumb.setImageBitmap(res.first)
                        meta.text = "الصفحات: ${res.second} · $metaBase"
                    }
                }
            }
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        val lp = { last: Boolean ->
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (!last) marginEnd = dp(8)
            }
        }
        actions.addView(chip("مشاركة", cAccent, cAccentSoft) { sharePdf(uri) }, lp(false))
        actions.addView(chip("تسمية", cText, cChipBg) { showRename(uri, name) }, lp(false))
        actions.addView(chip("حذف", cDanger, cDangerSoft) { confirmDelete(uri) }, lp(true))

        card.addView(top)
        card.addView(actions)
        listBox.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
    }

    private fun renderFirstPage(uri: Uri): Pair<Bitmap, Int>? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { r ->
                    val n = r.pageCount
                    r.openPage(0).use { page ->
                        val w = 240
                        val h = (w.toFloat() * page.height / page.width).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Pair(bmp, n)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Actions ----------

    private fun openPdf(uri: Uri) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("ما في تطبيق يفتح PDF")
        }
    }

    private fun sharePdf(uri: Uri) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "مشاركة"))
    }

    private fun showRename(uri: Uri, current: String) {
        val input = EditText(this).apply {
            setText(current.removeSuffix(".pdf"))
            setSelectAllOnFocus(true)
            isSingleLine = true
            setTextColor(cText)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dlg = AlertDialog.Builder(this, dialogTheme())
            .setTitle("تسمية الملف")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ -> renameTo(uri, input.text.toString()) }
            .setNegativeButton("إلغاء", null)
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    private fun renameTo(uri: Uri, raw: String) {
        var n = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (n.isEmpty()) return
        if (!n.endsWith(".pdf", ignoreCase = true)) n += ".pdf"
        try {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, n)
            }
            contentResolver.update(uri, v, null, null)
        } catch (e: Exception) {
            toast("تعذر تغيير الاسم: ${e.message}")
        }
        refreshList()
    }

    private fun confirmDelete(uri: Uri) {
        AlertDialog.Builder(this, dialogTheme())
            .setMessage("حذف هذا الملف؟")
            .setPositiveButton("حذف") { _, _ ->
                try {
                    contentResolver.delete(uri, null, null)
                    thumbs.remove(uri.toString())
                } catch (e: Exception) {
                    toast("تعذر الحذف: ${e.message}")
                }
                refreshList()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
