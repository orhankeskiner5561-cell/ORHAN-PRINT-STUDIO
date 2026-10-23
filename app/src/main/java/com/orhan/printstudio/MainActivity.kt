package com.orhan.printstudio

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.content.Context
import android.view.Gravity
import android.widget.*
import android.view.View
import android.widget.AdapterView
import java.io.FileOutputStream
import android.graphics.pdf.PdfDocument
import kotlin.math.min
import kotlin.math.max

class MainActivity : Activity() {
    private var photo: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var mode: Spinner
    private lateinit var paper: Spinner
    private lateinit var fitting: Spinner
    private val pickPhoto = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,64,24,64) }
        val title = TextView(this).apply { text = "ORHAN PRINT STUDIO"; textSize = 24f; gravity = Gravity.CENTER; setPadding(0,20,0,20) }
        root.addView(title)
        preview = ImageView(this).apply { setBackgroundColor(0xffeeeeee.toInt()); scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        fun selector(items: List<String>): Spinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items); root.addView(this) }
        paper = selector(listOf("A4", "10 x 15 cm", "A5"))
        fitting = selector(listOf("Tamamını sığdır (kırpma yok)", "Sayfayı doldur (kırpabilir)"))
        mode = selector(listOf("Orijinal renkli", "Siyah-beyaz"))
        val select = Button(this).apply { text = "FOTOĞRAF SEÇ"; setOnClickListener { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, pickPhoto) } }
        root.addView(select)
        root.addView(Button(this).apply { text = "ÖNİZLEMEYİ GÜNCELLE"; setOnClickListener { refreshPreview() } })
        root.addView(Button(this).apply { text = "YAZDIR / PDF KAYDET"; setOnClickListener { printPhoto() } })
        val refreshListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refreshPreview() }
        }
        paper.onItemSelectedListener = refreshListener
        fitting.onItemSelectedListener = refreshListener
        mode.onItemSelectedListener = refreshListener
        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickPhoto && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts)
                val sample = max(1, max(opts.outWidth, opts.outHeight) / 3200)
                photo = BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample })
                refreshPreview()
            } catch (e: Exception) { Toast.makeText(this,"Fotoğraf açılamadı: ${e.message}",Toast.LENGTH_LONG).show() }
        }
    }

    private fun drawPhoto(canvas: Canvas, width: Float, height: Float) {
        val bitmap = photo ?: return
        val scale = if (fitting.selectedItemPosition == 0) min(width/bitmap.width, height/bitmap.height) else max(width/bitmap.width, height/bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (mode.selectedItemPosition == 1) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        canvas.drawBitmap(bitmap, null, RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint)
    }

    private fun refreshPreview() {
        if (photo == null) return
        val w = 540; val h = if (paper.selectedItemPosition == 1) 810 else 764
        val bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE); drawPhoto(canvas,w.toFloat(),h.toFloat())
        preview.setImageDrawable(null)
        preview.setImageBitmap(bitmap)
        preview.invalidate()
    }

    private fun printPhoto() {
        if (photo == null) { Toast.makeText(this,"Önce fotoğraf seçin",Toast.LENGTH_SHORT).show(); return }
        val isPhoto = paper.selectedItemPosition == 1
        val media = when(paper.selectedItemPosition) { 1 -> PrintAttributes.MediaSize("PHOTO_4X6", "10x15 cm", 4000,6000); 2 -> PrintAttributes.MediaSize.ISO_A5; else -> PrintAttributes.MediaSize.ISO_A4 }
        val attrs = PrintAttributes.Builder().setMediaSize(media).setColorMode(if (mode.selectedItemPosition == 1) PrintAttributes.COLOR_MODE_MONOCHROME else PrintAttributes.COLOR_MODE_COLOR).build()
        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Orhan Fotoğraf", object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: android.os.CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                callback.onLayoutFinished(PrintDocumentInfo.Builder("orhan-fotograf.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(), true)
            }
            override fun onWrite(pages: Array<android.print.PageRange>, destination: ParcelFileDescriptor, cancellationSignal: android.os.CancellationSignal, callback: WriteResultCallback) {
                try {
                    val doc = PdfDocument()
                    val pageW = if(isPhoto) 1200 else if(paper.selectedItemPosition==2) 1240 else 1754
                    val pageH = if(isPhoto) 1800 else if(paper.selectedItemPosition==2) 1754 else 2480
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW,pageH,1).create())
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    drawPhoto(page.canvas,pageW.toFloat(),pageH.toFloat())
                    doc.finishPage(page)
                    FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                    doc.close()
                    callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) { callback.onWriteFailed(e.message) }
            }
        },attrs)
    }
}
