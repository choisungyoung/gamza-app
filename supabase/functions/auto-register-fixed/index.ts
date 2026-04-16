import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

Deno.serve(async () => {
  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
  )

  const now = new Date()
  const curYear  = now.getFullYear()
  const curMonth = now.getMonth() + 1
  const curMonthStr = `${curYear}-${String(curMonth).padStart(2, '0')}`

  // 전달 고정거래 조회
  const prevMonth   = curMonth === 1 ? 12 : curMonth - 1
  const prevYear    = curMonth === 1 ? curYear - 1 : curYear
  const prevMonthStr = `${prevYear}-${String(prevMonth).padStart(2, '0')}`

  const { data: fixedTxs, error } = await supabase
    .from('transactions')
    .select('*')
    .eq('is_fixed', true)
    .gte('date', `${prevMonthStr}-01`)
    .lte('date', `${prevMonthStr}-31`)

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    })
  }

  // 이번달 이미 생성된 고정거래 조합 수집 (중복 방지)
  const { data: existingTxs } = await supabase
    .from('transactions')
    .select('book_id, title, amount, category, asset')
    .eq('is_fixed', true)
    .gte('date', `${curMonthStr}-01`)
    .lte('date', `${curMonthStr}-31`)

  const existingKeys = new Set(
    (existingTxs ?? []).map(t => `${t.book_id}|${t.title}|${t.amount}|${t.category}|${t.asset}`)
  )

  let created = 0
  for (const tx of fixedTxs ?? []) {
    const key = `${tx.book_id}|${tx.title}|${tx.amount}|${tx.category}|${tx.asset}`
    if (existingKeys.has(key)) continue

    const lastDay   = new Date(curYear, curMonth, 0).getDate()
    const origDay   = parseInt(tx.date.split('-')[2])
    const targetDay = Math.min(origDay, lastDay)
    const newDate   = `${curMonthStr}-${String(targetDay).padStart(2, '0')}`

    await supabase.from('transactions').insert({
      book_id:        tx.book_id,
      title:          tx.title,
      amount:         tx.amount,
      type:           tx.type,
      category:       tx.category,
      category_emoji: tx.category_emoji,
      date:           newDate,
      tx_time:        tx.tx_time,
      note:           tx.note,
      asset:          tx.asset,
      to_asset:       tx.to_asset,
      created_by:     tx.created_by,
      is_fixed:       true,
    })
    existingKeys.add(key)
    created++
  }

  return new Response(
    JSON.stringify({ processed: fixedTxs?.length ?? 0, created }),
    { headers: { 'Content-Type': 'application/json' } }
  )
})
