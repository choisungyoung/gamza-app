package com.myapp.budget.ui.datamanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.repository.DataExportRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DataManagementUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean? = null
)

class DataManagementViewModel(
    private val exportRepo: DataExportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState = _uiState.asStateFlow()

    /** 내보낼 파일의 (bytes, fileName)을 화면에 전달 */
    private val _exportReady = MutableSharedFlow<Pair<ByteArray, String>>()
    val exportReady = _exportReady.asSharedFlow()

    fun export() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, message = null)
            runCatching { exportRepo.exportToExcel() }
                .onSuccess { bytes ->
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    val fileName = "감자가계부_${now.year}${now.monthNumber.toString().padStart(2,'0')}${now.dayOfMonth.toString().padStart(2,'0')}.xlsx"
                    _exportReady.emit(bytes to fileName)
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        message = "내보내기 완료!",
                        isSuccess = true
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        message = "내보내기 실패: ${it.message}",
                        isSuccess = false
                    )
                }
        }
    }

    fun import(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, message = null)
            runCatching { exportRepo.importFromExcel(bytes) }
                .onSuccess { result ->
                    val msg = buildString {
                        append("가져오기 완료!\n")
                        append("거래내역 ${result.transactions}건")
                        if (result.fixedExpenses > 0) append(", 고정지출 ${result.fixedExpenses}건")
                        if (result.failed > 0) append("\n(${result.failed}건 오류)")
                    }
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        message = msg,
                        isSuccess = true
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        message = "가져오기 실패: ${it.message}",
                        isSuccess = false
                    )
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, isSuccess = null)
    }
}
