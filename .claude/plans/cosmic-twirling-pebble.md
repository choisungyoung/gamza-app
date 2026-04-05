# Plan: 현재 선택된 가계부 표시 + 전환 토스트

## Context
가계부를 전환해도 어떤 가계부가 활성화됐는지 UI에서 알 수 없음.
- 모든 메인 화면(홈/거래/통계/자산) TopAppBar 제목이 "감자 가계부"로 하드코딩
- 사이드 드로어 헤더도 "감자 가계부"로 하드코딩
- `SessionManager.activeBook` StateFlow가 존재하지만 메인 화면에서 collect 안 됨

## 변경 사항 3가지

---

### 1. SessionManager — 전환 이벤트 SharedFlow 추가

**파일**: `shared/src/commonMain/kotlin/com/myapp/budget/domain/SessionManager.kt`

명시적 가계부 전환 시에만 이벤트를 발행하는 SharedFlow 추가:
```kotlin
val bookSwitched = MutableSharedFlow<Book>(extraBufferCapacity = 1)
```

**파일**: `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/book/BookViewModel.kt`

`selectBook()` 성공 시 emit:
```kotlin
.onSuccess {
    sessionManager.setActiveBook(book)
    sessionManager.bookSwitched.tryEmit(book)  // ← 추가
}
```
> `migrateOfflineData`나 `syncBooks`에서 호출되는 `setActiveBook`은 해당 안 됨 — 초기 로드 시 토스트 미표시

---

### 2. HomeScreen TopAppBar — 가계부명 인라인 표시

**파일**: `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/home/HomeViewModel.kt`

`SessionManager` 생성자 주입, `activeBook`과 `bookSwitchedEvent` 노출:
```kotlin
class HomeViewModel(
    private val repository: TransactionRepository,
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val sessionManager: SessionManager,   // ← 추가
) : ViewModel() {
    val activeBook = sessionManager.activeBook          // StateFlow<Book?>
    val bookSwitchedEvent = sessionManager.bookSwitched // SharedFlow<Book>
    ...
}
```
> Koin `viewModelOf(::HomeViewModel)` 방식이라 `AppModule.kt` 수정 불필요

**파일**: `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/home/HomeScreen.kt`

TopAppBar title Row에 가계부명을 오른쪽에 소자로 추가, Snackbar로 전환 알림:
```
[🥔] 감자 가계부  📒 내 가계부          [Menu]
```
```kotlin
val activeBook by viewModel.activeBook.collectAsState()
val snackbarHostState = remember { SnackbarHostState() }

// 전환 토스트
LaunchedEffect(Unit) {
    viewModel.bookSwitchedEvent.collect { book ->
        snackbarHostState.showSnackbar("${book.iconEmoji} ${book.name}로 전환되었습니다")
    }
}

Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PotatoCharacter(modifier = Modifier.size(32.dp))
                    Text("감자 가계부", fontWeight = FontWeight.Bold, style = titleLarge)
                    activeBook?.let { book ->
                        Text(
                            "${book.iconEmoji} ${book.name}",
                            style = labelMedium,
                            color = Color.White.copy(alpha = 0.70f)
                        )
                    }
                }
            },
            ...
        )
    }
)
```

---

### 3. 사이드 드로어 헤더 — 활성 가계부 칩 추가

**파일**: `composeApp/src/commonMain/kotlin/com/myapp/budget/App.kt`

드로어 composable에 `activeBook: Book?` 파라미터 추가.  
`App()` 내부에서:
```kotlin
val activeBook by sessionManager.activeBook.collectAsState()
```
→ 드로어 composable에 전달

이메일 Text 아래:
```
감자 가계부
admin@naver.com
[📒 내 가계부]   ← 반투명 흰색 pill
```
```kotlin
activeBook?.let { book ->
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(book.iconEmoji, fontSize = 14.sp)
        Text(book.name, style = labelMedium, color = Color.White)
    }
}
```

---

## 수정 파일 목록

| 파일 | 변경 내용 |
|------|---------|
| `domain/SessionManager.kt` | `bookSwitched: MutableSharedFlow<Book>` 추가 |
| `ui/book/BookViewModel.kt` | `selectBook()` 성공 시 `bookSwitched.tryEmit(book)` |
| `ui/home/HomeViewModel.kt` | `SessionManager` 생성자 추가, `activeBook`/`bookSwitchedEvent` 노출 |
| `ui/home/HomeScreen.kt` | TopAppBar 인라인 가계부명, SnackbarHost, LaunchedEffect 전환 감지 |
| `App.kt` | 드로어 composable에 `activeBook` 파라미터 + 칩 UI |

## 검증
1. 빌드 후 로그인 → 홈 TopAppBar에 `📒 내 가계부` 소자 표시 확인
2. 사이드 드로어 열기 → 이메일 아래 pill 칩 확인
3. 가계부 전환 → 홈 화면 복귀 시 Snackbar "📒 내 가계부로 전환되었습니다" 표시 확인
4. 앱 첫 로드 시 토스트 미표시 확인 (초기 로드는 emit 안 함)
