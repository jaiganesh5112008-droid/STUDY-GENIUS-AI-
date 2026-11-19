package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.max

sealed interface PreparedMaterial {
    val displayName: String
    val mimeType: String
    val sizeBytes: Long
    val previewSummary: String

    data class Pdf(
        override val displayName: String,
        override val sizeBytes: Long,
        val bytes: ByteArray
    ) : PreparedMaterial {
        override val mimeType: String = "application/pdf"
        override val previewSummary: String = "PDF document (${formatFileSize(sizeBytes)})"
    }

    data class Text(
        override val displayName: String,
        override val mimeType: String,
        override val sizeBytes: Long,
        val extractedText: String
    ) : PreparedMaterial {
        override val previewSummary: String =
            extractedText.trim().take(160).replace("\n", " ")
    }

    data class Image(
        override val displayName: String,
        override val sizeBytes: Long,
        val bitmap: Bitmap
    ) : PreparedMaterial {
        override val mimeType: String = "image/jpeg"
        override val previewSummary: String =
            "Image ${bitmap.width}x${bitmap.height} (${formatFileSize(sizeBytes)})"
    }
}

sealed interface MaterialReadResult {
    data class Success(val material: PreparedMaterial) : MaterialReadResult
    data class Failure(val errorMessage: String) : MaterialReadResult
}

object DocumentProcessor {
    private const val MAX_DOCUMENT_BYTES = 15L * 1024L * 1024L // 15 MB (< 20 MB Gemini inline limit)
    private const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L // 10 MB
    private const val MAX_EXTRACTED_CHARS = 90_000
    private const val MAX_IMAGE_DIMENSION = 1600

    val SUPPORTED_DOCUMENT_MIMES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "image/jpeg",
        "image/png",
        "image/webp"
    )

    fun createCameraCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "study_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun processUri(context: Context, uri: Uri): MaterialReadResult =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val (fileName, reportedSize) = queryMetadata(context, uri)
                val mime = resolver.getType(uri)?.lowercase().orEmpty()
                val lowerName = fileName.lowercase()

                val isPdf = mime == "application/pdf" || lowerName.endsWith(".pdf")
                val isDocx =
                    mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        lowerName.endsWith(".docx")
                val isTxt = mime == "text/plain" || lowerName.endsWith(".txt") || lowerName.endsWith(".md")
                val isImage = mime.startsWith("image/") ||
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".webp")

                if (!isPdf && !isDocx && !isTxt && !isImage) {
                    return@withContext MaterialReadResult.Failure(
                        "Unsupported file type ($fileName). Please select a PDF, DOCX, TXT, or image file."
                    )
                }

                val maxAllowed = if (isImage) MAX_IMAGE_BYTES else MAX_DOCUMENT_BYTES
                if (reportedSize > maxAllowed) {
                    return@withContext MaterialReadResult.Failure(
                        "File is too large (${formatFileSize(reportedSize)}). Maximum allowed size is ${formatFileSize(maxAllowed)}."
                    )
                }

                when {
                    isPdf -> readPdf(context, uri, fileName)
                    isDocx -> readDocx(context, uri, fileName)
                    isTxt -> readText(context, uri, fileName)
                    isImage -> readImage(context, uri, fileName)
                    else -> MaterialReadResult.Failure("Unsupported file format.")
                }
            } catch (e: SecurityException) {
                MaterialReadResult.Failure("Permission denied while reading the selected file. Please pick the file again.")
            } catch (e: Exception) {
                MaterialReadResult.Failure("Could not read file: ${e.localizedMessage ?: "Unreadable or corrupted file."}")
            }
        }

    private fun readPdf(context: Context, uri: Uri, fileName: String): MaterialReadResult {
        val bytes = readBoundedBytes(context, uri, MAX_DOCUMENT_BYTES)
            ?: return MaterialReadResult.Failure("PDF exceeds the 15 MB size limit or could not be opened.")

        if (bytes.size < 8) {
            return MaterialReadResult.Failure("The selected PDF file appears to be empty or corrupted.")
        }
        val header = String(bytes.copyOfRange(0, minOf(bytes.size, 8)), Charsets.US_ASCII)
        if (!header.contains("%PDF")) {
            return MaterialReadResult.Failure("Invalid PDF file format. Please choose a valid, readable PDF document.")
        }
        return MaterialReadResult.Success(
            PreparedMaterial.Pdf(
                displayName = fileName,
                sizeBytes = bytes.size.toLong(),
                bytes = bytes
            )
        )
    }

    private fun readDocx(context: Context, uri: Uri, fileName: String): MaterialReadResult {
        val bytes = readBoundedBytes(context, uri, MAX_DOCUMENT_BYTES)
            ?: return MaterialReadResult.Failure("DOCX exceeds the 15 MB size limit or could not be opened.")

        val extracted = extractDocxXmlText(bytes)
        if (extracted.isBlank() || extracted.length < 20) {
            return MaterialReadResult.Failure(
                "No readable text was found inside this DOCX file. Make sure it is not encrypted or image-only."
            )
        }
        return MaterialReadResult.Success(
            PreparedMaterial.Text(
                displayName = fileName,
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                sizeBytes = bytes.size.toLong(),
                extractedText = extracted.take(MAX_EXTRACTED_CHARS)
            )
        )
    }

    private fun extractDocxXmlText(docxBytes: ByteArray): String {
        val builder = StringBuilder()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlBytes = zip.readBytes()
                    val parser = Xml.newPullParser()
                    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.TEXT -> {
                                val text = parser.text
                                if (!text.isNullOrEmpty()) {
                                    builder.append(text)
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                val tag = parser.name
                                if (tag == "p" || tag == "br" || tag == "tr") {
                                    builder.append('\n')
                                } else if (tag == "tc") {
                                    builder.append(' ')
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return builder.toString()
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun readText(context: Context, uri: Uri, fileName: String): MaterialReadResult {
        val bytes = readBoundedBytes(context, uri, MAX_DOCUMENT_BYTES)
            ?: return MaterialReadResult.Failure("Text file exceeds the 15 MB limit or could not be opened.")
        val text = String(bytes, Charsets.UTF_8).trim()
        if (text.length < 15) {
            return MaterialReadResult.Failure("The selected text file is too short or empty to analyze.")
        }
        return MaterialReadResult.Success(
            PreparedMaterial.Text(
                displayName = fileName,
                mimeType = "text/plain",
                sizeBytes = bytes.size.toLong(),
                extractedText = text.take(MAX_EXTRACTED_CHARS)
            )
        )
    }

    private fun readImage(context: Context, uri: Uri, fileName: String): MaterialReadResult {
        val rawBytes = readBoundedBytes(context, uri, MAX_IMAGE_BYTES)
            ?: return MaterialReadResult.Failure("Image exceeds the 10 MB size limit or could not be opened.")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return MaterialReadResult.Failure("Could not decode the selected image. Please try another photo.")
        }

        val largest = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (largest / sampleSize > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return MaterialReadResult.Failure("Failed to process image bitmap.")

        return MaterialReadResult.Success(
            PreparedMaterial.Image(
                displayName = fileName,
                sizeBytes = rawBytes.size.toLong(),
                bitmap = bitmap
            )
        )
    }

    private fun readBoundedBytes(context: Context, uri: Uri, maxBytes: Long): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun queryMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "Selected File"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    name = cursor.getString(nameIdx) ?: name
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
