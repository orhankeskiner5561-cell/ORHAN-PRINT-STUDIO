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
import android.provider.Settings
import android.net.wifi.WifiManager
import android.text.TextUtils
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.graphics.Color
import android.graphics.Typeface
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
    private var zoom=1f
    private var dx=0f
    private var dy=0f
    private var lastX=0f
    private var lastY=0f
    private var caption=""
    private var sticker=""
    private lateinit var copies: EditText
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(248,248,250)
        window.navigationBarColor=Color.rgb(248,248,250)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        scaleDetector=ScaleGestureDetector(this,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(detector:ScaleGestureDetector):Boolean {
                zoom=(zoom*detector.scaleFactor).coerceIn(0.25f,5f)
                refreshPreview()
                return true
            }
        })
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val title = TextView(this).apply { text = "ORHAN PRINT STUDIO"; textSize = 24f; gravity = Gravity.CENTER; setPadding(0,20,0,20) }
        root.addView(title)
        preview = ImageView(this).apply {
            setBackgroundColor(0xffeeeeee.toInt())
            scaleType=ImageView.ScaleType.FIT_CENTER
            setOnTouchListener { _, event ->
                if(fitting.selectedItemPosition!=2) return@setOnTouchListener false
                scaleDetector.onTouchEvent(event)
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {lastX=event.x;lastY=event.y;true}
                    MotionEvent.ACTION_MOVE -> {
                        if(event.pointerCount==1 && !scaleDetector.isInProgress){
                            dx+=(event.x-lastX)/width.coerceAtLeast(1)
                            dy+=(event.y-lastY)/height.coerceAtLeast(1)
                            refreshPreview()
                        }
                        lastX=event.x;lastY=event.y;true
                    }
                    else -> true
                }
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, (resources.displayMetrics.widthPixels * 0.95f).toInt()))
        fun selector(items: List<String>): Spinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items); root.addView(this) }
        paper = selector(listOf("A4", "10 x 15 cm", "A5", "A3 (PDF)", "A6", "13 x 18 cm", "15 x 21 cm", "10 x 10 cm", "Letter"))
        fitting = selector(listOf("Tamamını sığdır (kırpma yok)", "Sayfayı doldur (kırpabilir)", "Serbest yerleştir"))
        mode = selector(listOf("Orijinal renkli", "Siyah-beyaz"))
        root.addView(TextView(this).apply{text="Baskı adedi";textSize=16f})
        copies=EditText(this).apply{inputType=2;setText("1");hint="Baskı sayısı (1-99)";setSingleLine(true)}
        root.addView(copies)
        val captionInput=EditText(this).apply{hint="Fotoğraf üzerine yazı";setSingleLine(true)}
        root.addView(captionInput)
        root.addView(Button(this).apply{text="YAZIYI EKLE";setOnClickListener{caption=captionInput.text.toString();refreshPreview()}})
        val stickerInput=EditText(this).apply{hint="Etiket (ör. ★)";setSingleLine(true)}
        root.addView(stickerInput)
        root.addView(Button(this).apply{text="ETİKETİ EKLE";setOnClickListener{sticker=stickerInput.text.toString();refreshPreview()}})
        root.addView(Button(this).apply{text="YERLEŞİMİ SIFIRLA";setOnClickListener{zoom=1f;dx=0f;dy=0f;refreshPreview()}})
        val select = Button(this).apply { text = "FOTOĞRAF SEÇ"; setOnClickListener { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, pickPhoto) } }
        root.addView(select)
        root.addView(Button(this).apply { text = "HP YAZICI / WI-FI BAĞLANTISI"; setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } })
        root.addView(Button(this).apply { text = "YAZICI SEÇ / YAZDIR"; setOnClickListener { printPhoto() } })
        root.addView(Button(this).apply { text = "ÖNİZLEMEYİ GÜNCELLE"; setOnClickListener { refreshPreview() } })

        val refreshListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refreshPreview() }
        }
        paper.onItemSelectedListener = refreshListener
        fitting.onItemSelectedListener = refreshListener
        mode.onItemSelectedListener = refreshListener
        scroll.addView(root)
        setContentView(scroll)
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
        val factor=scale*(if(fitting.selectedItemPosition==2)zoom else 1f)
        val w=bitmap.width*factor;val h=bitmap.height*factor
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (mode.selectedItemPosition == 1) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        canvas.save()
        canvas.clipRect(0f,0f,width,height)
        val x=if(fitting.selectedItemPosition==2)dx*width else 0f
        val y=if(fitting.selectedItemPosition==2)dy*height else 0f
        canvas.drawBitmap(bitmap,null,RectF((width-w)/2+x,(height-h)/2+y,(width+w)/2+x,(height+h)/2+y),paint)
        val tp=Paint(Paint.ANTI_ALIAS_FLAG).apply{
            color=Color.WHITE;textSize=width*0.05f;typeface=Typeface.DEFAULT_BOLD
            setShadowLayer(2f,1f,1f,Color.BLACK)
        }
        if(caption.isNotBlank())canvas.drawText(caption.take(60),width*0.05f,height*0.90f,tp)
        if(sticker.isNotBlank())canvas.drawText(sticker.take(12),width*0.75f,height*0.15f,tp)
        canvas.restore()
    }

    private fun refreshPreview() {
        if (photo == null) return
        val dims = dimensions(); val w = 540; val h = (540f * dims.second / dims.first).toInt()
        val bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE); drawPhoto(canvas,w.toFloat(),h.toFloat())
        preview.setImageDrawable(null)
        preview.setImageBitmap(bitmap)
        preview.invalidate()
    }

    private fun dimensions(): Pair<Int,Int> = when(paper.selectedItemPosition) {
        1 -> 100 to 150; 2 -> 148 to 210; 3 -> 297 to 420; 4 -> 105 to 148
        5 -> 130 to 180; 6 -> 150 to 210; 7 -> 100 to 100; 8 -> 216 to 279
        else -> 210 to 297
    }

    private fun printPhoto() {
        if (photo == null) { Toast.makeText(this,"Önce fotoğraf seçin",Toast.LENGTH_SHORT).show(); return }
        val quantity=copies.text.toString().toIntOrNull()?.coerceIn(1,99)?:1
        val dims = dimensions()
        val media = when(paper.selectedItemPosition) { 1 -> PrintAttributes.MediaSize("PHOTO_4X6", "10x15 cm", 4000,6000); 2 -> PrintAttributes.MediaSize.ISO_A5; 3 -> PrintAttributes.MediaSize.ISO_A3; 4 -> PrintAttributes.MediaSize.ISO_A6; 8 -> PrintAttributes.MediaSize.NA_LETTER; 5 -> PrintAttributes.MediaSize("PHOTO_5X7", "13x18 cm", 5100,7100); 6 -> PrintAttributes.MediaSize("PHOTO_6X8", "15x21 cm", 5900,8300); 7 -> PrintAttributes.MediaSize("PHOTO_SQUARE", "10x10 cm", 4000,4000); else -> PrintAttributes.MediaSize.ISO_A4 }
        val attrs = PrintAttributes.Builder().setMediaSize(media).setColorMode(if (mode.selectedItemPosition == 1) PrintAttributes.COLOR_MODE_MONOCHROME else PrintAttributes.COLOR_MODE_COLOR).build()
        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Orhan Fotoğraf ($quantity adet - yazıcıda seçin)", object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: android.os.CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                callback.onLayoutFinished(PrintDocumentInfo.Builder("orhan-fotograf.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(), true)
            }
            override fun onWrite(pages: Array<android.print.PageRange>, destination: ParcelFileDescriptor, cancellationSignal: android.os.CancellationSignal, callback: WriteResultCallback) {
                try {
                    val doc = PdfDocument()
                    val pageW = (dims.first * 72f / 25.4f).toInt()
                    val pageH = (dims.second * 72f / 25.4f).toInt()
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
