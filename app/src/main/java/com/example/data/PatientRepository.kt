package com.example.data

import kotlinx.coroutines.flow.Flow

class PatientRepository(private val patientDao: PatientDao) {
    val allRecords: Flow<List<PatientRecord>> = patientDao.getAllRecords()
    val uniqueDoctors: Flow<List<String>> = patientDao.getUniqueDoctors()
    val recentScans: Flow<List<ScanSession>> = patientDao.getRecentScans()

    fun getRecordsByDoctor(doctorName: String): Flow<List<PatientRecord>> =
        patientDao.getRecordsByDoctor(doctorName)

    suspend fun getRecordById(id: Int): PatientRecord? =
        patientDao.getRecordById(id)

    suspend fun insertRecord(record: PatientRecord): Long =
        patientDao.insertRecord(record)

    suspend fun insertRecords(records: List<PatientRecord>) =
        patientDao.insertRecords(records)

    suspend fun updateRecord(record: PatientRecord) =
        patientDao.updateRecord(record)

    suspend fun deleteRecord(record: PatientRecord) =
        patientDao.deleteRecord(record)

    suspend fun deleteRecordById(id: Int) =
        patientDao.deleteRecordById(id)

    suspend fun insertScanSession(session: ScanSession) =
        patientDao.insertScanSession(session)

    suspend fun deleteScanSessionById(id: String) =
        patientDao.deleteScanSessionById(id)
}
