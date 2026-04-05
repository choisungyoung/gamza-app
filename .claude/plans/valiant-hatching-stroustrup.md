# 공유 가계부 동기화 기능 구현

## Context
공유 가계부에서 상대방이 추가한 데이터가 실시간으로 보이지 않는 문제.
현재 동기화는 로그인 시 1회만 발생하고, 가계부 전환 시나 앱 재진입 시에는 서버에서 데이터를 가져오지 않음.
- `pullBookData(bookId)`: Supabase → 로컬 전체 덮어쓰기 (현재 `private`)
- `selectBook()`: 로컬 DB 커서만 변경, pull 없음
- 수동 동기화 수단 없음

**목표:**
1. 가계부 전환 시 자동으로 서버 데이터 pull
2. 홈 화면 TopAppBar에 수동 동기화 버튼 추가

---

## 구현 계획

### Step 1: BookRepository 인터페이스에 syncBookData 추가
**파일:** `shared/src/commonMain/kotlin/com/myapp/budget/domain/repository/BookRepository.kt`

```kotlin
suspend fun syncBookData(bookId: String)
```

### Step 2: BookRepositoryImpl에 구현 추가
**파일:** `shared/src/commonMain/kotlin/com/myapp/budget/data/repository/BookRepositoryImpl.kt`

- `private fun pullBookData` → `private suspend fun pullBookData` 유지
- 인터페이스 구현체 추가:
```kotlin
override suspend fun syncBookData(bookId: String) {
    pullBookData(bookId)
}
```

### Step 3: BookViewModel.selectBook() 개선
**파일:** `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/book/BookViewModel.kt`

selectBook 성공 후 백그라운드에서 `syncBookData` 호출. 전환 자체는 즉시 완료 처리하고 sync는 fire-and-forget (실패해도 전환은 유지).

```kotlin
fun selectBook(book: Book) {
    viewModelScope.launch {
        runCatching { bookRepository.selectBook(book.id) }
            .onSuccess {
                sessionManager.setActiveBook(book)
                sessionManager.notifyBookSwitched(book)
                // 백그라운드 sync (실패 무시)
                runCatching { bookRepository.syncBookData(book.id) }
            }
    }
}
```

### Step 4: HomeViewModel에 sync 기능 추가
**파일:** `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/home/HomeViewModel.kt`

- 생성자에 `BookRepository` 추가 (AppModule 변경 불필요 — Koin 자동 resolve)
- 별도 `MutableStateFlow<SyncState>` 추가 (uiState는 combine stateIn이라 직접 수정 불가)

```kotlin
data class SyncState(
    val isSyncing: Boolean = false,
    val syncSuccess: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val repository: TransactionRepository,
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val sessionManager: SessionManager,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun syncCurrentBook() {
        val bookId = sessionManager.activeBookId ?: return
        viewModelScope.launch {
            _syncState.value = SyncState(isSyncing = true)
            runCatching { bookRepository.syncBookData(bookId) }
                .onSuccess { _syncState.value = SyncState(syncSuccess = true) }
                .onFailure { _syncState.value = SyncState(error = it.message ?: "동기화 실패") }
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncState()
    }
}
```

### Step 5: HomeScreen TopAppBar에 동기화 버튼 추가
**파일:** `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/home/HomeScreen.kt`

- `syncState` 수집
- `isSyncing = true` → 버튼 자리에 `CircularProgressIndicator` (소형)
- `isSyncing = false` → `Icons.Default.Sync` 아이콘 버튼
- `syncSuccess = true` → Snackbar "동기화 완료" 표시 후 `clearSyncState()` 호출
- `error != null` → Snackbar 에러 메시지 표시 후 `clearSyncState()` 호출

```kotlin
val syncState by viewModel.syncState.collectAsState()

// LaunchedEffect for sync result
LaunchedEffect(syncState.syncSuccess, syncState.error) {
    when {
        syncState.syncSuccess -> {
            snackbarHostState.showSnackbar("동기화 완료")
            viewModel.clearSyncState()
        }
        syncState.error != null -> {
            snackbarHostState.showSnackbar(syncState.error!!)
            viewModel.clearSyncState()
        }
    }
}

// TopAppBar actions
actions = {
    if (syncState.isSyncing) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    } else {
        IconButton(onClick = { viewModel.syncCurrentBook() }) {
            Icon(Icons.Default.Sync, contentDescription = "동기화")
        }
    }
    IconButton(onClick = onMenuClick) {
        Icon(Icons.Default.Menu, contentDescription = "메뉴")
    }
}
```

---

## 수정 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `shared/.../domain/repository/BookRepository.kt` | `syncBookData(bookId)` 추가 |
| `shared/.../data/repository/BookRepositoryImpl.kt` | `syncBookData` 구현 (pullBookData 위임) |
| `composeApp/.../ui/book/BookViewModel.kt` | `selectBook` 후 `syncBookData` 호출, `BookRepository` 주입 추가 |
| `composeApp/.../ui/home/HomeViewModel.kt` | `BookRepository` 주입, `SyncState`, `syncCurrentBook()`, `clearSyncState()` 추가 |
| `composeApp/.../ui/home/HomeScreen.kt` | `syncState` 수집, TopAppBar Sync 버튼/스피너, Snackbar 처리 |
| `composeApp/.../di/AppModule.kt` | **변경 없음** (Koin 자동 resolve) |

---

## Step 6: 로그인/회원가입 키보드 가림 현상 수정
**파일:**
- `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/auth/LoginScreen.kt`
- `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/auth/SignupScreen.kt`

**원인:** `imePadding()`은 적용되어 있으나, Column에 `verticalScroll`이 없어서 키보드가 올라올 때 아래 필드가 가려짐. LoginScreen은 `Alignment.Center`라 더 심함.

**LoginScreen 수정:**
- `Box(contentAlignment = Alignment.Center)` → `Alignment.TopCenter`
- Column에 `.verticalScroll(rememberScrollState())` 추가
- `import androidx.compose.foundation.rememberScrollState`, `verticalScroll` 추가

**SignupScreen 수정:**
- Column에 `.verticalScroll(rememberScrollState())` 추가
- 동일 import 추가

---

## 수정 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `shared/.../domain/repository/BookRepository.kt` | `syncBookData(bookId)` 추가 |
| `shared/.../data/repository/BookRepositoryImpl.kt` | `syncBookData` 구현 (pullBookData 위임) |
| `composeApp/.../ui/book/BookViewModel.kt` | `selectBook` 후 `syncBookData` 호출, `BookRepository` 주입 추가 |
| `composeApp/.../ui/home/HomeViewModel.kt` | `BookRepository` 주입, `SyncState`, `syncCurrentBook()`, `clearSyncState()` 추가 |
| `composeApp/.../ui/home/HomeScreen.kt` | `syncState` 수집, TopAppBar Sync 버튼/스피너, Snackbar 처리 |
| `composeApp/.../ui/auth/LoginScreen.kt` | `Alignment.TopCenter`, Column에 `verticalScroll` 추가 |
| `composeApp/.../ui/auth/SignupScreen.kt` | Column에 `verticalScroll` 추가 |
| `composeApp/.../di/AppModule.kt` | **변경 없음** (Koin 자동 resolve) |

---

## 검증

1. 빌드: `./gradlew :composeApp:assembleDebug`
2. 공유 가계부 전환 → 앱이 서버에서 데이터를 pull하는지 확인 (logcat)
3. 홈 화면 Sync 버튼 탭 → 스피너 표시 → "동기화 완료" Snackbar
4. 상대방이 거래 추가 후 Sync 버튼 탭 → 새 거래 반영 확인
5. 로그인 화면에서 비밀번호 필드 탭 → 키보드가 올라와도 필드가 가려지지 않음
6. 회원가입 화면에서 비밀번호 확인 필드 탭 → 스크롤 가능, 필드 보임
