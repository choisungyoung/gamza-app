-- ============================================================
-- pg_cron 설정 파일
-- Supabase Dashboard > SQL Editor에서 필요한 쿼리를 실행하세요
-- ============================================================

-- ── 1. 확장 활성화 (최초 1회만) ─────────────────────────────

create extension if not exists pg_cron;
create extension if not exists pg_net;

-- ── 2. cron job 등록 ────────────────────────────────────────
-- 매월 1일 00:00 UTC에 auto-register-fixed Edge Function 호출

select cron.schedule(
  'auto-register-fixed-monthly',
  '0 0 1 * *',
  $$
    select net.http_post(
      url     := 'https://pwolqtdutnrjaqalnzna.supabase.co/functions/v1/auto-register-fixed',
      headers := '{"Authorization": "Bearer sb_publishable_8ZrMpsZMjeDoMgVIfWiTjw_L-QeAwJd", "Content-Type": "application/json"}'::jsonb,
      body    := '{}'::jsonb
    );
  $$
);

-- ── 3. 등록된 job 목록 확인 ─────────────────────────────────

select * from cron.job;

-- ── 4. job 실행 이력 확인 ───────────────────────────────────

select * from cron.job_run_details order by start_time desc limit 20;

-- ── 5. job 삭제 ─────────────────────────────────────────────

select cron.unschedule('auto-register-fixed-monthly');

-- ── 6. 수동 테스트 실행 ─────────────────────────────────────
-- Edge Function을 즉시 한 번 호출 (실제 스케줄 없이 테스트용)

select net.http_post(
  url     := 'https://pwolqtdutnrjaqalnzna.supabase.co/functions/v1/auto-register-fixed',
  headers := '{"Authorization": "Bearer sb_publishable_8ZrMpsZMjeDoMgVIfWiTjw_L-QeAwJd", "Content-Type": "application/json"}'::jsonb,
  body    := '{}'::jsonb
);
