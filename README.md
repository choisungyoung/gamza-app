# 🥔 감자 (Gamza) — 가계부 앱

Kotlin Multiplatform + Compose Multiplatform으로 만든 Android/iOS 가계부 앱.
단일 Kotlin 코드베이스로 Android와 iOS를 모두 지원합니다.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **거래 내역** | 수입 / 지출 / 이체 기록, 날짜·시간·메모 포함 |
| **자산 관리** | 계좌·현금·카드 등 자산 그룹별 잔액 추적 |
| **고정 지출** | 월 정기 지출 자동 등록 |
| **커스텀 카테고리** | 기본 카테고리 외 사용자 정의 소분류 생성 |
| **통계** | 월별 수입/지출 합계, 카테고리별 비율 차트 |
| **검색 & 필터** | 날짜·카테고리·유형·자산 기준 필터링 |

---

## 스크린 구성

```
홈 (Home)              → 월 요약, 상위 5개 지출 카테고리, 최근 거래
거래 내역 (Transactions) → 전체 목록, 필터링
통계 (Statistics)       → 차트 및 분석
자산 (Assets)           → 계좌 잔액, 그룹 관리
거래 추가/수정           → 날짜·시간·카테고리·자산 선택
카테고리 관리 (Drawer)   → 소분류 생성/편집/순서 변경
고정 지출 (Drawer)       → 정기 지출 설정
검색                    → 키워드·필터 검색
```

---

## 기술 스택

| 분류 | 라이브러리 | 버전 |
|------|-----------|------|
| Language | Kotlin Multiplatform | 2.1.0 |
| UI | Compose Multiplatform | 1.7.3 |
| DI | Koin | 4.0.0 |
| Database | SQLDelight | 2.0.2 |
| Date/Time | kotlinx-datetime | 0.6.1 |
| Serialization | kotlinx-serialization | 1.7.3 |
| HTTP | Ktor | 3.0.3 |
| Android SDK | min 26 / target 35 | — |
| iOS | 15+ (x64, Arm64, SimulatorArm64) | — |

---

## 아키텍처

```
composeApp (UI Layer)
    ↓
shared/domain (Repository Interface + Model)
    ↓
shared/data (Repository 구현체)
    ↓
SQLDelight (Database)
```

### 모듈 구조

```
gamza-app/
├── composeApp/                  # Compose UI 레이어
│   └── src/
│       ├── commonMain/          # 공통 Composable + ViewModel
│       │   └── kotlin/com/myapp/budget/
│       │       ├── App.kt       # 내비게이션 정의
│       │       ├── ui/          # 화면별 Screen + ViewModel
│       │       ├── di/          # AppModule (ViewModel DI)
│       │       └── util/        # FormatUtils (₩ 포맷)
│       ├── androidMain/         # MainActivity, BudgetApp
│       └── iosMain/             # MainViewController
│
├── shared/                      # 비즈니스 로직 레이어
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/myapp/budget/
│       │   │   ├── domain/      # Model, Repository 인터페이스
│       │   │   ├── data/        # Repository 구현체
│       │   │   ├── db/          # DatabaseDriverFactory (expect)
│       │   │   └── di/          # SharedModule
│       │   └── sqldelight/      # Budget.sq (스키마 + 쿼리)
│       ├── androidMain/         # AndroidSqliteDriver
│       └── iosMain/             # NativeSqliteDriver
│
└── gradle/libs.versions.toml    # 중앙화된 버전 관리
```

### 핵심 설계 원칙

- **DI**: Koin. 플랫폼별 `androidModule` / `iosModule` → 공통 `sharedModule` → UI `appModule` 순서로 초기화
- **DB**: SQLDelight 2.x — 컴파일 타임 타입 안전 SQL. `.sq` 파일로 스키마 및 쿼리 정의
- **플랫폼 분기**: `expect/actual`로 `DatabaseDriverFactory` 구현
- **상태 관리**: `ViewModel` + `StateFlow` + `SharingStarted.WhileSubscribed(5_000)`
- **날짜**: `kotlinx-datetime`의 `LocalDate` / `LocalTime`. DB 저장 시 ISO-8601 문자열

---

## 빌드 방법

### 사전 조건

- Android Studio Iguana 이상 또는 IntelliJ IDEA with KMM plugin
- JDK 17+
- Xcode 15+ (iOS 빌드 시)

### Android

```bash
# 디버그 APK 빌드
./gradlew :composeApp:assembleDebug

# 단위 테스트
./gradlew :composeApp:testDebugUnitTest
```

### iOS

```bash
# 시뮬레이터용 프레임워크 빌드
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

빌드 후 Xcode에서 `iosApp/iosApp.xcodeproj`를 열어 실행합니다.

### SQLDelight 코드 생성

```bash
./gradlew :shared:generateCommonMainBudgetDatabaseInterface
```

### 전체 빌드 / 테스트

```bash
./gradlew build
./gradlew :shared:allTests
```

---

## iOS 연동

`MainViewController()`를 Swift에서 호출해 Compose UI를 임베드합니다.

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

앱 진입점(`@main`)에서 Koin 초기화:

```swift
@main
struct iOSApp: App {
    init() {
        IosModuleKt.initKoin()  // MainViewController() 전에 반드시 호출
    }
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```

---

## 데이터베이스 스키마

SQLDelight로 정의된 6개 테이블:

| 테이블 | 용도 |
|--------|------|
| `TransactionEntity` | 수입/지출/이체 거래 |
| `FixedExpenseEntity` | 월 정기 지출 |
| `AssetEntity` | 계좌·현금·카드 등 자산 |
| `AssetGroupEntity` | 자산 그룹 (예금, 카드 등) |
| `UserCategoryEntity` | 사용자 정의 소분류 |
| `ParentCategoryEntity` | 부모 카테고리 커스터마이징 |

---

## 카테고리

기본 제공 18개 카테고리:

**지출 (8)**: 식비, 교통, 주거, 쇼핑, 건강, 문화, 교육, 기타지출
**수입 (5)**: 급여, 부업, 투자, 용돈, 기타수입
**이체 (5)**: 계좌이체, 저축, 현금인출, 투자이체, 대출상환
