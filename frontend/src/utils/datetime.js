/**
 * 时间工具 — 后端的时间字段是「本地（中国时区）墙钟时间」字符串，
 * 形如 yyyy-MM-ddTHH:mm:ss，不带时区信息。
 *
 * ⚠️ 不要用 Date#toISOString()。它按 UTC 输出，在中国时区下会让写入后端的
 * 时间整体偏差 8 小时（例如北京时间 09:00 会写成 01:00）。
 */

function pad(n) {
  return String(n).padStart(2, '0')
}

/** Date → 'YYYY-MM-DDTHH:mm:ss'（本地时区） */
export function toLocalDateTime(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    + `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** Date → 'YYYY-MM-DD'（本地时区），用于导出文件名等只取日期的场景 */
export function toLocalDate(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 当天的起止时刻，用于「现场借用」这类按天计算的场景 */
export function todayRange(d = new Date()) {
  const start = new Date(d)
  start.setHours(0, 0, 0, 0)
  const end = new Date(d)
  end.setHours(23, 59, 59, 0)
  return { start: toLocalDateTime(start), end: toLocalDateTime(end) }
}
