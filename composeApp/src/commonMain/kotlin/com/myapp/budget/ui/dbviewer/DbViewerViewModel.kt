package com.myapp.budget.ui.dbviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.DbTableData
import com.myapp.budget.domain.repository.DbViewerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DbViewerViewModel(
    private val repository: DbViewerRepository
) : ViewModel() {

    private val _tables = MutableStateFlow<List<DbTableData>>(emptyList())
    val tables: StateFlow<List<DbTableData>> = _tables

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _deleteResult = MutableStateFlow<String?>(null)
    val deleteResult: StateFlow<String?> = _deleteResult

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _tables.value = repository.getAllTableData()
            _isLoading.value = false
        }
    }

    fun deleteTable(tableName: String) {
        viewModelScope.launch {
            runCatching { repository.deleteTable(tableName) }
                .onSuccess {
                    _deleteResult.value = "$tableName 전체 삭제 완료"
                    loadData()
                }
                .onFailure { _deleteResult.value = "삭제 실패: ${it.message}" }
        }
    }

    fun clearDeleteResult() {
        _deleteResult.value = null
    }
}
