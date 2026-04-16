-- ============================================================
-- Supabase 전체 초기화 + 재생성 스크립트
-- Supabase Dashboard > SQL Editor에서 실행하세요
-- ⚠️  모든 데이터가 삭제됩니다. 프로덕션에서는 신중히 실행하세요.
-- ============================================================

-- ── 1. 정책 삭제 ─────────────────────────────────────────────────────────────

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
DROP TABLE IF EXISTS transactions       CASCADE;
DROP TABLE IF EXISTS invite_codes       CASCADE;
DROP TABLE IF EXISTS book_members       CASCADE;
DROP TABLE IF EXISTS books              CASCADE;

-- ── 4. 테이블 생성 ───────────────────────────────────────────────────────────

-- 가계부
CREATE TABLE books (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL,
    color_hex  TEXT        NOT NULL DEFAULT '#A0522D',
    icon_emoji TEXT        NOT NULL DEFAULT '📒',
    owner_id   UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 가계부 멤버 (OWNER / EDITOR / VIEWER)
CREATE TABLE book_members (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id   UUID        NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role      TEXT        NOT NULL DEFAULT 'VIEWER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(book_id, user_id)
);

-- 초대 코드
CREATE TABLE invite_codes (
    code       TEXT        PRIMARY KEY,
    book_id    UUID        NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    created_by UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 거래 내역
CREATE TABLE transactions (
    id             UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id        UUID    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    title          TEXT    NOT NULL,
    amount         BIGINT  NOT NULL,
    type           TEXT    NOT NULL,                        -- INCOME | EXPENSE | TRANSFER
    category       TEXT    NOT NULL DEFAULT '',
    category_emoji TEXT    NOT NULL DEFAULT '',
    date           TEXT    NOT NULL,                        -- ISO-8601 (YYYY-MM-DD)
    tx_time        TEXT    NOT NULL DEFAULT '00:00:00',     -- HH:MM:SS
    note           TEXT    NOT NULL DEFAULT '',
    asset          TEXT    NOT NULL DEFAULT '',
    to_asset       TEXT    NOT NULL DEFAULT '',
    created_by     TEXT    NOT NULL DEFAULT '',
    is_fixed       BOOLEAN NOT NULL DEFAULT FALSE
);

-- 지출/수입 상위 카테고리
CREATE TABLE parent_categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id    UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    emoji      TEXT NOT NULL DEFAULT '',
    type       TEXT NOT NULL,           -- INCOME | EXPENSE
    sort_order INT  NOT NULL DEFAULT 0
);

-- 지출/수입 하위 카테고리
CREATE TABLE user_categories (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id          UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    emoji            TEXT NOT NULL DEFAULT '',
    parent_remote_id UUID REFERENCES parent_categories(id) ON DELETE CASCADE,
    type             TEXT NOT NULL,     -- INCOME | EXPENSE
    sort_order       INT  NOT NULL DEFAULT 0
);

-- 자산 그룹
CREATE TABLE asset_groups (
    id           UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id      UUID    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name         TEXT    NOT NULL,
    emoji        TEXT    NOT NULL DEFAULT '',
    grp_key      TEXT    NOT NULL,
    sort_order   INT     NOT NULL DEFAULT 0,
    is_liability BOOLEAN NOT NULL DEFAULT FALSE
);

-- 자산
CREATE TABLE assets (
    id              UUID   PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id         UUID   NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name            TEXT   NOT NULL,
    emoji           TEXT   NOT NULL DEFAULT '',
    owner           TEXT   NOT NULL DEFAULT '',
    initial_balance BIGINT NOT NULL DEFAULT 0,
    group_key       TEXT   NOT NULL,
    sort_order      INT    NOT NULL DEFAULT 0
);

-- ── 5. RLS 활성화 ────────────────────────────────────────────────────────────

ALTER TABLE books             ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_members      ENABLE ROW LEVEL SECURITY;
ALTER TABLE invite_codes      ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions      ENABLE ROW LEVEL SECURITY;
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

-- 멤버는 가계부 조회 가능
CREATE POLICY "books_member_read" ON books
    FOR SELECT
    USING (is_book_member(id));

-- 오너만 수정/삭제 가능
CREATE POLICY "books_owner_write" ON books
    FOR ALL
    USING (owner_id = auth.uid())
    WITH CHECK (owner_id = auth.uid());

-- ── 8. book_members 정책 ─────────────────────────────────────────────────────

-- 멤버는 같은 가계부의 멤버 목록 조회 가능
CREATE POLICY "book_members_read" ON book_members
    FOR SELECT
    USING (is_book_member(book_id));

-- 본인 자신만 멤버로 추가 가능 (초대 코드 수락)
CREATE POLICY "book_members_insert" ON book_members
    FOR INSERT
    WITH CHECK (user_id = auth.uid());

-- 본인 탈퇴 또는 오너가 강퇴
CREATE POLICY "book_members_delete" ON book_members
    FOR DELETE
    USING (
        user_id = auth.uid()
        OR EXISTS (SELECT 1 FROM books b WHERE b.id = book_members.book_id AND b.owner_id = auth.uid())
    );

-- ── 9. invite_codes 정책 ─────────────────────────────────────────────────────

-- 누구나 초대 코드 조회 가능 (코드를 아는 사람만 접근)
CREATE POLICY "invite_codes_read" ON invite_codes
    FOR SELECT USING (true);

-- 생성자 본인만 초대 코드 생성 가능
CREATE POLICY "invite_codes_insert" ON invite_codes
    FOR INSERT
    WITH CHECK (created_by = auth.uid());

-- 초대 코드 사용 처리 (used = true 업데이트)
CREATE POLICY "invite_codes_update" ON invite_codes
    FOR UPDATE USING (true);

-- ── 10. 자식 테이블 정책 ─────────────────────────────────────────────────────
-- 가계부 멤버이면 모든 CRUD 허용

CREATE POLICY "transactions_book_member" ON transactions
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

-- ── 11. RPC: 멤버 + 닉네임 조회 ─────────────────────────────────────────────
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
        COALESCE(au.raw_user_meta_data->>'display_name', au.email, bm.user_id::text)
    FROM book_members bm
    JOIN auth.users au ON au.id = bm.user_id
    WHERE bm.book_id = p_book_id;
$$;

-- ── 12. RPC: 내 가계부 목록 + 오너 닉네임 조회 ──────────────────────────────

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

-- ── 13. RPC: 가계부 생성 ─────────────────────────────────────────────────────
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
