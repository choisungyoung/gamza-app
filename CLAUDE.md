# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin Multiplatform + Compose Multiplatform 가계부 앱. Android/iOS 공유 비즈니스 로직은 `shared`, UI는 `composeApp` 모듈에 위치.

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
│       ├── androidMain/ # MainActivity, Android Preview
│       └── iosMain/     # MainViewController
├── shared/              # 비즈니스 로직 레이어
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/myapp/budget/
│       │   │   ├── data/repository/   # Repository 구현체
│       │   │   ├── db/                # DatabaseDriverFactory (expect/actual)
│       │   │   ├── di/                # Koin SharedModule
│       │   │   └── domain/            # Model, Repository 인터페이스
│       │   └── sqldelight/            # .sq 파일 → Budget.sq
│       ├── androidMain/ # AndroidSqliteDriver, androidModule
│       └── iosMain/     # NativeSqliteDriver, iosModule
└── gradle/
    └── libs.versions.toml
```

## Architecture

**레이어**: `composeApp(UI)` → `shared/domain` ← `shared/data`

- **DI**: Koin. `androidModule` / `iosModule`에서 플랫폼별 `DatabaseDriverFactory`를 제공하고, `sharedModule`에서 Repository를 바인딩. ViewModel은 `composeApp`의 `appModule`에서 `viewModelOf(::XxxViewModel)`로 등록.
- **DB**: SQLDelight 2.x. `.sq` 파일은 `shared/src/commonMain/sqldelight/com/myapp/budget/db/Budget.sq`. 코드는 `com.myapp.budget.db` 패키지로 생성됨.
- **플랫폼 분기**: `expect/actual`로 `DatabaseDriverFactory` 구현. Android는 `AndroidSqliteDriver`, iOS는 `NativeSqliteDriver`.
- **날짜**: `kotlinx-datetime`의 `LocalDate`. DB 저장 시 ISO-8601 문자열(`toString()`/`LocalDate.parse()`).

## Koin 초기화

- **Android**: `BudgetApp : Application`에서 `startKoin { modules(androidModule, sharedModule, appModule) }` 호출.
- **iOS**: `shared/src/iosMain/` 내 `initKoin()` 함수에서 `startKoin { modules(iosModule, sharedModule, appModule) }` 호출. Swift 앱 진입점(`@main`)에서 반드시 `MainViewController()` 이전에 호출해야 함.

## UI 구조

**내비게이션**: `App.kt`에서 `sealed class Screen`(Home, Transactions, Statistics, AddEdit)으로 화면 정의. 하단 탭 3개(Home, Transactions, Statistics)와 `AnimatedContent`로 전환.

**ViewModel 패턴**: 모든 ViewModel은 `androidx.lifecycle.ViewModel`을 상속. `StateFlow`로 UI 상태 노출, `SharingStarted.WhileSubscribed(5_000)` 사용. Repository의 `Flow<List<Transaction>>`을 `.map()` / `.combine()`으로 변환.

**Category**: `shared/domain/model/Category.kt`에 13개 카테고리(지출 8, 수입 5)가 enum으로 정의. `displayName`(한국어), `emoji`, `type(TransactionType)` 포함. 타입별 조회: `Category.forType(TransactionType)`.

**금액 포맷**: `composeApp/util/FormatUtils.kt`의 `Long.formatAsWon()` — `₩1,000,000` 형식.

## Key Versions

| 라이브러리 | 버전 |
|---|---|
| Kotlin | 2.1.0 |
| Compose Multiplatform | 1.7.3 |
| SQLDelight | 2.0.2 |
| Koin | 4.0.0 |
| Ktor | 3.0.3 |
| kotlinx-datetime | 0.6.1 |

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

Koin 초기화는 iOS 앱 진입점(`@main`)에서 `initKoin()`을 호출해 `iosModule + sharedModule + appModule`을 등록.
