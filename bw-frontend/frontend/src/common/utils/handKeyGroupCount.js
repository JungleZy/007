// 旧结算曾只保存多组数。仅凭全页保存的封顶前依据恢复数量，不能以最终扣分反推。
export function handKeyGroupCount(summary, rule, resolvers, pageCount) {
  const saved = summary?.quantoGroupNumber
  const charged = Number(summary?.quantoGroup)
  if (Number(saved) > 0 || !(charged > 0)) return saved ?? '--'
  const perGroup = Number(rule?.other?.quantoGroup?.l)
  const cap = Number(rule?.other?.quantoGroup?.max)
  if (!(perGroup > 0) || !Number.isInteger(pageCount) || pageCount < 1) return '--'
  let raw = 0
  for (let page = 0; page < pageCount; page++) {
    const evidence = resolvers?.[page]?.deductionEvidence
    if (!Array.isArray(evidence)) return '--'
    for (const item of evidence) {
      const amount = Number(item.deductions?.quantoGroup ?? 0)
      if (!Number.isFinite(amount) || amount < 0 || amount % perGroup !== 0) return '--'
      raw += amount
    }
  }
  if (raw <= 0 || Math.min(raw, cap) !== charged) return '--'
  return raw / perGroup
}
