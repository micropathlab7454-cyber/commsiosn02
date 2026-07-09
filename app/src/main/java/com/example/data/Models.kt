package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patient_records")
data class PatientRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val patientName: String,
    val age: String,
    val referringDoctor: String,
    val testName: String,
    val commission: Double? = null,
    val other: String? = null,
    val scanSessionId: String? = null,
    val ocrConfidence: Int = 100,
    val isDuplicate: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val photoCount: Int = 1,
    val recordCount: Int = 0,
    val averageOcrConfidence: Int = 100
)
