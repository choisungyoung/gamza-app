package com.myapp.budget.ui.datamanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.data.remote.RealtimeManager
import com.myapp.budget.domain.repository.BookRepository
import com.myapp.budget.domain.repository.DataExportRepository
import com.myapp.budget.domain.SessionManager
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
    private val exportRepo: DataExportRepository,
    private val bookRepository: BookRepository,
    private val sessionManager: SessionManager,
    private val realtimeManager: RealtimeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState = _uiState.asStateFlow()

    /** 내보낼 파일의 (bytes, fileName)을 화면에 전달 */
    private val _exportReady = MutableSharedFlow<Pair<ByteArray, String>>()
    val exportReady = _exportReady.asSharedFlow()

    /**
     * 마지막으로 확인된 bookId를 캐시.
     * 파일 피커가 열려 있는 동안 SessionManager가 일시적으로 clear() 되어도
     * 가져오기 시 올바른 bookId를 사용할 수 있도록 보장.
     */
    private var cachedBookId: String? = null

    init {
        viewModelScope.launch {
            sessionManager.activeBook.collect { book ->
                if (book != null) cachedBookId = book.id
            }
        }
    }

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
        // sessionManager가 일시적으로 clear()되어도 cachedBookId로 폴백
        val capturedBookId = sessionManager.activeBookId ?: cachedBookId
        if (bytes.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                message = "파일을 읽을 수 없습니다. 다시 시도해 주세요.",
                isSuccess = false
            )
            return
        }
        if (capturedBookId == null) {
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                message = "가져오기 실패: 활성화된 가계부가 없습니다.",
                isSuccess = false
            )
            return
        }
        _uiState.value = _uiState.value.copy(isImporting = true, message = null)
        viewModelScope.launch {
            realtimeManager.stopWatching()
            runCatching { exportRepo.importFromExcel(bytes, capturedBookId) }
                .onSuccess { result ->
                    // 가져오기 완료 후 강제 동기화 (Supabase → 로컬 최신화)
                    runCatching { bookRepository.syncBookData(capturedBookId) }
                    // 동기화 완료 후 Realtime 감시 재개
                    realtimeManager.startWatching(capturedBookId)

                    val msg = buildString {
                        if (result.transactions == 0 && result.failed == 0) {
                            append("가져올 데이터가 없습니다.\n감자 가계부에서 내보낸 파일인지 확인해 주세요.")
                        } else {
                            append("가져오기 완료!\n")
                            append("거래내역 ${result.transactions}건")
                            if (result.failed > 0) append("\n(${result.failed}건 오류)")
                        }
                    }
                    val success = result.transactions > 0
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        message = msg,
                        isSuccess = success
                    )
                }
                .onFailure {
                    // 실패 시에도 Realtime 감시 재개
                    realtimeManager.startWatching(capturedBookId)
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
