package com.simulagamer.filedesk

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun isZipFile(file: DocumentFile): Boolean {
    val name = file.name?.lowercase().orEmpty()
    return !file.isDirectory && (name.endsWith(".zip") ||
        file.type == "application/zip" ||
        file.type == "application/x-zip-compressed")
}

fun extractZip(
    context: Context,
    source: DocumentFile,
    destination: DocumentFile,
    createFolder: Boolean = true
): DocumentFile? {
    return runCatching {
        val zipName = (source.name ?: "Arquivo").substringBeforeLast(".zip", source.name ?: "Arquivo")
            .ifBlank { "Arquivo extraído" }
        val root = if (createFolder) {
            destination.createDirectory(uniqueZipChildName(destination, zipName)) ?: return@runCatching null
        } else destination

        openZipInput(context, source.uri).use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val parts = safeZipParts(entry.name)
                    if (parts.isNotEmpty()) {
                        var dir = root
                        val folderParts = if (entry.isDirectory) parts else parts.dropLast(1)
                        for (part in folderParts) {
                            dir = dir.findFile(part)?.takeIf { it.isDirectory }
                                ?: dir.createDirectory(part)
                                ?: error("Não foi possível criar a pasta $part")
                        }
                        if (!entry.isDirectory) {
                            val fileName = parts.last()
                            val target = dir.createFile(guessZipMime(fileName), uniqueZipChildName(dir, fileName))
                                ?: error("Não foi possível criar $fileName")
                            openZipOutput(context, target.uri).use { out -> zip.copyTo(out) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        root
    }.getOrNull()
}

fun createZip(
    context: Context,
    sources: List<DocumentFile>,
    destination: DocumentFile,
    desiredName: String = defaultZipName(sources)
): DocumentFile? {
    if (sources.isEmpty()) return null
    return runCatching {
        val cleanName = desiredName.trim().ifBlank { "Arquivo.zip" }.let {
            if (it.lowercase().endsWith(".zip")) it else "$it.zip"
        }
        val target = destination.createFile("application/zip", uniqueZipChildName(destination, cleanName))
            ?: return@runCatching null
        openZipOutput(context, target.uri).use { raw ->
            ZipOutputStream(raw).use { zip ->
                sources.forEach { source ->
                    addToZip(context, zip, source, source.name ?: "arquivo")
                }
            }
        }
        target
    }.getOrNull()
}

private fun addToZip(context: Context, zip: ZipOutputStream, source: DocumentFile, path: String) {
    val safePath = path.replace('\\', '/').trimStart('/')
    if (source.isDirectory) {
        val children = source.listFiles()
        if (children.isEmpty()) {
            zip.putNextEntry(ZipEntry("$safePath/"))
            zip.closeEntry()
        } else {
            children.forEach { child ->
                addToZip(context, zip, child, "$safePath/${child.name ?: "arquivo"}")
            }
        }
    } else {
        zip.putNextEntry(ZipEntry(safePath))
        openZipInput(context, source.uri).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}

private fun safeZipParts(name: String): List<String> {
    val normalized = name.replace('\\', '/').trimStart('/')
    val parts = normalized.split('/').filter { it.isNotBlank() }
    require(parts.none { it == "." || it == ".." }) { "Caminho inválido no ZIP" }
    return parts
}

private fun openZipInput(context: Context, uri: Uri): InputStream {
    return if (uri.scheme == "file") {
        FileInputStream(File(requireNotNull(uri.path)))
    } else {
        requireNotNull(context.contentResolver.openInputStream(uri))
    }
}

private fun openZipOutput(context: Context, uri: Uri): OutputStream {
    return if (uri.scheme == "file") {
        FileOutputStream(File(requireNotNull(uri.path)))
    } else {
        requireNotNull(context.contentResolver.openOutputStream(uri))
    }
}

private fun uniqueZipChildName(destination: DocumentFile, desired: String): String {
    if (destination.findFile(desired) == null) return desired
    val dot = desired.lastIndexOf('.')
    val base = if (dot > 0) desired.substring(0, dot) else desired
    val ext = if (dot > 0) desired.substring(dot) else ""
    var n = 2
    while (destination.findFile("$base ($n)$ext") != null) n++
    return "$base ($n)$ext"
}

private fun defaultZipName(sources: List<DocumentFile>): String {
    return if (sources.size == 1) {
        val base = sources.first().name?.substringBeforeLast('.')?.ifBlank { "Arquivo" } ?: "Arquivo"
        "$base.zip"
    } else "Arquivos.zip"
}

private fun guessZipMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "txt", "md", "log", "csv" -> "text/plain"
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}
