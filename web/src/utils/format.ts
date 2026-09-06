// 时间与价格展示（后端已按 Asia/Shanghai 返回 +08:00 的 ISO 字符串）。

export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${month}月${day}日 ${hh}:${mm}`
}

export function formatPrice(price: string | number): string {
  const n = Number(price)
  if (Number.isNaN(n)) return String(price)
  return `¥${n.toFixed(2)}`
}
