package com.mrjdm.scribe

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** Extracts text from every PDF page on-device so imported scans are searchable offline. */
internal fun indexPdfText(file: File): String {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(descriptor)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        buildString {
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                val bitmap = try {
                    val width = page.width.coerceAtMost(1500)
                    val height = (page.height * width.toFloat() / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally { page.close() }
                val text = try {
                    Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
                } finally { bitmap.recycle() }
                append("Page ${index + 1}\n")
                append(text)
                append("\n\n")
            }
        }.trim()
    } finally {
        recognizer.close()
        renderer.close()
        descriptor.close()
    }
}
