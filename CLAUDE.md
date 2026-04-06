# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin Multiplatform + Compose Multiplatform 가계부 앱. Android/iOS 공유 비즈니스 로직은 `shared`, UI는 `composeApp` 모듈에 위치. Supabase 기반 인증·공유 가계부·실시간 동기화 지원.

- **Package**: `com.myapp.budget`
- **Min SDK**: Android 26, iOS 15
- **Targets**: Android, iosX64, iosArm64, iosSimulatorArm64

## Build Commands

```bash
# Android 빌드
./gradlew :composeApp:assembleDebug

# iOS 프레임워크 빌드 (시뮬레이터)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# SQLDelight 코드 생성
./gradlew :shared:generateCommonMainBudgetDatabaseInterface

# 전체 빌드
./gradlew build

# 테스트
./gradlew :shared:allTests
./gradlew :composeApp:testDebugUnitTest
```

## Module Structure

```
gamza-app/
├── composeApp/          # Compose UI 레이어 (Android + iOS)
│   └── src/
│       ├── commonMain/  # 공통 Composable, ViewModel
│       │   └── kotlin/com/myapp/budget/
│       │       ├── App.kt           # sealed class Screen + 내비게이션
│       │       ├── ui/              # 화면별 Screen + ViewModel
│       │       │   ├── auth/        # LoginScreen, SignupScreen, AuthViewModel
│       │       │   ├── home/        # HomeScreen, HomeViewModel
│       │       │   ├── transactions/# TransactionListScreen, TransactionListViewModel
│       │       │   ├── statistics/  # StatisticsScreen, StatisticsViewModel
│       │       │   ├── asset/       # AssetScreen, AssetViewModel
│       │       │   ├── addedit/     # AddEditScreen, CategoryPickerDialog, AssetPickerDialog
│       │       │   ├── search/      # SearchScreen, SearchViewModel
│       │       │   ├── category/    # CategoryManagementScreen, CategoryManagementViewModel
│       │       │   ├── fixedexpense/# FixedExpenseScreen, FixedExpenseViewModel
│       │       │   ├── book/        # BookListScreen, BookSettingsScreen, CreateBookScreen,
│       │       │   │                #   EditBookScreen, InviteScreen, MemberManagementScreen, BookViewModel
│       │       │   ├── datamanagement/ # DataManagementScreen (XLSX 내보내기/가져오기)
│       │       │   ├── dbviewer/    # DbViewerScreen (관리자 DB 조회)
│       │       │   ├── splash/      # SplashScreen
│       │       │   └── components/  # PotatoCharacter, TransactionItem, EmojiText
│       │       ├── di/              # appModule (viewModelOf 등록)
│       │       ├── platform/        # expect/actual: FileOps, GoogleAuth, BackPressExitHandler, OnBackPressed
│       │       └── util/            # FormatUtils (₩ 포맷)
│       ├── androidMain/ # BudgetApp (Application), MainActivity
│       └── iosMain/     # MainViewController, initKoin(url, key, clientId)
├── shared/              # 비즈니스 로직 레이어
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/myapp/budget/
│       │   │   ├── domain/
│       │   │   │   ├── model/         # Transaction, Asset, AssetGroup, Book, BookMember,
│       │   │   │   │                  #   FixedExpense, Category, UserCategory, ParentCategory,
│       │   │   │   │                  #   MemberRole, LocalUser, MonthlySummary, DbTableData
│       │   │   │   ├── repository/    # Repository 인터페이스 8개
│       │   │   │   └── SessionManager.kt  # 활성 사용자·가계부 상태 (StateFlow)
│       │   │   ├── data/
│       │   │   │   ├── repository/    # Repository 구현체 8개
│       │   │   │   └── remote/        # RemoteDtos, SupabaseClientProvider, RealtimeManager
│       │   │   ├── db/                # DatabaseDriverFactory (expect), BudgetDatabaseSeeder
│       │   │   └── di/                # sharedModule
│       │   └── sqldelight/            # Budget.sq (스키마 + 쿼리), 마이그레이션 1~11.sqm
│       ├── androidMain/ # AndroidSqliteDriver, androidModule
│       └── iosMain/     # NativeSqliteDriver, iosModule
└── gradle/
    └── libs.versions.toml
```

## Architecture

**레이어**: `composeApp(UI)` → `shared/domain` ← `shared/data` ↔ `Supabase`

- **DI**: Koin. `androidModule` / `iosModule`에서 플랫폼별 `DatabaseDriverFactory`를 제공하고, `sharedModule`에서 Repository·SessionManager·RealtimeManager를 바인딩. ViewModel은 `composeApp`의 `appModule`에서 `viewModelOf(::XxxViewModel)`로 등록.
- **DB**: SQLDelight 2.x. `.sq` 파일은 `shared/src/commonMain/sqldelight/com/myapp/budget/db/Budget.sq`. 코드는 `com.myapp.budget.db` 패키지로 생성됨. 마이그레이션은 `1~11.sqm`.
- **플랫폼 분기**: `expect/actual`로 `DatabaseDriverFactory`(DB 드라이버), `FileOps`(파일 저장/열기), `GoogleAuth`(Google 로그인), `BackPressExitHandler`/`OnBackPressed`(뒤로가기) 구현.
- **날짜**: `kotlinx-datetime`의 `LocalDate` / `LocalTime`. DB 저장 시 ISO-8601 문자열(`toString()`/`LocalDate.parse()`).

## Koin 초기화

- **Android**: `BudgetApp : Application`에서 `startKoin { modules(androidModule, sharedModule, appModule) }` 호출.
- **iOS**: `iosMain/MainViewController.kt` 내 `initKoin(supabaseUrl, supabaseAnonKey, googleWebClientId)` 함수에서 `startKoin { modules(iosModule, sharedModule, appModule) }` 호출. Swift 앱 진입점(`@main`)에서 반드시 `MainViewController()` 이전에 호출해야 함.

## Supabase 연동

- **SupabaseClientProvider**: URL·AnonKey를 받아 SupabaseClient 싱글톤 생성. Auth, Postgrest, Realtime, ComposeAuth(Google) 플러그인 설치.
- **인증**: `AuthRepositoryImpl` → Supabase Auth (이메일/비밀번호 + Google). 로그인 후 `SessionManager.setUser()` 호출.
- **동기화 흐름**: 가계부 생성 시 Supabase `create_book` RPC 호출 → 로컬 캐시 저장. 가계부 전환 시 `syncBookData(bookId)` → `pullBookData`로 전체 덮어쓰기. 실시간 변경은 `RealtimeManager`가 감지해 자동 재동기화.
- **book_id 격리**: 모든 데이터 테이블(TransactionEntity, AssetEntity 등)에 `book_id TEXT NOT NULL`이 있어 가계부 단위로 데이터가 격리됨. Repository는 `SessionManager.activeBook`을 `flatMapLatest`로 구독해 book_id가 없으면 `emptyList()` 반환.

## UI 구조

**내비게이션**: `App.kt`에서 `sealed class Screen`으로 화면 정의. 하단 탭 4개(Home, Transactions, Statistics, Assets)와 `AnimatedContent`로 전환. 탭 외 화면(BookList, AddEdit 등)은 하단 탭 숨김.

**Screen 목록**:
- 탭: `Home`, `Transactions`, `Statistics`, `Assets`
- 거래: `AddEdit(transactionId?, previousScreen)`
- 검색: `Search`
- 관리: `CategoryManagement(previousScreen)`, `FixedExpenses(previousScreen)`, `DataManagement(previousScreen)`, `DbViewer(previousScreen)`
- 가계부: `BookList`, `CreateBook(previousScreen)`, `BookSettings(bookId, previousScreen)`, `EditBook(bookId, previousScreen, readOnly)`, `Invite(bookId, previousScreen)`, `MemberManagement(bookId, previousScreen)`
- 인증: `Login`, `Signup`

**ViewModel 패턴**: 모든 ViewModel은 `androidx.lifecycle.ViewModel`을 상속. `StateFlow`로 UI 상태 노출, `SharingStarted.WhileSubscribed(5_000)` 사용. Repository의 `Flow`를 `.map()` / `.combine()`으로 변환.

**SessionManager**: `activeBook: StateFlow<Book?>`, `currentUser: StateFlow<LocalUser?>`, `currentRole: StateFlow<MemberRole?>`, `bookSwitched: SharedFlow<Book>` 노출. Repository들이 `activeBook`을 구독해 book_id 기반 쿼리 실행.

**BudgetDatabaseSeeder**: DB 최초 생성 시 `seedIfNeeded(db)` → 기본 카테고리 삽입. 가계부 생성 시 `seedAssetsForBook(db, bookId)` → 기본 자산 그룹 5개 + 기본 계좌 1개 삽입 (`insertAssetGroupWithBook` / `insertAssetWithBook` 사용).

**금액 포맷**: `composeApp/util/FormatUtils.kt`의 `Long.formatAsWon()` — `₩1,000,000` 형식.

**로고**: `composeApp/src/commonMain/composeResources/drawable/potato_character.xml`. `PotatoCharacter(modifier)` 컴포넌트로 사용.

## Key Versions

| 라이브러리 | 버전 |
|---|---|
| Kotlin | 2.1.0 |
| Compose Multiplatform | 1.7.3 |
| SQLDelight | 2.0.2 |
| Koin | 4.0.0 |
| Supabase | 3.1.4 |
| Ktor | 3.1.3 |
| kotlinx-datetime | 0.6.1 |
| kotlinx-serialization | 1.7.3 |

## iOS 연동

`MainViewController()`를 Swift에서 호출해 Compose UI를 임베드:

```swift
// ContentView.swift
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

Koin + Supabase 초기화는 iOS 앱 진입점(`@main`)에서 `initKoin(supabaseUrl:supabaseAnonKey:googleWebClientId:)`를 호출해 `iosModule + sharedModule + appModule`을 등록.
