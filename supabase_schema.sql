-- ============================================================
-- Supabase 전체 초기화 + 재생성 스크립트
-- Supabase Dashboard > SQL Editor에서 실행하세요
-- ⚠️  모든 데이터가 삭제됩니다. 프로덕션에서는 신중히 실행하세요.
-- ============================================================

-- ── 1. 정책 전체 삭제 ────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "books_member_read"             ON books;
DROP POLICY IF EXISTS "books_owner_write"             ON books;
DROP POLICY IF EXISTS "book_members_read"             ON book_members;
DROP POLICY IF EXISTS "book_members_select"           ON book_members;
DROP POLICY IF EXISTS "book_members_insert"           ON book_members;
DROP POLICY IF EXISTS "book_members_delete"           ON book_members;
DROP POLICY IF EXISTS "invite_codes_read"             ON invite_codes;
DROP POLICY IF EXISTS "invite_codes_select"           ON invite_codes;
DROP POLICY IF EXISTS "invite_codes_insert"           ON invite_codes;
DROP POLICY IF EXISTS "invite_codes_update"           ON invite_codes;
DROP POLICY IF EXISTS "transactions_book_member"      ON transactions;
DROP POLICY IF EXISTS "fixed_expenses_book_member"    ON fixed_expenses;
DROP POLICY IF EXISTS "parent_categories_book_member" ON parent_categories;
DROP POLICY IF EXISTS "user_categories_book_member"   ON user_categories;
DROP POLICY IF EXISTS "asset_groups_book_member"      ON asset_groups;
DROP POLICY IF EXISTS "assets_book_member"            ON assets;

-- ── 2. 함수 삭제 ─────────────────────────────────────────────────────────────

DROP FUNCTION IF EXISTS create_book(text, text, text) CASCADE;
DROP FUNCTION IF EXISTS get_members_with_names(uuid) CASCADE;
DROP FUNCTION IF EXISTS get_my_books_with_owner_names() CASCADE;
DROP FUNCTION IF EXISTS is_book_member(uuid) CASCADE;

-- ── 3. 테이블 삭제 (자식 → 부모 순서) ───────────────────────────────────────

DROP TABLE IF EXISTS assets             CASCADE;
DROP TABLE IF EXISTS asset_groups       CASCADE;
DROP TABLE IF EXISTS user_categories    CASCADE;
DROP TABLE IF EXISTS parent_categories  CASCADE;
DROP TABLE IF EXISTS fixed_expenses     CASCADE;
DROP TABLE IF EXISTS transactions       CASCADE;
DROP TABLE IF EXISTS invite_codes       CASCADE;
DROP TABLE IF EXISTS book_members       CASCADE;
DROP TABLE IF EXISTS books              CASCADE;

-- ── 4. 테이블 생성 ───────────────────────────────────────────────────────────

CREATE TABLE books (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    color_hex  TEXT NOT NULL DEFAULT '#A0522D',
    icon_emoji TEXT NOT NULL DEFAULT '📒',
    owner_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE book_members (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id   UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role      TEXT NOT NULL DEFAULT 'VIEWER',
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(book_id, user_id)
);

CREATE TABLE invite_codes (
    code       TEXT PRIMARY KEY,
    book_id    UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    created_by UUID NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE fixed_expenses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id      UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    title        TEXT NOT NULL,
    amount       BIGINT NOT NULL,
    category     TEXT NOT NULL,
    asset        TEXT NOT NULL DEFAULT '',
    day_of_month INT NOT NULL DEFAULT 1,
    start_year   INT NOT NULL,
    start_month  INT NOT NULL,
    note         TEXT NOT NULL DEFAULT '',
    is_active    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id          UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    title            TEXT NOT NULL,
    amount           BIGINT NOT NULL,
    type             TEXT NOT NULL,
    category         TEXT NOT NULL,
    date             TEXT NOT NULL,
    tx_time          TEXT NOT NULL DEFAULT '00:00:00',
    note             TEXT NOT NULL DEFAULT '',
    asset            TEXT NOT NULL DEFAULT '',
    to_asset         TEXT NOT NULL DEFAULT '',
    category_emoji   TEXT NOT NULL DEFAULT '',
    created_by       TEXT NOT NULL DEFAULT '',
    fixed_expense_id UUID REFERENCES fixed_expenses(id) ON DELETE SET NULL,
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE parent_categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id    UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    emoji      TEXT NOT NULL DEFAULT '',
    type       TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE user_categories (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id          UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    emoji            TEXT NOT NULL DEFAULT '',
    parent_remote_id UUID REFERENCES parent_categories(id) ON DELETE CASCADE,
    type             TEXT NOT NULL,
    sort_order       INT NOT NULL DEFAULT 0
);

CREATE TABLE asset_groups (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id      UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    emoji        TEXT NOT NULL DEFAULT '',
    grp_key      TEXT NOT NULL,
    sort_order   INT NOT NULL DEFAULT 0,
    is_liability BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id         UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    emoji           TEXT NOT NULL DEFAULT '',
    owner           TEXT NOT NULL DEFAULT '',
    initial_balance BIGINT NOT NULL DEFAULT 0,
    group_key       TEXT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0
);

-- ── 5. RLS 활성화 ────────────────────────────────────────────────────────────

ALTER TABLE books             ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_members      ENABLE ROW LEVEL SECURITY;
ALTER TABLE invite_codes      ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions      ENABLE ROW LEVEL SECURITY;
ALTER TABLE fixed_expenses    ENABLE ROW LEVEL SECURITY;
ALTER TABLE parent_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_categories   ENABLE ROW LEVEL SECURITY;
ALTER TABLE asset_groups      ENABLE ROW LEVEL SECURITY;
ALTER TABLE assets            ENABLE ROW LEVEL SECURITY;

-- ── 6. 헬퍼 함수: is_book_member ─────────────────────────────────────────────
-- SECURITY DEFINER로 RLS 없이 직접 조회 → book_members 정책 재귀 방지

CREATE OR REPLACE FUNCTION is_book_member(p_book_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM book_members
        WHERE book_id = p_book_id AND user_id = auth.uid()
    );
$$;

-- ── 7. books 정책 ────────────────────────────────────────────────────────────

CREATE POLICY "books_member_read" ON books
    FOR SELECT
    USING (is_book_member(id));

CREATE POLICY "books_owner_write" ON books
    FOR ALL
    USING (owner_id = auth.uid())
    WITH CHECK (owner_id = auth.uid());

-- ── 8. book_members 정책 ─────────────────────────────────────────────────────
-- SELECT: is_book_member() 함수로 재귀 없이 멤버십 확인

CREATE POLICY "book_members_read" ON book_members
    FOR SELECT
    USING (is_book_member(book_id));

CREATE POLICY "book_members_insert" ON book_members
    FOR INSERT
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "book_members_delete" ON book_members
    FOR DELETE
    USING (
        user_id = auth.uid()
        OR EXISTS (SELECT 1 FROM books b WHERE b.id = book_members.book_id AND b.owner_id = auth.uid())
    );

-- ── 9. invite_codes 정책 ─────────────────────────────────────────────────────

CREATE POLICY "invite_codes_read" ON invite_codes
    FOR SELECT USING (true);

CREATE POLICY "invite_codes_insert" ON invite_codes
    FOR INSERT
    WITH CHECK (created_by = auth.uid());

CREATE POLICY "invite_codes_update" ON invite_codes
    FOR UPDATE USING (true);

-- ── 10. 자식 테이블 정책 (is_book_member 함수 사용) ──────────────────────────

CREATE POLICY "transactions_book_member" ON transactions
    FOR ALL
    USING (is_book_member(book_id))
    WITH CHECK (is_book_member(book_id));

CREATE POLICY "fixed_expenses_book_member" ON fixed_expenses
    FOR ALL
    USING (is_book_member(book_id))
    WITH CHECK (is_book_member(book_id));

CREATE POLICY "parent_categories_book_member" ON parent_categories
    FOR ALL
    USING (is_book_member(book_id))
    WITH CHECK (is_book_member(book_id));

CREATE POLICY "user_categories_book_member" ON user_categories
    FOR ALL
    USING (is_book_member(book_id))
    WITH CHECK (is_book_member(book_id));

CREATE POLICY "asset_groups_book_member" ON asset_groups
    FOR ALL
    USING (is_book_member(book_id))
    WITH CHECK (is_book_member(book_id));

CREATE POLICY "assets_book_member" ON assets
    FOR ALL
    USING (is_book_member(book_id))
    WITH CHECK (is_book_member(book_id));

-- ── 11. 멤버 + 닉네임 조회 함수 ─────────────────────────────────────────────
-- SECURITY DEFINER로 auth.users에서 display_name 읽기

CREATE OR REPLACE FUNCTION get_members_with_names(p_book_id UUID)
RETURNS TABLE(
    id UUID, book_id UUID, user_id UUID, role TEXT,
    joined_at TIMESTAMPTZ, display_name TEXT
)
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT bm.id, bm.book_id, bm.user_id, bm.role, bm.joined_at,
        COALESCE(au.raw_user_meta_data->>'display_name', au.email, bm.user_id::text) AS display_name
    FROM book_members bm
    JOIN auth.users au ON au.id = bm.user_id
    WHERE bm.book_id = p_book_id;
$$;

-- 현재 유저가 속한 모든 가계부의 오너 닉네임 조회

CREATE OR REPLACE FUNCTION get_my_books_with_owner_names()
RETURNS TABLE(book_id UUID, owner_display_name TEXT)
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT b.id,
        COALESCE(au.raw_user_meta_data->>'display_name', au.email, b.owner_id::text)
    FROM books b
    JOIN book_members bm ON bm.book_id = b.id AND bm.user_id = auth.uid()
    JOIN auth.users au ON au.id = b.owner_id;
$$;

-- ── 12. create_book 함수 ─────────────────────────────────────────────────────
-- SECURITY DEFINER: books INSERT + book_members OWNER 등록을 원자적으로 처리

CREATE OR REPLACE FUNCTION create_book(p_name TEXT, p_color_hex TEXT, p_icon_emoji TEXT)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_book_id UUID;
    v_result  JSON;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    INSERT INTO books (name, color_hex, icon_emoji, owner_id)
    VALUES (p_name, p_color_hex, p_icon_emoji, v_user_id)
    RETURNING id INTO v_book_id;

    INSERT INTO book_members (book_id, user_id, role, joined_at)
    VALUES (v_book_id, v_user_id, 'OWNER', NOW());

    SELECT json_build_object(
        'id',         b.id,
        'name',       b.name,
        'color_hex',  b.color_hex,
        'icon_emoji', b.icon_emoji,
        'owner_id',   b.owner_id
    ) INTO v_result
    FROM books b WHERE b.id = v_book_id;

    RETURN v_result;
END;
$$;
