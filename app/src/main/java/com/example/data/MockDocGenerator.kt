package com.example.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream

object MockDocGenerator {

    enum class MockType {
        FUEL_RECEIPT,
        BILL_OF_LADING,
        DELIVERY_RECEIPT
    }

    /**
     * Generates a realistic high-contrast receipt/document image on a Bitmap and returns JPEG bytes.
     */
    fun generateMockDocumentBytes(type: MockType): ByteArray {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw Paper Background
        val bgPaint = Paint().apply {
            color = Color.rgb(248, 246, 240) // Crème off-white paper
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw Paper texture edge lines
        val marginPaint = Paint().apply {
            color = Color.rgb(220, 215, 205)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(10f, 10f, width - 10f, height - 10f, marginPaint)

        // Drawing Utilities
        val paintText = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }

        when (type) {
            MockType.FUEL_RECEIPT -> {
                // Header
                paintText.textSize = 28f
                paintText.isFakeBoldText = true
                var y = 60f
                canvas.drawText("PILOT TRAVEL CENTER #351", 50f, y, paintText)
                
                paintText.textSize = 20f
                paintText.isFakeBoldText = false
                y += 35f
                canvas.drawText("I-40 EXIT 162 | VALENCIA, NM", 50f, y, paintText)
                canvas.drawText("TEL: (505) 865-1022", 50f, y + 25f, paintText)
                
                // Divider
                y += 60f
                drawDashedLine(canvas, 50f, width - 50f, y, paintText)

                // Invoice Data
                y += 40f
                paintText.textSize = 18f
                canvas.drawText("DATE: 05/23/2026 08:14 AM", 50f, y, paintText)
                canvas.drawText("INVOICE NO: #PL-899121", 50f, y + 25f, paintText)
                canvas.drawText("VEHICLE ID: COV-TRK-78A", 50f, y + 50f, paintText)
                canvas.drawText("DRIVER: Shalabh Raizada", 50f, y + 75f, paintText)

                // Divider
                y += 110f
                drawDashedLine(canvas, 50f, width - 50f, y, paintText)

                // Items list
                y += 45f
                paintText.textSize = 20f
                paintText.isFakeBoldText = true
                canvas.drawText("DESCRIPTION", 50f, y, paintText)
                canvas.drawText("TOTAL", width - 150f, y, paintText)

                y += 35f
                paintText.isFakeBoldText = false
                paintText.textSize = 18f
                canvas.drawText("ULTRA LOW SULFUR DIESEL", 50f, y, paintText)
                y += 25f
                canvas.drawText("132.50 GAL @ \$3.959/GAL", 55f, y, paintText)
                canvas.drawText("\$524.57", width - 150f, y - 10f, paintText)

                y += 40f
                canvas.drawText("DEF FUEL (DISPENSER 4)", 50f, y, paintText)
                y += 25f
                canvas.drawText("5.00 GAL @ \$3.100/GAL", 55f, y, paintText)
                canvas.drawText("\$15.50", width - 150f, y - 10f, paintText)

                // Divider
                y += 50f
                drawDashedLine(canvas, 50f, width - 50f, y, paintText)

                // Totals
                y += 45f
                paintText.textSize = 22f
                paintText.isFakeBoldText = true
                canvas.drawText("TOTAL AMOUNT Paid", 50f, y, paintText)
                canvas.drawText("\$540.07", width - 150f, y, paintText)

                // Barcoding at bottom
                y += 80f
                val barPaint = Paint().apply {
                    color = Color.BLACK
                    strokeWidth = 4f
                }
                for (i in 0..40) {
                    val lineX = 150f + (i * 7)
                    val randHeight = if (i % 3 == 0) 60f else if (i % 2 == 0) 45f else 55f
                    barPaint.strokeWidth = if (i % 5 == 0) 6f else 2f
                    canvas.drawLine(lineX, y, lineX, y + randHeight, barPaint)
                }
                paintText.textSize = 14f
                paintText.isFakeBoldText = false
                canvas.drawText("* 899121952407 *", 240f, y + 80f, paintText)
            }

            MockType.BILL_OF_LADING -> {
                // Header Block
                paintText.textSize = 30f
                paintText.isFakeBoldText = true
                var y = 70f
                canvas.drawText("BILL OF LADING", 50f, y, paintText)
                
                paintText.textSize = 16f
                paintText.isFakeBoldText = false
                y += 30f
                canvas.drawText("STANDARD STRAIGHT BILL OF LADING — NON NEGOTIABLE", 50f, y, paintText)

                // Outlined Border for Ship details
                y += 30f
                val rect = RectF(40f, y, width - 40f, y + 160f)
                val strokePaint = Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawRect(rect, strokePaint)

                // SHIP TO/FROM details
                paintText.textSize = 14f
                canvas.drawLine(width / 2f, y, width / 2f, y + 160f, strokePaint)
                canvas.drawText("SHIP FROM (ORIGIN):", 52f, y + 20f, paintText)
                paintText.isFakeBoldText = true
                canvas.drawText("SWIFT PACKAGING CO.", 52f, y + 40f, paintText)
                paintText.isFakeBoldText = false
                canvas.drawText("100 Packaging Way, Atlanta, GA", 52f, y + 60f, paintText)

                canvas.drawText("SHIP TO (CONSIGNEE):", width / 2f + 12f, y + 20f, paintText)
                paintText.isFakeBoldText = true
                canvas.drawText("WALMART DISTRIBUTION #84", width / 2f + 12f, y + 40f, paintText)
                paintText.isFakeBoldText = false
                canvas.drawText("900 Walmart Highway, Bentonville, AR", width / 2f + 12f, y + 60f, paintText)

                // Line separator inside box
                canvas.drawLine(40f, y + 80f, width - 40f, y + 80f, strokePaint)
                canvas.drawText("CARRIER: Swift Transportation Co.", 52f, y + 110f, paintText)
                canvas.drawText("TRAILER #: TR-99401", 52f, y + 130f, paintText)
                canvas.drawText("SEAL #: SE-11928391", width / 2f + 12f, y + 110f, paintText)
                canvas.drawText("BOL NO: BOL-551239", width / 2f + 12f, y + 130f, paintText)

                // Freight Items Table
                y += 200f
                val tableRect = RectF(40f, y, width - 40f, y + 160f)
                canvas.drawRect(tableRect, strokePaint)
                canvas.drawLine(40f, y + 30f, width - 40f, y + 30f, strokePaint)

                // Table Headers
                paintText.isFakeBoldText = true
                paintText.textSize = 14f
                canvas.drawText("QTY", 50f, y + 20f, paintText)
                canvas.drawText("DESCRIPTION", 130f, y + 20f, paintText)
                canvas.drawText("WEIGHT (LBS)", width - 150f, y + 20f, paintText)

                // Column dividers
                canvas.drawLine(100f, y, 100f, y + 160f, strokePaint)
                canvas.drawLine(width - 180f, y, width - 180f, y + 160f, strokePaint)

                // Row Content
                paintText.isFakeBoldText = false
                canvas.drawText("22 PLT", 50f, y + 55f, paintText)
                canvas.drawText("Recycled Kraft Cardboard Shells", 115f, y + 55f, paintText)
                canvas.drawText("42,500 lbs", width - 150f, y + 55f, paintText)

                canvas.drawText("1 PLT", 50f, y + 85f, paintText)
                canvas.drawText("Warehouse Spacers / Dunnage", 115f, y + 85f, paintText)
                canvas.drawText("450 lbs", width - 150f, y + 85f, paintText)

                // Line 3 total
                canvas.drawLine(40f, y + 110f, width - 40f, y + 110f, strokePaint)
                paintText.isFakeBoldText = true
                canvas.drawText("TOTAL", 115f, y + 135f, paintText)
                canvas.drawText("42,950 lbs", width - 150f, y + 135f, paintText)

                // Signatures
                y += 210f
                canvas.drawLine(50f, y + 40f, 220f, y + 40f, strokePaint)
                canvas.drawText("Shipper Signature", 50f, y + 55f, paintText)
                
                canvas.drawLine(width - 220f, y + 40f, width - 50f, y + 40f, strokePaint)
                canvas.drawText("Driver Signature", width - 220f, y + 55f, paintText)

                // Simple simulated signature scribbles
                val sigPaint = Paint().apply {
                    color = Color.rgb(20, 50, 200) // Blue ink signature
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawArc(RectF(60f, y + 10f, 180f, y + 35f), 10f, 150f, false, sigPaint)
                canvas.drawArc(RectF(width - 200f, y + 10f, width - 80f, y + 35f), 30f, 120f, false, sigPaint)
            }

            MockType.DELIVERY_RECEIPT -> {
                // Header
                paintText.textSize = 28f
                paintText.isFakeBoldText = true
                var y = 60f
                canvas.drawText("FEDEX FREIGHT DELIVERY", 50f, y, paintText)
                
                paintText.textSize = 18f
                paintText.isFakeBoldText = false
                y += 35f
                canvas.drawText("CONSIGNEE RECEIPT & DELIVERY LOG", 50f, y, paintText)

                // Divider
                y += 45f
                drawDashedLine(canvas, 50f, width - 50f, y, paintText)

                y += 35f
                canvas.drawText("DELIVERY ID: #FDX-77610A", 50f, y, paintText)
                canvas.drawText("DATE: 05/23/2026 12:44 PM", 50f, y + 25f, paintText)
                canvas.drawText("DRIVER ID: DRV-7719", 50f, y + 50f, paintText)
                canvas.drawText("RECIPIENT: Costco Wholesale #29", 50f, y + 75f, paintText)

                // Table item info
                y += 135f
                drawDashedLine(canvas, 50f, width - 50f, y, paintText)

                y += 40f
                paintText.isFakeBoldText = true
                canvas.drawText("ITEMS SHIPPED & DELIVERED", 50f, y, paintText)
                
                y += 30f
                paintText.isFakeBoldText = false
                canvas.drawText("14 Boxes - Electronics / LCD Displays", 50f, y, paintText)
                canvas.drawText("Status: DELIVERED / SIGNED", 50f, y + 25f, paintText)

                y += 75f
                drawDashedLine(canvas, 50f, width - 50f, y, paintText)

                // Large sign Box
                y += 30f
                canvas.drawText("SIGNATURE RECIPIENT:", 50f, y, paintText)
                val signBox = RectF(50f, y + 15f, width - 50f, y + 115f)
                val dPaint = Paint().apply {
                    color = Color.DKGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRect(signBox, dPaint)

                // Recipient blue ink sign
                val recPaint = Paint().apply {
                    color = Color.rgb(20, 100, 200)
                    strokeWidth = 4f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawArc(RectF(100f, y + 35f, 250f, y + 95f), 0f, 180f, false, recPaint)
                canvas.drawArc(RectF(180f, y + 25f, 320f, y + 85f), 45f, 90f, false, recPaint)
            }
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }

    private fun drawDashedLine(canvas: Canvas, startX: Float, endX: Float, y: Float, paint: Paint) {
        val origColor = paint.color
        val origWidth = paint.strokeWidth
        paint.color = Color.BLACK
        paint.strokeWidth = 3f
        
        var currentX = startX
        val dashWidth = 10f
        val gapWidth = 8f
        
        while (currentX < endX) {
            canvas.drawLine(currentX, y, currentX + dashWidth, y, paint)
            currentX += dashWidth + gapWidth
        }
        
        paint.color = origColor
        paint.strokeWidth = origWidth
    }
}
