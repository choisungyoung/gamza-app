/**
 * notify-kick: book_members DELETE 웹훅 → 강퇴된 사용자에게 FCM 푸시 알림 전송
 *
 * Supabase 환경 변수 (secrets):
 *   FIREBASE_PROJECT_ID    - Firebase 프로젝트 ID
 *   FIREBASE_CLIENT_EMAIL  - 서비스 계정 이메일
 *   FIREBASE_PRIVATE_KEY   - 서비스 계정 개인 키 (-----BEGIN PRIVATE KEY-----\n...)
 *
 * Database Webhook 설정:
 *   Table: book_members  /  Event: DELETE  /  URL: <함수 URL>
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import * as jose from 'https://esm.sh/jose@5.3.0'

const FIREBASE_PROJECT_ID = Deno.env.get('FIREBASE_PROJECT_ID')!
const FIREBASE_CLIENT_EMAIL = Deno.env.get('FIREBASE_CLIENT_EMAIL')!
const FIREBASE_PRIVATE_KEY = Deno.env.get('FIREBASE_PRIVATE_KEY')!

async function getFcmAccessToken(): Promise<string> {
  const privateKey = await jose.importPKCS8(
    FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
    'RS256',
  )
  const jwt = await new jose.SignJWT({
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
  })
    .setProtectedHeader({ alg: 'RS256' })
    .setIssuer(FIREBASE_CLIENT_EMAIL)
    .setAudience('https://oauth2.googleapis.com/token')
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(privateKey)

  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  })
  const { access_token } = await res.json()
  return access_token as string
}

Deno.serve(async (req) => {
  try {
    const body = await req.json()
    // Supabase Database Webhook sends { type, table, record, old_record, ... }
    const oldRecord = body.old_record ?? body.record
    const bookId: string = oldRecord?.book_id
    const userId: string = oldRecord?.user_id

    if (!bookId || !userId) {
      return new Response(JSON.stringify({ skipped: 'missing_fields' }), { status: 200 })
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
    )

    // 강퇴된 사용자의 FCM 토큰 조회
    const { data: tokenRow } = await supabase
      .from('user_fcm_tokens')
      .select('token')
      .eq('user_id', userId)
      .single()

    if (!tokenRow?.token) {
      return new Response(JSON.stringify({ skipped: 'no_fcm_token' }), { status: 200 })
    }

    // 가계부 이름 조회 (삭제된 책은 없을 수 있으므로 optional)
    const { data: bookRow } = await supabase
      .from('books')
      .select('name')
      .eq('id', bookId)
      .single()

    const bookName = bookRow?.name ?? '가계부'

    // FCM v1 API로 알림 전송
    const accessToken = await getFcmAccessToken()
    const fcmRes = await fetch(
      `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: {
            token: tokenRow.token,
            notification: {
              title: '가계부 접근 불가',
              body: `'${bookName}' 가계부에서 제외되었습니다.`,
            },
            data: {
              book_id: bookId,
              type: 'KICK',
            },
            android: {
              priority: 'HIGH',
              notification: { channel_id: 'kick_notifications' },
            },
          },
        }),
      },
    )

    const result = await fcmRes.json()
    return new Response(JSON.stringify(result), {
      status: fcmRes.ok ? 200 : 500,
      headers: { 'Content-Type': 'application/json' },
    })
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 })
  }
})
