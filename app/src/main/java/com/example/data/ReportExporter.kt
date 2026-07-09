package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ReportExporter {
    private const val TAG = "ReportExporter"

    // A4 Landscape dimensions in points (72 points/inch)
    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 40f

    /**
     * Splits a string into multiple lines to fit within a maximum width.
     */
    private fun splitTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val testWidth = paint.measureText(testLine)
            if (testWidth <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Single word is too long, split it character by character if needed
                    lines.add(word)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * Automatically generates a professional multi-page Landscape A4 PDF report in the app cache folder.
     */
    fun generatePdfReport(
        context: Context,
        doctorName: String,
        month: String,
        year: String,
        records: List<PatientRecord>
    ): File? {
        try {
            val pdfDocument = PdfDocument()

            // Paint definitions
            val titlePaint = Paint().apply {
                color = 0xFF0056D2.toInt() // Sapphire Blue
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = 0xFF475569.toInt() // Slate Gray
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = 0xFF0F172A.toInt() // Dark Navy
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val headerTextPaint = Paint().apply {
                color = 0xFFFFFFFF.toInt() // White
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyTextPaint = Paint().apply {
                color = 0xFF1E293B.toInt() // Slate 800
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val totalTextPaint = Paint().apply {
                color = 0xFF0F172A.toInt() // Slate 900
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val borderPaint = Paint().apply {
                color = 0xFF0F172A.toInt() // Dark border
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }

            val fillHeaderPaint = Paint().apply {
                color = 0xFF0056D2.toInt() // High Density blue background
                style = Paint.Style.FILL
            }

            val fillRowAltPaint = Paint().apply {
                color = 0xFFF8FAFC.toInt() // Alternating row
                style = Paint.Style.FILL
            }

            val fillTotalPaint = Paint().apply {
                color = 0xFFF1F5F9.toInt() // Totals block
                style = Paint.Style.FILL
            }

            // Table geometry (Total width available = 842 - 80 = 762)
            val columns = listOf(
                ColumnDef("DATE", 80f),
                ColumnDef("PATIENT NAME", 180f),
                ColumnDef("AGE", 50f),
                ColumnDef("TEST NAME", 252f),
                ColumnDef("COMMISSION", 100f),
                ColumnDef("OTHER", 100f)
            )

            // Dynamic pagination parameters
            val pageHeight = PAGE_HEIGHT
            val topMargin = MARGIN
            val bottomMargin = MARGIN
            val rowHeight = 22f

            // Calculate totals
            val totalCommission = records.sumOf { it.commission ?: 0.0 }
            val totalOther = records.mapNotNull { it.other?.toDoubleOrNull() }.sum()
            val grandTotal = totalCommission + totalOther

            // Group records into pages
            // Page 1 header takes about 80 points, table header takes 22 points
            val p1MaxHeight = pageHeight - topMargin - bottomMargin - 90f - 22f
            val otherMaxHeight = pageHeight - topMargin - bottomMargin - 22f
            
            val p1MaxRows = (p1MaxHeight / rowHeight).toInt().coerceAtLeast(1)
            val otherMaxRows = (otherMaxHeight / rowHeight).toInt().coerceAtLeast(1)

            val pages = mutableListOf<List<PatientRecord>>()
            var remainingRecords = records
            
            if (remainingRecords.isEmpty()) {
                pages.add(emptyList())
            } else {
                var isFirstPage = true
                while (remainingRecords.isNotEmpty()) {
                    val limit = if (isFirstPage) p1MaxRows else otherMaxRows
                    val chunk = remainingRecords.take(limit)
                    pages.add(chunk)
                    remainingRecords = remainingRecords.drop(limit)
                    isFirstPage = false
                }
            }

            val totalPagesCount = pages.size

            // Draw each page
            for (pageIndex in 0 until totalPagesCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var currentY = topMargin

                if (pageIndex == 0) {
                    // Draw Main clinical header
                    canvas.drawText("CLINICAL PATHOLOGY LABORATORY DOCTOR REGISTER", MARGIN, currentY + 15f, titlePaint)
                    canvas.drawText("MONTHLY COMMISSION RECONCILIATION SHEET", MARGIN, currentY + 30f, subtitlePaint)
                    
                    currentY += 45f
                    
                    // Meta Row
                    canvas.drawText("DOCTOR: ${doctorName.uppercase()}", MARGIN, currentY + 10f, metaPaint)
                    val periodText = "PERIOD: $month / $year"
                    val periodWidth = metaPaint.measureText(periodText)
                    canvas.drawText(periodText, PAGE_WIDTH - MARGIN - periodWidth, currentY + 10f, metaPaint)
                    
                    currentY += 25f
                    
                    // Thick Divider
                    val oldStroke = borderPaint.strokeWidth
                    borderPaint.strokeWidth = 2f
                    canvas.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, borderPaint)
                    borderPaint.strokeWidth = oldStroke
                    
                    currentY += 15f
                }

                // Draw Table Header
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f, fillHeaderPaint)
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f, borderPaint)

                var currentX = MARGIN
                for (col in columns) {
                    canvas.drawText(col.name, currentX + 6f, currentY + 14f, headerTextPaint)
                    canvas.drawLine(currentX, currentY, currentX, currentY + 22f, borderPaint)
                    currentX += col.width
                }
                canvas.drawLine(PAGE_WIDTH - MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f, borderPaint)

                currentY += 22f

                // Draw Table Rows
                val pageRecords = pages[pageIndex]
                for (recordIndex in pageRecords.indices) {
                    val record = pageRecords[recordIndex]

                    // Alternating background
                    if (recordIndex % 2 == 1) {
                        canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, fillRowAltPaint)
                    }

                    // Prepare cell texts
                    val dateText = record.date
                    val nameText = record.patientName + " (" + record.age + ")"
                    val ageText = record.age
                    val testText = record.testName
                    val commText = record.commission?.let { "₹${String.format("%.2f", it)}" } ?: "-"
                    val otherText = record.other?.takeIf { it.isNotEmpty() } ?: "-"

                    // Draw content with cell wrapping support
                    currentX = MARGIN
                    val cellValues = listOf(dateText, nameText, ageText, testText, commText, otherText)
                    
                    for (cIdx in columns.indices) {
                        val col = columns[cIdx]
                        val rawText = cellValues[cIdx]
                        
                        // Wrap text to fit column width
                        val lines = splitTextToLines(rawText, bodyTextPaint, col.width - 12f)
                        var textY = currentY + 14f
                        
                        // We draw only up to 2 wrapped lines to prevent overflow, but our row size is fine
                        for (lIdx in 0 until minOf(2, lines.size)) {
                            canvas.drawText(lines[lIdx], currentX + 6f, textY, bodyTextPaint)
                            textY += 9f
                        }

                        // Vertical cell border
                        canvas.drawLine(currentX, currentY, currentX, currentY + rowHeight, borderPaint)
                        currentX += col.width
                    }
                    canvas.drawLine(PAGE_WIDTH - MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, borderPaint)
                    canvas.drawLine(MARGIN, currentY + rowHeight, PAGE_WIDTH - MARGIN, currentY + rowHeight, borderPaint)

                    currentY += rowHeight
                }

                // If it is the last page, draw Totals block and Signatures
                if (pageIndex == totalPagesCount - 1) {
                    // Totals Row
                    canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 24f, fillTotalPaint)
                    canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 24f, borderPaint)

                    // Columns width offset to the right
                    // Totals label spans the first 4 columns (80 + 180 + 50 + 252 = 562)
                    canvas.drawText("TOTALS", MARGIN + 12f, currentY + 16f, totalTextPaint)
                    canvas.drawLine(MARGIN, currentY, MARGIN, currentY + 24f, borderPaint)
                    
                    // Vertical dividing line before Commission
                    canvas.drawLine(MARGIN + 562f, currentY, MARGIN + 562f, currentY + 24f, borderPaint)
                    
                    // Commission Total
                    canvas.drawText("₹${String.format("%.2f", totalCommission)}", MARGIN + 562f + 6f, currentY + 16f, totalTextPaint)
                    canvas.drawLine(MARGIN + 562f + 100f, currentY, MARGIN + 562f + 100f, currentY + 24f, borderPaint)
                    
                    // Other Total
                    canvas.drawText("₹${String.format("%.2f", totalOther)}", MARGIN + 562f + 100f + 6f, currentY + 16f, totalTextPaint)
                    canvas.drawLine(PAGE_WIDTH - MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 24f, borderPaint)

                    currentY += 34f

                    // Grand Summary Box (Right Aligned)
                    val boxWidth = 320f
                    val boxHeight = 55f
                    val boxLeft = PAGE_WIDTH - MARGIN - boxWidth
                    
                    canvas.drawRect(boxLeft, currentY, PAGE_WIDTH - MARGIN, currentY + boxHeight, fillRowAltPaint)
                    canvas.drawRect(boxLeft, currentY, PAGE_WIDTH - MARGIN, currentY + boxHeight, borderPaint)

                    canvas.drawText("Total Referred Patients:", boxLeft + 12f, currentY + 18f, bodyTextPaint)
                    canvas.drawText("${records.size}", PAGE_WIDTH - MARGIN - 30f, currentY + 18f, totalTextPaint)

                    canvas.drawText("Grand Total Payout:", boxLeft + 12f, currentY + 40f, totalTextPaint)
                    val payoutText = "₹${String.format("%.2f", grandTotal)}"
                    val payoutWidth = totalTextPaint.measureText(payoutText)
                    canvas.drawText(payoutText, PAGE_WIDTH - MARGIN - 12f - payoutWidth, currentY + 40f, totalTextPaint.apply { color = 0xFF0056D2.toInt() })
                    totalTextPaint.color = 0xFF0F172A.toInt() // restore color

                    currentY += boxHeight + 40f

                    // Signatures line (fits bottom)
                    if (currentY + 30f < PAGE_HEIGHT - bottomMargin) {
                        canvas.drawLine(MARGIN, currentY, MARGIN + 180f, currentY, borderPaint)
                        canvas.drawText("Prepared By (Lab Executive)", MARGIN, currentY + 12f, bodyTextPaint)

                        val sig2Left = PAGE_WIDTH - MARGIN - 180f
                        canvas.drawLine(sig2Left, currentY, PAGE_WIDTH - MARGIN, currentY, borderPaint)
                        canvas.drawText("Verified By (Authorized Pathologist)", sig2Left, currentY + 12f, bodyTextPaint)
                    }
                }

                // Draw Footer with Page Number
                val footerText = "Page ${pageIndex + 1} of $totalPagesCount"
                val footerWidth = bodyTextPaint.measureText(footerText)
                canvas.drawText(footerText, (PAGE_WIDTH - footerWidth) / 2f, PAGE_HEIGHT - bottomMargin + 15f, bodyTextPaint)

                pdfDocument.finishPage(page)
            }

            // Save PDF to App Cache directory
            val cleanedDoctorName = doctorName.replace("\\s+".toRegex(), "_")
            val fileName = "Doctor_Report_${cleanedDoctorName}_${month}_${year}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            Log.d(TAG, "Successfully generated clinical PDF at: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF report", e)
            return null
        }
    }

    /**
     * Automatically generates a professional CSV/Excel compatible sheet in the app cache folder.
     */
    fun generateExcelReport(
        context: Context,
        doctorName: String,
        month: String,
        year: String,
        records: List<PatientRecord>
    ): File? {
        try {
            val csvBuilder = StringBuilder()

            // Metadata headers
            csvBuilder.append("CLINICAL PATHOLOGY LABORATORY DOCTOR REGISTER\n")
            csvBuilder.append("MONTHLY COMMISSION RECONCILIATION SHEET\n")
            csvBuilder.append("DOCTOR,${escapeCsv(doctorName.uppercase())}\n")
            csvBuilder.append("PERIOD,${escapeCsv("$month / $year")}\n")
            csvBuilder.append("\n")

            // Table Header
            csvBuilder.append("DATE,PATIENT NAME,AGE,TEST NAME,COMMISSION,OTHER\n")

            // Table Rows
            for (record in records) {
                val dateStr = record.date
                val nameStr = record.patientName
                val ageStr = record.age
                val testStr = record.testName
                val commissionStr = record.commission?.let { String.format("%.2f", it) } ?: "0.00"
                val otherStr = record.other?.takeIf { it.isNotEmpty() } ?: "-"

                csvBuilder.append("${escapeCsv(dateStr)},${escapeCsv(nameStr)},${escapeCsv(ageStr)},${escapeCsv(testStr)},$commissionStr,${escapeCsv(otherStr)}\n")
            }

            // Calculations
            val totalCommission = records.sumOf { it.commission ?: 0.0 }
            val totalOther = records.mapNotNull { it.other?.toDoubleOrNull() }.sum()
            val grandTotal = totalCommission + totalOther

            csvBuilder.append("\n")
            csvBuilder.append("TOTALS,,,,${String.format("%.2f", totalCommission)},${String.format("%.2f", totalOther)}\n")
            csvBuilder.append("GRAND TOTAL PAYOUT,,,,${String.format("%.2f", grandTotal)},\n")
            csvBuilder.append("TOTAL REFERRED PATIENTS,${records.size},,,,\n")

            // Write to file
            val cleanedDoctorName = doctorName.replace("\\s+".toRegex(), "_")
            val fileName = "Doctor_Report_${cleanedDoctorName}_${month}_${year}.csv"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
            }

            Log.d(TAG, "Successfully generated Excel-CSV at: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error generating CSV report", e)
            return null
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    /**
     * Copy generated file to public Downloads folder for instant native download experience.
     */
    fun downloadFileToDownloadsFolder(context: Context, file: File, mimeType: String): Boolean {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destinationFile = File(downloadsDir, file.name)
            file.copyTo(destinationFile, overwrite = true)
            
            // Trigger Media Scanner to notify downloads app immediately
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val contentUri = Uri.fromFile(destinationFile)
            mediaScanIntent.data = contentUri
            context.sendBroadcast(mediaScanIntent)

            Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to public Downloads", e)
            Toast.makeText(context, "Saved internally. Error downloading to public folder.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Shares a file to WhatsApp or fallback share sheet.
     */
    fun shareToWhatsApp(context: Context, file: File, mimeType: String) {
        try {
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
            }

            try {
                context.startActivity(Intent.createChooser(shareIntent, "Share with WhatsApp"))
            } catch (e: Exception) {
                // WhatsApp is not installed, fallback to normal share sheet
                Log.w(TAG, "WhatsApp is not installed. Using standard share sheet.", e)
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Share Report"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing file", e)
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Prints a document using the Android print framework.
     */
    fun printDocument(context: Context, file: File, jobName: String) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = PDFPrintDocumentAdapter(file)
            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Printing failed", e)
            Toast.makeText(context, "Printing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    data class ColumnDef(val name: String, val width: Float)
}
