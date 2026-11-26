package dev.ehssan.countertest

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val scanCode = 100
    private lateinit var listBox: LinearLayout
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101114"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "ماسح الأوراق"
            textSize = 26f
            setTextColor(Color.WHITE)
        }

        val scanBtn = Button(this).apply {
            text = "مسح ورقة جديدة"
            textSize = 20f
            setOnClickListener { startScan() }
        }

        val label = TextView(this).apply {
            text = "الملفات المحفوظة"
            textSize = 16f
            setTextColor(Color.parseColor("#9AA0A6"))
        }

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(listBox)

        emptyText = TextView(this).apply {
            text = "ما في ملفات بعد"
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(16), 0, 0)
        }

        root.addView(title)
        root.addView(scanBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(80)).apply {
            topMargin = dp(16)
            bottomMargin = dp(16)
        })
        root.addView(label)
        root.addView(emptyText)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

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
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Scan_$stamp.pdf")
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
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayOf("Documents/Scans%", "application/pdf")

        contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                addRow(c.getString(nameCol), uri)
            }
        }
        emptyText.visibility = if (listBox.childCount == 0) View.VISIBLE else View.GONE
    }

    private fun addRow(name: String, uri: Uri) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C1E24"))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        }
        val nameView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }
        val lp = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }

        buttons.addView(btn("فتح") { openPdf(uri) }, lp())
        buttons.addView(btn("مشاركة") { sharePdf(uri) }, lp())
        buttons.addView(btn("حذف") { confirmDelete(uri) }, lp())

        card.addView(nameView)
        card.addView(buttons)
        listBox.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }

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

    private fun confirmDelete(uri: Uri) {
        AlertDialog.Builder(this)
            .setMessage("حذف هذا الملف؟")
            .setPositiveButton("حذف") { _, _ ->
                try {
                    contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    toast("تعذر الحذف: ${e.message}")
                }
                refreshList()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
