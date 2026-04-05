# 다중 공유방 가계부 구현 계획 (Phase 1)

## Context
현재 앱은 순수 로컬 SQLDelight DB만 사용하는 단일 사용자 앱이다. 두 명 이상이 가계부를 공유할 수 있도록 **다중 공유방(Book)** 개념과 클라우드 동기화를 추가한다. Phase 1은 인증·가계부 생성·초대·기본 동기화를 포함한다.

---

## 기술 스택 추가

| 추가 항목 | 이유 |
|-----------|------|
| **supabase-kt 3.x** (auth-kt, postgrest-kt) | KMP 공식 Supabase 클라이언트; 기존 Ktor 위에서 동작 |
| **Supabase 프로젝트** (외부 설정 필요) | PostgreSQL + Auth + Row Level Security |

---

## Supabase 클라우드 테이블 (대시보드 SQL 에디터에서 실행)

```sql
-- 1. users (auth.users 자동 미러링)
CREATE TABLE public.users (
    id           UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email        TEXT NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- handle_new_user 트리거로 회원가입 시 자동 삽입

-- 2. books (가계부)
CREATE TABLE public.books (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    color_hex  TEXT NOT NULL DEFAULT '#A0522D',
    icon_emoji TEXT NOT NULL DEFAULT '📒',
    owner_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. book_members
CREATE TYPE public.member_role AS ENUM ('OWNER', 'EDITOR', 'VIEWER');
CREATE TABLE public.book_members (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id   UUID NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role      public.member_role NOT NULL DEFAULT 'VIEWER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(book_id, user_id)
);

-- 4. invite_codes
CREATE TABLE public.invite_codes (
    code       TEXT PRIMARY KEY,         -- 6자리 영숫자
    book_id    UUID NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES auth.users(id),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours',
    used       BOOLEAN NOT NULL DEFAULT false
);

-- 5. transactions (원격 동기화 대상)
CREATE TABLE public.transactions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id    UUID NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    local_id   BIGINT,
    title      TEXT NOT NULL,
    amount     BIGINT NOT NULL,
    type       TEXT NOT NULL,
    category   TEXT NOT NULL,
    date       TEXT NOT NULL,
    time       TEXT NOT NULL DEFAULT '00:00:00',
    note       TEXT NOT NULL DEFAULT '',
    asset      TEXT NOT NULL DEFAULT '',
    to_asset   TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
-- (categories, assets 테이블도 동일 구조로 추가)
-- 모든 테이블에 RLS 적용 (멤버만 읽기, editor/owner만 쓰기)
```

---

## 로컬 SQLDelight 스키마 변경

### 8.sqm (신규 마이그레이션)

```sql
-- 기존 6개 테이블에 book_id 추가 (기존 데이터는 '' 로 채워짐)
ALTER TABLE TransactionEntity    ADD COLUMN book_id TEXT NOT NULL DEFAULT '';
ALTER TABLE FixedExpenseEntity   ADD COLUMN book_id TEXT NOT NULL DEFAULT '';
ALTER TABLE UserCategoryEntity   ADD COLUMN book_id TEXT NOT NULL DEFAULT '';
ALTER TABLE ParentCategoryEntity ADD COLUMN book_id TEXT NOT NULL DEFAULT '';
ALTER TABLE AssetGroupEntity     ADD COLUMN book_id TEXT NOT NULL DEFAULT '';
ALTER TABLE AssetEntity          ADD COLUMN book_id TEXT NOT NULL DEFAULT '';

-- 로그인 사용자 캐시 (단일 행)
CREATE TABLE LocalUserEntity (
    id           TEXT NOT NULL PRIMARY KEY,
    email        TEXT NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    cached_at    TEXT NOT NULL DEFAULT ''
);

-- 가계부 로컬 캐시
CREATE TABLE BookEntity (
    id          TEXT NOT NULL PRIMARY KEY,
    name        TEXT NOT NULL,
    color_hex   TEXT NOT NULL DEFAULT '#A0522D',
    icon_emoji  TEXT NOT NULL DEFAULT '📒',
    owner_id    TEXT NOT NULL,
    is_selected INTEGER NOT NULL DEFAULT 0,
    synced_at   TEXT NOT NULL DEFAULT ''
);

-- 멤버 로컬 캐시
CREATE TABLE BookMemberEntity (
    id        TEXT NOT NULL PRIMARY KEY,
    book_id   TEXT NOT NULL,
    user_id   TEXT NOT NULL,
    role      TEXT NOT NULL DEFAULT 'VIEWER',
    joined_at TEXT NOT NULL DEFAULT ''
);

-- 오프라인 동기화 큐
CREATE TABLE SyncQueueEntity (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    operation   TEXT NOT NULL,   -- INSERT | UPDATE | DELETE
    table_name  TEXT NOT NULL,
    record_id   TEXT NOT NULL,
    payload     TEXT NOT NULL,   -- JSON
    created_at  TEXT NOT NULL DEFAULT '',
    retry_count INTEGER NOT NULL DEFAULT 0
);
```

> **주의**: `shared/build.gradle.kts`에서 SQLDelight version을 **6 → 8**로 변경
> (7.sqm은 이미 존재, 8.sqm이 신규)

---

## 신규 파일 목록

### shared/domain/model/
- `User.kt` — `data class LocalUser(id, email, displayName)`
- `Book.kt` — `data class Book(id, name, colorHex, iconEmoji, ownerId, isSelected)`
- `BookMember.kt` — `data class BookMember(...)` + `enum class MemberRole { OWNER, EDITOR, VIEWER }`
- `InviteCode.kt` — `data class InviteCode(code, bookId, expiresAt)`

### shared/domain/
- `SessionManager.kt` — `class SessionManager { val activeBookId: MutableStateFlow<String?> }`
  Koin `single`으로 등록. 활성 가계부 ID를 모든 ViewModel에 공유.

### shared/domain/repository/
- `AuthRepository.kt` — signUp / signIn / signOut / currentUser / authStateFlow()
- `BookRepository.kt` — getAllBooks / createBook / selectBook / invite / join / members / removeMember
- `SyncRepository.kt` — syncOnAppOpen / pushPendingChanges / enqueueSyncItem

### shared/data/remote/
- `SupabaseClientProvider.kt` — `expect object`; actual은 androidMain/iosMain에서 `createSupabaseClient(url, key)` 호출

### shared/data/repository/
- `AuthRepositoryImpl.kt` — supabase.auth 래핑; 로그인 성공 시 LocalUserEntity upsert
- `BookRepositoryImpl.kt` — Supabase book/member CRUD + 로컬 BookEntity 미러링; 초대 코드 생성/참여
- `SyncRepositoryImpl.kt` — syncOnAppOpen: Supabase→로컬 pull; pushPendingChanges: SyncQueueEntity 드레인

### composeApp/ui/auth/
- `LoginScreen.kt` — 이메일/비밀번호 로그인 UI
- `SignupScreen.kt` — 회원가입 UI (닉네임 포함)
- `AuthViewModel.kt` — AuthUiState(isLoading, error); AuthRepository 위임

### composeApp/ui/book/
- `BookListScreen.kt` — 가계부 목록, 탭으로 전환, "+" 생성
- `CreateBookScreen.kt` — 이름/색상/아이콘 입력
- `BookSettingsScreen.kt` — 이름 수정, 멤버 관리 진입, 가계부 삭제
- `InviteScreen.kt` — 6자리 코드 표시 + 만료 카운트다운 + 공유
- `MemberManagementScreen.kt` — 멤버 목록, 역할 변경, 강퇴
- `BookViewModel.kt` — BookUiState; BookRepository + SessionManager 위임

---

## 기존 파일 수정

### gradle/libs.versions.toml
```toml
[versions]
supabase = "3.1.4"

[libraries]
supabase-postgrest = { module = "io.github.jan-tennert.supabase:postgrest-kt", version.ref = "supabase" }
supabase-auth      = { module = "io.github.jan-tennert.supabase:auth-kt",      version.ref = "supabase" }
```

### shared/build.gradle.kts
- SQLDelight `version = 6` → `version = 8`
- commonMain dependencies에 `supabase-postgrest`, `supabase-auth` 추가

### shared/di/SharedModule.kt
```kotlin
single { SessionManager() }
single<AuthRepository>  { AuthRepositoryImpl(get(), get()) }
single<BookRepository>  { BookRepositoryImpl(get(), get()) }
single<SyncRepository>  { SyncRepositoryImpl(get(), get(), get()) }
```

### androidMain/di/AndroidModule.kt + iosMain/di/IosModule.kt
```kotlin
single { SupabaseClientProvider.getClient() }
```

### composeApp/di/AppModule.kt
```kotlin
viewModelOf(::AuthViewModel)
viewModelOf(::BookViewModel)
```

### composeApp/App.kt
1. **Screen 확장**: `Login`, `Signup`, `BookList`, `CreateBook`, `BookSettings(bookId)`, `Invite(bookId)`, `MemberManagement(bookId)` 추가
2. **Auth 게이트**: `authRepository.authStateFlow()` 수집 → Loading=Splash, LoggedOut=LoginScreen, LoggedIn=MainApp
3. **AppDrawer 헤더**: 현재 활성 Book 이름/아이콘 표시 + "가계부 전환" 메뉴 항목 추가
4. **showBottomBar**: 신규 화면들 제외

### 기존 ViewModel 전체 (HomeViewModel, TransactionListViewModel 등)
- `SessionManager` 생성자 주입 추가
- `repository.getAll()` → `repository.getByBookId(sessionManager.activeBookId.value ?: return)`

### 기존 Repository 인터페이스 + 구현체
- `getByBookId(bookId: String): Flow<List<...>>` 추가
- `insert(...)` 에 `bookId: String` 파라미터 추가
- `insert` 내부에서 `syncRepository.enqueueSyncItem(...)` 호출 추가

---

## 구현 순서

```
Step 1: 의존성 추가 (libs.versions.toml, shared/build.gradle.kts)
Step 2: 로컬 DB 마이그레이션 (8.sqm + Budget.sq 정규 스키마 업데이트)
        → ./gradlew generateCommonMainBudgetDatabaseInterface 로 검증
Step 3: Supabase 프로젝트 생성 + SQL 실행 (외부, 수동)
Step 4: SupabaseClientProvider expect/actual 구현
Step 5: 도메인 모델 + Repository 인터페이스 신규 작성
Step 6: SessionManager 작성 + Koin 등록
Step 7: AuthRepositoryImpl + AuthViewModel + LoginScreen + SignupScreen
Step 8: App.kt에 Auth 게이트 적용
Step 9: BookRepositoryImpl + BookViewModel + Book 화면들
Step 10: SessionManager 기반으로 기존 ViewModel/Repository를 book-scoped 전환
         → 첫 로그인 시 기존 데이터(book_id='')를 새 book_id로 백필
Step 11: SyncRepositoryImpl (pull on app open + push queue drain)
Step 12: InviteScreen + MemberManagementScreen + BookSettingsScreen
```

---

## 첫 실행 온보딩 흐름

```
신규 회원가입
  → Supabase Auth 계정 생성
  → LoggedIn 감지
  → 로컬 BookEntity가 없음 → Book.create("내 가계부") 자동 실행
  → 기존 로컬 데이터(book_id='') → 새 bookId로 백필
  → SyncRepository.syncOnAppOpen() → Supabase에 초기 데이터 업로드
```

---

## 검증 방법

1. **로컬 스키마**: `./gradlew generateCommonMainBudgetDatabaseInterface` 오류 없음
2. **신규 사용자**: 회원가입 → 자동 "내 가계부" 생성 → 거래 추가 → Supabase 대시보드에서 rows 확인
3. **공유**: A 기기에서 초대 코드 생성 → B 기기에서 코드 입력 → 두 기기에서 같은 가계부 조회
4. **권한**: Viewer 계정으로 로그인 → FAB 숨김 확인
5. **오프라인**: 비행기 모드에서 거래 추가 → 인터넷 복구 후 Supabase 동기화 확인

---

## Phase 2 (이번 범위 외)
- Supabase Realtime WebSocket 실시간 동기화
- 정산(Settlement) 기능
- 푸시 알림 (FCM/APNs + Supabase Edge Functions)
- 충돌 해결 UI (Last-Write-Wins 외 선택 옵션)
