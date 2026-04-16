# 고정지출 → 거래 is_fixed 통합 리팩토링 플랜

## Context
`fixed_expenses` 테이블과 `transactions.fixed_expense_id` FK 구조를 제거하고,
`transactions.is_fixed = true` 플래그 하나로 단순화.
자동생성은 Supabase Edge Function이 매일 전달의 `is_fixed=true` 거래를 이번달로 복사.
앱 시작 시 `autoRegisterPending` 완전 제거.

**확정 요건:**
- 수정: 이번달 거래만 변경 → 다음달은 수정된 값을 자동으로 참조
- 삭제: is_fixed=false로 전환 → 전달 참조 없어지면 자동생성 중단
- 자동생성: Edge Function(B)만, 클라이언트 autoRegisterPending(A) 제거
- fixed_origin_id 없음 (비운영 → 불필요)

---

## 변경 파일 목록

### 삭제
- `shared/.../domain/model/FixedExpense.kt`
- `shared/.../domain/repository/FixedExpenseRepository.kt`
- `shared/.../data/repository/FixedExpenseRepositoryImpl.kt`
- `composeApp/.../ui/fixedexpense/FixedExpenseViewModel.kt`

### 신규 생성
- `shared/src/commonMain/sqldelight/.../13.sqm`
- `supabase/functions/auto-register-fixed/index.ts`
- `composeApp/.../ui/fixedexpense/FixedTransactionViewModel.kt`

---

## Step 1: `13.sqm` — 로컬 DB 마이그레이션

```sql
-- TransactionEntity 재생성: fixed_expense_id 제거, is_fixed 추가
CREATE TABLE TransactionEntity_new (
    id             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    title          TEXT    NOT NULL,
    amount         INTEGER NOT NULL,
    type           TEXT    NOT NULL,
    category       TEXT    NOT NULL,
    category_emoji TEXT    NOT NULL DEFAULT '',
    date           TEXT    NOT NULL,
    time           TEXT    NOT NULL DEFAULT '00:00:00',
    note           TEXT    NOT NULL DEFAULT '',
    asset          TEXT    NOT NULL DEFAULT '',
    to_asset       TEXT    NOT NULL DEFAULT '',
    is_fixed       INTEGER NOT NULL DEFAULT 0,
    book_id        TEXT    NOT NULL DEFAULT '',
    created_by     TEXT    NOT NULL DEFAULT '',
    remote_id      TEXT    NOT NULL DEFAULT ''
);
INSERT INTO TransactionEntity_new
    SELECT id, title, amount, type, category, category_emoji, date, time,
           note, asset, to_asset, 0, book_id, created_by, remote_id
    FROM TransactionEntity;
DROP TABLE TransactionEntity;
ALTER TABLE TransactionEntity_new RENAME TO TransactionEntity;
DROP TABLE IF EXISTS FixedExpenseEntity;
```

---

## Step 2: `supabase_schema.sql` 수정

- DROP 섹션: `fixed_expenses`, `fixed_expenses_book_member` 정책 추가
- CREATE `transactions`: `fixed_expense_id` 제거, `is_fixed BOOLEAN NOT NULL DEFAULT FALSE` 추가
- CREATE `fixed_expenses` 블록 전체 제거
- RLS: `fixed_expenses_book_member` 정책 제거

---

## Step 3: `Budget.sq` 수정

**TransactionEntity DDL:** `fixed_expense_id INTEGER` 제거, `is_fixed INTEGER NOT NULL DEFAULT 0` 추가

**제거할 쿼리:** `selectByFixedExpenseId`, `countByFixedExpenseId`, `detachFixedExpense`,
`deleteByFixedExpenseId`, `detachFixedExpenseFromDate`, FixedExpenseEntity 관련 DDL+쿼리 전체
(selectAllFixedExpenses, selectFixedExpensesByBookId, insertFixedExpenseWithBook, 등)

**수정할 쿼리:**
```sql
insertWithBookAndCreator:
INSERT INTO TransactionEntity (title, amount, type, category, category_emoji, date, time, note, asset, to_asset, is_fixed, book_id, created_by)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

update:
UPDATE TransactionEntity
SET title=?, amount=?, type=?, category=?, category_emoji=?, date=?, time=?, note=?, asset=?, to_asset=?, is_fixed=?
WHERE id=?;
```

**추가할 쿼리:**
```sql
selectFixedTransactionsByBookId:
SELECT * FROM TransactionEntity
WHERE book_id = ? AND is_fixed = 1
ORDER BY date DESC, id DESC;
```

---

## Step 4: `Transaction.kt` 도메인 모델

```kotlin
val isFixed: Boolean = false,   // 추가
// val fixedExpenseId: Long? = null  ← 제거
```

---

## Step 5: `RemoteDtos.kt` 수정

```kotlin
// TransactionRemoteDto
@SerialName("is_fixed") val isFixed: Boolean = false,  // 추가
// fixedExpenseId 필드 제거

// FixedExpenseRemoteDto 클래스 전체 삭제
```

---

## Step 6: `TransactionRepository.kt` 인터페이스

**제거:** `getByFixedExpenseId`, `detachFixedExpense`, `deleteByFixedExpenseId`, `deleteByFixedExpenseRemoteId`

**추가:** `fun getAllFixed(): Flow<List<Transaction>>`

---

## Step 7: `TransactionRepositoryImpl.kt`

- `insert()` / `update()` / `toModel()`: `isFixed` 추가, `fixedExpenseId` 제거
- 제거된 메서드 4개 삭제
- `getAllFixed()` 추가 (`selectFixedTransactionsByBookId` 사용)

---

## Step 8: `BookRepositoryImpl.kt`

**`pullBookData()` 변경:**
```kotlin
// 제거: fixed_expenses fetch, deleteFixedExpensesByBookId, insertFixedExpenseWithBookFull 블록
// transactions.forEach 내부: fixedExpenseId 매핑 제거, is_fixed 추가
queries.insertWithBookAndCreator(
    t.title, t.amount, t.type, t.category, t.categoryEmoji, t.date,
    t.time, t.note, t.asset, t.toAsset,
    if (t.isFixed) 1L else 0L,  // fixed_expense_id 대신 is_fixed
    bookId, t.createdBy
)
```

**`pushBookData()` 변경:** fixed_expenses push 블록 전체 제거. transactions push에 `isFixed` 포함.

---

## Step 9: `HomeViewModel.kt`

```kotlin
// 제거: FixedExpenseRepository 파라미터
// 제거: autoRegisterPending 호출 2곳 (init, bookSwitched)
```

---

## Step 10: `AddEditViewModel.kt`

**제거:**
- `fixedExpenseRepository` 의존성
- `loadedFixedExpenseId`, `loadedFixedExpenseRemoteId`
- `confirmRemoveFixed()`, `countPendingMonths()`, `pendingAutoRegisterCount`
- `showAutoRegisterDialog`, `confirmAutoRegister()`, `showRemoveFixedDialog`
- `save()` 내 3-case FixedExpense 분기 전체

**변경 후 save() 핵심:**
```kotlin
val transaction = Transaction(
    id = editingId ?: 0, title = title.trim(), amount = amount,
    type = transactionType, category = categoryStr,
    categoryEmoji = selectedParent!!.emoji, date = date, time = time,
    note = note.trim(), asset = selectedAsset, toAsset = toAsset,
    isFixed = saveAsFixed && transactionType == TransactionType.EXPENSE
)
if (editingId != null) repository.update(transaction) else repository.insert(transaction)
onSuccess()
```

**`onFixedExpenseToggled()` 단순화:**
```kotlin
fun onFixedExpenseToggled(newValue: Boolean) { saveAsFixed = newValue }
```

---

## Step 11: `AddEditScreen.kt`

- 고정 거래 토글: `saveAsFixed` 로직 단순화 (EXPENSE 타입일 때만 표시 유지)
- `showAutoRegisterDialog` 다이얼로그 블록 제거
- `showRemoveFixedDialog` 블록 제거

---

## Step 12: `FixedTransactionViewModel.kt` (신규)

```kotlin
class FixedTransactionViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val fixedTransactions: StateFlow<List<Transaction>> =
        transactionRepository.getAllFixed()
            .map { list ->
                // title+amount+category+asset 기준 최신 1개씩
                list.groupBy { "${it.title}|${it.amount}|${it.category}|${it.asset}" }
                    .values.map { it.first() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun stopRecurring(transaction: Transaction, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                transactionRepository.update(transaction.copy(isFixed = false))
            }.onSuccess { onSuccess() }
        }
    }
}
```

---

## Step 13: `FixedExpenseScreen.kt` 리팩토링

- `FixedExpenseViewModel` → `FixedTransactionViewModel`
- 아이템: title, `매월 {date.dayOfMonth}일 · {amount}`, asset
- 삭제 → "중단" 버튼 (stopRecurring 호출)
- "거래 유지/삭제" 다이얼로그 제거

---

## Step 14: `DataExportRepositoryImpl.kt`

```kotlin
// 생성자: FixedExpenseRepository 제거 (TransactionRepository만 사용)
class DataExportRepositoryImpl(private val transactionRepo: TransactionRepository)

// exportToExcel(): getAllFixed()로 is_fixed=true 거래 내보내기
// importFromExcel(): isFixed 필드 처리
```

---

## Step 15: `DbViewerRepositoryImpl.kt`

FixedExpenseEntity 뷰어 블록 제거.

---

## Step 16: DI 모듈

**`sharedModule.kt`:**
```kotlin
// 제거: single<FixedExpenseRepository> { ... }
// DataExportRepositoryImpl 생성자에서 fixedExpenseRepo 제거
single<DataExportRepository> { DataExportRepositoryImpl(get()) }
```

**`appModule.kt`:**
```kotlin
// viewModelOf(::FixedExpenseViewModel) → viewModelOf(::FixedTransactionViewModel)
```

---

## Step 17: `App.kt`

```kotlin
// import 변경: FixedExpenseScreen 유지 (파일 재사용)
// Screen.FixedExpenses 유지
// FixedExpenseScreen 내부가 FixedTransactionViewModel 사용하도록 변경됨
```

---

## Step 18: Edge Function `supabase/functions/auto-register-fixed/index.ts`

```typescript
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

Deno.serve(async () => {
  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
  )
  const now = new Date()
  const curYear  = now.getFullYear()
  const curMonth = now.getMonth() + 1
  const prevMonth = curMonth === 1 ? 12 : curMonth - 1
  const prevYear  = curMonth === 1 ? curYear - 1 : curYear
  const prevMonthStr = `${prevYear}-${String(prevMonth).padStart(2,'0')}`
  const curMonthStr  = `${curYear}-${String(curMonth).padStart(2,'0')}`

  const { data: fixedTxs } = await supabase
    .from('transactions').select('*').eq('is_fixed', true)
    .gte('date', `${prevMonthStr}-01`).lte('date', `${prevMonthStr}-31`)

  let created = 0
  for (const tx of fixedTxs ?? []) {
    const lastDay = new Date(curYear, curMonth, 0).getDate()
    const newDay  = Math.min(parseInt(tx.date.split('-')[2]), lastDay)
    const newDate = `${curMonthStr}-${String(newDay).padStart(2,'0')}`

    const { data: exists } = await supabase.from('transactions').select('id')
      .eq('book_id', tx.book_id).eq('title', tx.title).eq('amount', tx.amount)
      .eq('category', tx.category).eq('asset', tx.asset).eq('is_fixed', true)
      .gte('date', `${curMonthStr}-01`).lte('date', `${curMonthStr}-31`)

    if (exists && exists.length > 0) continue

    await supabase.from('transactions').insert({
      book_id: tx.book_id, title: tx.title, amount: tx.amount, type: tx.type,
      category: tx.category, category_emoji: tx.category_emoji,
      date: newDate, tx_time: tx.tx_time, note: tx.note,
      asset: tx.asset, to_asset: tx.to_asset, created_by: tx.created_by,
      is_fixed: true,
    })
    created++
  }
  return new Response(JSON.stringify({ processed: fixedTxs?.length ?? 0, created }),
    { headers: { 'Content-Type': 'application/json' } })
})
```

**Schedule:** Supabase Dashboard > Edge Functions > Schedule: `0 0 * * *` (매일 자정 UTC)

---

## 구현 순서

1. `13.sqm` 마이그레이션 작성
2. `Budget.sq` 스키마/쿼리 수정
3. `Transaction.kt` + `TransactionRemoteDto` 수정
4. `TransactionRepository.kt` + `TransactionRepositoryImpl.kt` 수정
5. FixedExpense 파일 3개 삭제
6. `BookRepositoryImpl.kt` 수정 (pullBookData/pushBookData)
7. `HomeViewModel.kt` 수정
8. `AddEditViewModel.kt` 수정
9. `AddEditScreen.kt` 수정
10. `FixedTransactionViewModel.kt` 신규 작성
11. `FixedExpenseScreen.kt` 리팩토링
12. `DataExportRepositoryImpl.kt` 수정
13. `DbViewerRepositoryImpl.kt` 수정
14. `sharedModule.kt` + `appModule.kt` 수정
15. `supabase_schema.sql` 수정
16. **빌드 확인**
17. Edge Function 파일 작성
18. 커밋 + 푸시

---

## 검증

1. `./gradlew :shared:generateCommonMainBudgetDatabaseInterface :shared:compileDebugKotlinAndroid :composeApp:compileDebugKotlinAndroid`
2. 거래 추가 시 "고정 거래" 토글 → Supabase `transactions.is_fixed = true` 확인
3. 고정 거래 목록 화면 표시 확인
4. "중단" 버튼 → `is_fixed = false` → 이후 Edge Function에서 미생성 확인
5. Supabase Dashboard > Edge Functions > `auto-register-fixed` 수동 실행 → 이번달 거래 생성 확인
6. `supabase_schema.sql` Supabase SQL Editor에서 실행 → `fixed_expenses` 테이블 없어지는지 확인
