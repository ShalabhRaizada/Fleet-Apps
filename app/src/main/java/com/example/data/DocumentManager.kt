package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object DocumentManager {
    
    /**
     * Save raw image bytes using AES-GCM encryption.
     */
    fun saveEncryptedImage(context: Context, imageBytes: ByteArray, filename: String): String {
        val encryptedBytes = CryptEngine.encrypt(context, imageBytes)
        val directory = File(context.filesDir, "secured_docs")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, filename)
        FileOutputStream(file).use { it.write(encryptedBytes) }
        return file.absolutePath
    }

    /**
     * Decrypt and load a Bitmap locally in RAM.
     */
    fun getDecryptedBitmap(context: Context, filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val encryptedBytes = file.readBytes()
            val decryptedBytes = CryptEngine.decrypt(context, encryptedBytes)
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a document and its extracted fields to a native PDF.
     * Draws the image on the PDF layout, and overlays the OCR parsed values.
     */
    fun convertToSearchablePdf(context: Context, document: Document, fields: List<ExtractedField>): File? {
        val bitmap = getDecryptedBitmap(context, document.localFilePath) ?: return null
        
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        
        val canvas = page.canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        
        // Draw OCR Text overlays. To make the PDF searchable/interactive, we draw text over the doc canvas
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLUE
            textSize = (bitmap.height / 45f).coerceAtLeast(16f)
            alpha = 180
            isUnderlineText = true
        }
        
        var yOffset = bitmap.height / 15f
        canvas.drawText("SEARCHABLE OCR LAYER — SECURED BY DOCDRIVER E2EE", bitmap.width / 20f, yOffset, titlePaint)
        yOffset += titlePaint.textSize * 2.5f
        
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = (bitmap.height / 55f).coerceAtLeast(14f)
        }
        
        fields.forEach { field ->
            canvas.drawText("${field.fieldKey}: ${field.fieldValue} (Accuracy: ${(field.confidence * 100).toInt()}%)", bitmap.width / 20f, yOffset, textPaint)
            yOffset += textPaint.textSize * 1.5f
        }
        
        pdfDocument.finishPage(page)
        
        val directory = File(context.filesDir, "generated_pdfs")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val pdfFile = File(directory, "DocDriver_${document.id}.pdf")
        return try {
            FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }
}
