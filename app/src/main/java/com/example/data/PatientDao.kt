package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patient_records ORDER BY date DESC, timestamp DESC")
    fun getAllRecords(): Flow<List<PatientRecord>>

    @Query("SELECT * FROM patient_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Int): PatientRecord?

    @Query("SELECT DISTINCT referringDoctor FROM patient_records WHERE referringDoctor != '' ORDER BY referringDoctor ASC")
    fun getUniqueDoctors(): Flow<List<String>>

    @Query("SELECT * FROM patient_records WHERE referringDoctor = :doctorName ORDER BY date DESC")
    fun getRecordsByDoctor(doctorName: String): Flow<List<PatientRecord>>

    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC LIMIT 50")
    fun getRecentScans(): Flow<List<ScanSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PatientRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<PatientRecord>)

    @Update
    suspend fun updateRecord(record: PatientRecord)

    @Delete
    suspend fun deleteRecord(record: PatientRecord)

    @Query("DELETE FROM patient_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanSession(session: ScanSession)

    @Query("DELETE FROM scan_sessions WHERE id = :id")
    suspend fun deleteScanSessionById(id: String)
}
