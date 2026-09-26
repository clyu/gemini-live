package io.github.clyu.geminilive.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.readText
import androidx.core.util.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Keeps the conversation transcript in app storage so it survives the app being closed. The file
 * lives in noBackupFilesDir, which is never included in cloud backups or device transfers.
 */
class TranscriptRepository(context: Context) {

    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "transcript.json"))

    suspend fun load(): List<TranscriptEntry> = withContext(Dispatchers.IO) {
        try {
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                TranscriptEntry(
                    id = item.getLong("id"),
                    role = Role.valueOf(item.getString("role")),
                    text = item.getString("text"),
                    interrupted = item.optBoolean("interrupted"),
                )
            }
        } catch (e: FileNotFoundException) {
            emptyList()
        } catch (e: Exception) {
            // The unreadable file is overwritten by the next save.
            Log.w(TAG, "Discarding unreadable transcript", e)
            emptyList()
        }
    }

    suspend fun save(entries: List<TranscriptEntry>) {
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            for (entry in entries) {
                array.put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("role", entry.role.name)
                        .put("text", entry.text)
                        .put("interrupted", entry.interrupted),
                )
            }
            try {
                file.writeText(array.toString())
            } catch (e: IOException) {
                Log.w(TAG, "Failed to save transcript", e)
            }
        }
    }

    private companion object {
        const val TAG = "TranscriptRepository"
    }
}
