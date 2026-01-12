package com.example.barcode_detection

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data logging module for box records. Logs each counted box to JSON file with rotation support.
 */
class DataLogger(private val logDirectory: File) {

    companion object {
        private const val TAG = "DataLogger"
        private const val LOG_FILE_PREFIX = "box_records_"
        private const val LOG_FILE_EXTENSION = ".json"
        private const val MAX_RECORDS_PER_FILE = 1000
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

    private var currentRecords = mutableListOf<BoxRecord>()
    private var recordCount = 0

    init {
        // Create log directory if it doesn't exist
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
    }

    /** Log a box record */
    fun logRecord(record: BoxRecord) {
        synchronized(this) {
            currentRecords.add(record)
            recordCount++

            Log.i(TAG, "Logged box #$recordCount: ${record.barcode} (PO: ${record.po ?: "N/A"})")

            // Rotate file if needed
            if (currentRecords.size >= MAX_RECORDS_PER_FILE) {
                flushToFile()
            }
        }
    }

    /** Create a box record with current timestamp */
    fun createRecord(
            barcode: String,
            po: String?,
            poStatus: POStatus,
            cameraId: String,
            frameId: String
    ): BoxRecord {
        return BoxRecord(
                barcode = barcode,
                po = po,
                poStatus = poStatus,
                timestamp = getCurrentTimestamp(),
                cameraId = cameraId,
                frameId = frameId
        )
    }

    /** Get current ISO8601 timestamp */
    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }

    /** Flush current records to file */
    fun flushToFile() {
        synchronized(this) {
            if (currentRecords.isEmpty()) return

            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "$LOG_FILE_PREFIX$timestamp$LOG_FILE_EXTENSION"
                val file = File(logDirectory, filename)

                val json = gson.toJson(currentRecords)
                file.writeText(json)

                Log.i(TAG, "Flushed ${currentRecords.size} records to $filename")
                currentRecords.clear()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush records", e)
            }
        }
    }

    /** Get total record count */
    fun getTotalCount(): Int = recordCount

    /** Get all log files */
    fun getLogFiles(): List<File> {
        return logDirectory
                .listFiles { file ->
                    file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(LOG_FILE_EXTENSION)
                }
                ?.toList()
                ?: emptyList()
    }

    /** Export all records to a single file */
    fun exportAll(outputFile: File): Boolean {
        return try {
            val allRecords = mutableListOf<BoxRecord>()

            // Read all log files
            getLogFiles().forEach { file ->
                val json = file.readText()
                val records = gson.fromJson(json, Array<BoxRecord>::class.java)
                allRecords.addAll(records)
            }

            // Add current records
            allRecords.addAll(currentRecords)

            // Write to output file
            val json = gson.toJson(allRecords)
            outputFile.writeText(json)

            Log.i(TAG, "Exported ${allRecords.size} records to ${outputFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            false
        }
    }
}
