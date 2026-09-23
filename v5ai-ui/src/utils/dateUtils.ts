/**
 * 日期工具：时间格式化。
 *
 * 支持 Date / 毫秒时间戳 / 秒级时间戳 / 时间字符串（含 ISO、`-`、`/` 分隔）。
 * 格式占位符（与 RuoYi 系 parseTime 一致）：
 *   {y} 年、{m} 月、{d} 日、{h} 时、{i} 分、{s} 秒、{a} 星期
 * 例如：'{y}-{m}-{d} {h}:{i}:{s}'、'{y}/{m}/{d}'、'{y}年{m}月{d}日 {h}时{i}分{s}秒'
 */

export type DateInput = string | number | Date | null | undefined

/**
 * 按 pattern 格式化时间；time 为空时返回 null。
 */
export function parseTime<T = string | null>(time: DateInput, pattern?: string): T {
  if (time === '' || time === null || time === undefined) {
    return null as T
  }
  let value: string | number | Date = time
  if (value instanceof Date) {
    value = value.getTime()
  }
  if (typeof value === 'string' && !isNaN(Number(value))) {
    value = Number(value)
  }
  if (typeof value === 'string') {
    // 仅对 "yyyy-MM-dd HH:mm:ss" 这类空格分隔、无时区的格式做 - → / 兼容替换
    // （iOS 对带连字符的空格分隔日期解析不可靠）；带 T/时区的 ISO-8601
    // （如 OffsetDateTime 序列化的 "2026-08-30T15:34:49.123+08:00"）直接交给 new Date 原生解析。
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(value)) {
      value = value.replace(/-/g, '/')
    }
  }
  if (typeof value === 'number' && value.toString().length === 10) {
    value = value * 1000 // 秒级时间戳转毫秒
  }
  const format = pattern || '{y}-{m}-{d} {h}:{i}:{s}'
  const date = new Date(value)
  const formatObj: Record<string, number> = {
    y: date.getFullYear(),
    m: date.getMonth() + 1,
    d: date.getDate(),
    h: date.getHours(),
    i: date.getMinutes(),
    s: date.getSeconds(),
    a: date.getDay()
  }
  return format.replace(/{([ymdhisa])+}/g, (result, key: string) => {
    const formatValue = formatObj[key]
    if (key === 'a') {
      return ['日', '一', '二', '三', '四', '五', '六'][formatValue]
    }
    return formatValue.toString().padStart(2, '0')
  }) as T
}

/**
 * 完整时间：yyyy-MM-dd HH:mm:ss（列表创建/更新时间等常用格式）。
 */
export function formatDateTime(time: DateInput): string | null {
  return parseTime(time, '{y}-{m}-{d} {h}:{i}:{s}')
}

/**
 * 日期：yyyy-MM-dd。
 */
export function formatDate(time: DateInput): string | null {
  return parseTime(time, '{y}-{m}-{d}')
}
