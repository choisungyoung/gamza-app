# Google OAuth 로그인 + 비로그인 모드 구현 플랜

## Context

현재 앱은 Supabase 이메일 로그인이 필수 게이트로 작동 — 로그인하지 않으면 앱 자체를 사용할 수 없음.
목표:
1. Google OAuth 로그인 버튼 추가
2. 로그인 없이 앱 사용 가능 (SQLDelight 로컬 DB는 로그인 불필요)
3. 가계부 공유 기능(Book 관련 화면) 접근 시에만 로그인 요구

---

## 사용자가 직접 해야 하는 작업 (코드 작업 전)

1. `local.properties`에 추가:
   ```
   GOOGLE_WEB_CLIENT_ID=<Web Client ID>.apps.googleusercontent.com
   ```
2. Supabase 대시보드 > Authentication > URL Configuration > Redirect URLs에 추가:
   - `<REVERSED_IOS_CLIENT_ID>:/oauth2redirect`  
     (예: `com.googleusercontent.apps.XXXXX-YYYY:/oauth2redirect`)

---

## 변경 파일 목록 (순서 중요)

### 1. `gradle/libs.versions.toml`
- `[versions]`에 추가:
  ```toml
  credentials = "1.5.0"
  googleid = "1.1.1"
  ```
- `[libraries]`에 추가:
  ```toml
  supabase-compose-auth = { module = "io.github.jan-tennert.supabase:compose-auth", version.ref = "supabase" }
  androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "credentials" }
  androidx-credentials-play-services = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "credentials" }
  googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleid" }
  ```

### 2. `shared/build.gradle.kts`
- `commonMain.dependencies`에 추가:
  ```kotlin
  implementation(libs.supabase.compose.auth)
  ```

### 3. `composeApp/build.gradle.kts`
- `androidMain.dependencies`에 추가:
  ```kotlin
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  ```
- `android { defaultConfig }` BuildConfig 필드 추가:
  ```kotlin
  buildConfigField("String", "GOOGLE_WEB_CLIENT_ID",
      "\"${localProperties["GOOGLE_WEB_CLIENT_ID"] ?: ""}\"")
  ```

### 4. `shared/src/commonMain/kotlin/com/myapp/budget/data/remote/SupabaseClientProvider.kt`
- `configure()` 시그니처에 `googleWebClientId: String = ""` 파라미터 추가
- `install(ComposeAuth) { if (googleWebClientId.isNotBlank()) googleNativeLogin(serverClientId = googleWebClientId) }` 추가
- import: `io.github.jan.supabase.compose.auth.ComposeAuth`, `io.github.jan.supabase.compose.auth.googleNativeLogin`

### 5. `composeApp/src/androidMain/kotlin/com/myapp/budget/BudgetApp.kt`
- `SupabaseClientProvider.configure()` 호출에 `googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID` 추가

### 6. `composeApp/src/iosMain/kotlin/com/myapp/budget/MainViewController.kt`
- `initKoin(supabaseUrl, supabaseAnonKey, googleWebClientId: String = "")` 파라미터 추가
- `SupabaseClientProvider.configure()` 호출에 `googleWebClientId = googleWebClientId` 전달

### 7. `iosApp/iosApp/iOSApp.swift`
- `doInitKoin` 호출에 `googleWebClientId: "YOUR_WEB_CLIENT_ID"` 추가
- (Kotlin 함수명 `initKoin` → Swift에서 `doInitKoin`으로 노출됨)

### 8. `iosApp/iosApp/Info.plist`
- CFBundleURLTypes에 reversed iOS Client ID URL 스킴 추가:
  ```xml
  <key>CFBundleURLTypes</key>
  <array>
      <dict>
          <key>CFBundleTypeRole</key><string>Editor</string>
          <key>CFBundleURLSchemes</key>
          <array>
              <string>com.googleusercontent.apps.XXXXX-YYYY</string>
          </array>
      </dict>
  </array>
  ```

### 9. `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/auth/AuthViewModel.kt`
- `setError(message: String)` 메서드 추가:
  ```kotlin
  fun setError(message: String) {
      _uiState.value = AuthUiState(error = message)
  }
  ```

### 10. `composeApp/src/commonMain/kotlin/com/myapp/budget/App.kt` — 핵심 변경
현재 구조:
```
splash → if (authUser == null) return Auth screens → main app
```
변경 후 구조:
```
splash → main app (항상) + authUser null이면 Book 접근 시 Login으로 이동
```

**구체적 변경:**

a) `if (authUser == null) { ... return@BudgetTheme }` 블록 (lines 141-160) **삭제**

b) `var currentScreen` 선언 바로 아래에 추가:
```kotlin
var postLoginDestination by remember { mutableStateOf<Screen?>(null) }
```

c) `onNavigate` 람다를 `navigateWithAuthGuard`로 교체:
```kotlin
val navigateWithAuthGuard: (Screen) -> Unit = remember(authUser) { { screen ->
    val isBookScreen = screen is Screen.BookList || screen is Screen.CreateBook
        || screen is Screen.BookSettings || screen is Screen.Invite
        || screen is Screen.MemberManagement
    if (isBookScreen && authUser == null) {
        postLoginDestination = screen
        currentScreen = Screen.Login
    } else {
        currentScreen = screen
    }
}}
```

d) `onBookListClick` 콜백 (line 235-238)을 `navigateWithAuthGuard` 사용으로 변경:
```kotlin
onBookListClick = {
    drawerOpen = false
    navigateWithAuthGuard(Screen.BookList)
}
```

e) `AppContent` 호출에 `onLoginSuccess` 추가:
```kotlin
AppContent(
    currentScreen = currentScreen,
    onNavigate = navigateWithAuthGuard,
    onMenuClick = onMenuClick,
    onLoginSuccess = {
        val dest = postLoginDestination ?: Screen.Home
        postLoginDestination = null
        currentScreen = dest
    }
)
```

f) `AppContent` 함수 시그니처에 `onLoginSuccess: () -> Unit` 추가

g) `showBottomBar`에 Login/Signup 제외 조건 추가:
```kotlin
&& currentScreen !is Screen.Login
&& currentScreen !is Screen.Signup
```

h) `AnimatedContent`의 `when(screen)` 블록에 Login/Signup 케이스 추가 (현재 main tabs 처리 이후):
```kotlin
is Screen.Login -> LoginScreen(
    onNavigateToSignup = { onNavigate(Screen.Signup) },
    onLoginSuccess = onLoginSuccess,
)
is Screen.Signup -> SignupScreen(
    onBack = { onNavigate(Screen.Login) },
    onSignupSuccess = { onNavigate(Screen.Login) },
)
```

### 11. `composeApp/src/commonMain/kotlin/com/myapp/budget/ui/auth/LoginScreen.kt`
- `rememberLoginWithGoogle` 추가 (compose-auth 제공)
- 기존 로그인 버튼 아래에 구분선 + Google 로그인 버튼 추가:
  ```kotlin
  val googleLoginState = rememberLoginWithGoogle(
      onResult = { result ->
          when (result) {
              is NativeSignInResult.Success -> onLoginSuccess()
              is NativeSignInResult.Error -> viewModel.setError(result.message)
              is NativeSignInResult.ClosedByUser -> {}
              is NativeSignInResult.NetworkError -> viewModel.setError("네트워크 오류가 발생했습니다.")
          }
      }
  )
  // 기존 로그인 Button + TextButton 사이에 삽입:
  OutlinedButton(
      onClick = { googleLoginState.startFlow() },
      modifier = Modifier.fillMaxWidth().height(48.dp),
      enabled = !uiState.isLoading,
  ) {
      Text("Google로 로그인")
  }
  ```

---

## 검증 방법

1. **Android 빌드**: `./gradlew :composeApp:assembleDebug` — 에러 없이 빌드
2. **비로그인 모드**: 앱 실행 → 로그인 없이 홈/내역/통계/자산 탭 정상 작동 확인
3. **Book 접근 시 로그인 유도**: 햄버거 메뉴 > 가계부 전환 → 로그인 화면으로 이동 확인
4. **이메일 로그인 후 Book 이동**: 로그인 성공 시 BookList 화면으로 자동 이동 확인
5. **Google 로그인 (Android)**: Google 로그인 버튼 클릭 → Credential Manager 시스템 다이얼로그 표시 → 계정 선택 → 로그인 성공
6. **iOS 빌드**: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — 에러 없이 빌드
7. **Google 로그인 (iOS)**: Google 로그인 버튼 → Safari 시트 → Google 인증 → 앱으로 복귀 → 로그인 성공
