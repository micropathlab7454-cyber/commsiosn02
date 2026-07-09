package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen {
    object Dashboard : Screen()
    object CameraScan : Screen()
    data class DataReview(val sessionId: String) : Screen()
    object DoctorPages : Screen()
    object Reports : Screen()
    data class PrintPreview(val doctorName: String, val month: String, val year: String) : Screen()
}

class DoctorViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DoctorViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = PatientRepository(database.patientDao())

    // UI state flows
    val allRecords: StateFlow<List<PatientRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uniqueDoctors: StateFlow<List<String>> = repository.uniqueDoctors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentScans: StateFlow<List<ScanSession>> = repository.recentScans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Navigation and transient UI states
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Camera Scan States
    val capturedBitmaps = mutableStateListOf<Bitmap>()
    private val _isProcessingOcr = MutableStateFlow(false)
    val isProcessingOcr: StateFlow<Boolean> = _isProcessingOcr.asStateFlow()

    private val _ocrSuccessMessage = MutableStateFlow<String?>(null)
    val ocrSuccessMessage: StateFlow<String?> = _ocrSuccessMessage.asStateFlow()

    // Search and Filter States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All") // "All", "Today", "Yesterday", "This Week", "This Month", "Custom"
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _customDateStart = MutableStateFlow("")
    val customDateStart: StateFlow<String> = _customDateStart.asStateFlow()

    private val _customDateEnd = MutableStateFlow("")
    val customDateEnd: StateFlow<String> = _customDateEnd.asStateFlow()

    // Doctor view parameters
    private val _selectedDoctor = MutableStateFlow<String?>(null)
    val selectedDoctor: StateFlow<String?> = _selectedDoctor.asStateFlow()

    private val _selectedMonth = MutableStateFlow("07")
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow("2026")
    val selectedYear: StateFlow<String> = _selectedYear.asStateFlow()

    init {
        // Pre-populate with realistic starting demo data if database is empty
        viewModelScope.launch {
            allRecords.first { it.isEmpty() || it.isNotEmpty() }
            if (allRecords.value.isEmpty()) {
                prepopulateDemoData()
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if (screen is Screen.DoctorPages && _selectedDoctor.value == null) {
            // Auto-select first doctor if available
            viewModelScope.launch {
                val docs = uniqueDoctors.first { it.isNotEmpty() || it.isEmpty() }
                if (docs.isNotEmpty()) {
                    _selectedDoctor.value = docs.first()
                }
            }
        }
    }

    fun addCapturedBitmap(bitmap: Bitmap) {
        capturedBitmaps.add(bitmap)
    }

    fun clearCapturedBitmaps() {
        capturedBitmaps.clear()
    }

    fun removeCapturedBitmap(index: Int) {
        if (index in capturedBitmaps.indices) {
            capturedBitmaps.removeAt(index)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setCustomDateRange(start: String, end: String) {
        _customDateStart.value = start
        _customDateEnd.value = end
    }

    fun selectDoctor(doctor: String) {
        _selectedDoctor.value = doctor
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun selectYear(year: String) {
        _selectedYear.value = year
    }

    /**
     * Executes OCR on all captured images, stores in DB, and opens review page
     */
    fun processCapturedImages() {
        if (capturedBitmaps.isEmpty()) return

        viewModelScope.launch {
            _isProcessingOcr.value = true
            _ocrSuccessMessage.value = null

            val sessionId = UUID.randomUUID().toString()
            val result = GeminiOcrService.scanRegisterImages(capturedBitmaps.toList(), sessionId)

            if (result.success) {
                // Insert scan session
                repository.insertScanSession(
                    ScanSession(
                        id = sessionId,
                        photoCount = capturedBitmaps.size,
                        recordCount = result.records.size,
                        averageOcrConfidence = result.confidence
                    )
                )

                // Insert all extracted patient records
                repository.insertRecords(result.records)

                _ocrSuccessMessage.value = if (result.isSimulation) {
                    "Demo mode activated! Simulated extraction of ${result.records.size} entries."
                } else {
                    "Successfully processed ${capturedBitmaps.size} register photos! OCR average confidence: ${result.confidence}%"
                }

                clearCapturedBitmaps()
                _isProcessingOcr.value = false
                navigateTo(Screen.DataReview(sessionId))
            } else {
                _isProcessingOcr.value = false
                _ocrSuccessMessage.value = "OCR failed to read register. Please try again."
            }
        }
    }

    /**
     * Editable spreadsheet updates
     */
    fun updateRecordField(record: PatientRecord, field: String, value: String) {
        viewModelScope.launch {
            val updated = when (field.uppercase()) {
                "DATE" -> record.copy(date = value)
                "PATIENTNAME" -> record.copy(patientName = value)
                "AGE" -> record.copy(age = value)
                "REFERRINGDOCTOR" -> record.copy(referringDoctor = GeminiOcrService.normalizeDoctorName(value))
                "TESTNAME" -> record.copy(testName = value)
                "COMMISSION" -> {
                    val doubleVal = value.trim().toDoubleOrNull()
                    record.copy(commission = doubleVal)
                }
                "OTHER" -> record.copy(other = value.trim())
                else -> record
            }
            repository.updateRecord(updated)
        }
    }

    fun deleteRecord(record: PatientRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }

    fun addNewRecord(sessionId: String? = null, doctorName: String? = null) {
        viewModelScope.launch {
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val newRecord = PatientRecord(
                date = today,
                patientName = "New Patient",
                age = "25 Y",
                referringDoctor = doctorName ?: "Dr. Sharma",
                testName = "CBC",
                scanSessionId = sessionId
            )
            repository.insertRecord(newRecord)
        }
    }

    fun deleteScanSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteScanSessionById(sessionId)
        }
    }

    /**
     * Pre-populate standard clinical dataset for lab registers demo
     */
    private suspend fun prepopulateDemoData() {
        val sessionId1 = UUID.randomUUID().toString()
        val sessionId2 = UUID.randomUUID().toString()

        val scan1 = ScanSession(
            id = sessionId1,
            photoCount = 2,
            recordCount = 6,
            averageOcrConfidence = 91
        )
        val scan2 = ScanSession(
            id = sessionId2,
            photoCount = 1,
            recordCount = 4,
            averageOcrConfidence = 85
        )

        repository.insertScanSession(scan1)
        repository.insertScanSession(scan2)

        val records = listOf(
            // Session 1
            PatientRecord(date = "08-07-2026", patientName = "Aarav Sharma", age = "45 Y", referringDoctor = "Dr. Sharma", testName = "CBC", commission = 150.0, other = "Emergency", scanSessionId = sessionId1),
            PatientRecord(date = "08-07-2026", patientName = "Kavita Patel", age = "32 Y", referringDoctor = "Dr. Jitendra", testName = "LFT", commission = 200.0, other = "", scanSessionId = sessionId1),
            PatientRecord(date = "08-07-2026", patientName = "Aman Gupta", age = "12 Y", referringDoctor = "Dr. Sharma", testName = "KFT", commission = null, other = "Staff Discount", scanSessionId = sessionId1),
            PatientRecord(date = "07-07-2026", patientName = "Suresh Raina", age = "54 Y", referringDoctor = "Dr. Jitendra", testName = "Thyroid Profile", commission = 250.0, other = "", scanSessionId = sessionId1),
            PatientRecord(date = "07-07-2026", patientName = "Meera Bai", age = "68 Y", referringDoctor = "Dr. Mehta", testName = "Lipid Profile", commission = 180.0, other = "Urgent", scanSessionId = sessionId1),
            PatientRecord(date = "06-07-2026", patientName = "Aman Gupta", age = "12 Y", referringDoctor = "Dr. Sharma", testName = "KFT", commission = 150.0, other = "Repeat test", scanSessionId = sessionId1, isDuplicate = true), // duplicate patient

            // Session 2
            PatientRecord(date = "05-07-2026", patientName = "Vikram Singh", age = "29 Y", referringDoctor = "Dr. Sharma", testName = "HBA1C", commission = 120.0, other = "", scanSessionId = sessionId2),
            PatientRecord(date = "05-07-2026", patientName = "Anjali Desai", age = "22 Y", referringDoctor = "Dr. Jitendra", testName = "Urine RE", commission = 80.0, other = "IPD", scanSessionId = sessionId2),
            PatientRecord(date = "04-07-2026", patientName = "Rajesh Koothrapali", age = "39 Y", referringDoctor = "Dr. Mehta", testName = "Vitamin D", commission = null, other = "", scanSessionId = sessionId2),
            PatientRecord(date = "04-07-2026", patientName = "Pooja Hegde", age = "31 Y", referringDoctor = "Dr. A. K. Gupta", testName = "CBC + Widal", commission = 180.0, other = "", scanSessionId = sessionId2)
        )

        repository.insertRecords(records)
    }
}
