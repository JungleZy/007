const decimal = /^(?:\d+(?:\.\d*)?|\.\d+)$/

export function settingMilliseconds(value) {
  if ((typeof value !== 'number' && typeof value !== 'string') || !decimal.test(String(value).trim())) {
    throw new Error('配置毫秒值必须为有限的非负数')
  }
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) throw new Error('配置毫秒值必须为有限的非负数')
  return number
}

// 保留接口中的 key/value 区间模型；两类练习使用同一份完整性校验。
export function parseTelegramBasicSettings(data) {
  if (!Array.isArray(data) || !data.length) throw new Error('基础练习配置为空，请先保存完整配置')
  const rows = data.map(item => {
    if (!item || !['0', '1'].includes(String(item.key)) || item.type !== 0) throw new Error('基础练习配置类型无效')
    const value = JSON.parse(item.value)
    if (!value || !Number.isInteger(value.type) || value.type < 0 || typeof value.name !== 'string' || !value.name.trim() || typeof value.msg !== 'string' || !value.msg.trim()) {
      throw new Error('基础练习分级名称、文案或类型无效')
    }
    if (value.type === 0) {
      if (typeof value.min !== 'string' || !value.min.startsWith('<') || typeof value.max !== 'string' || !value.max.startsWith('>')) throw new Error('基础练习缺少合法异常区间')
      settingMilliseconds(value.min.slice(1))
      settingMilliseconds(value.max.slice(1))
    } else {
      value.min = settingMilliseconds(value.min)
      value.max = settingMilliseconds(value.max)
      if (value.max <= value.min) throw new Error('基础练习区间上界必须大于下界')
    }
    return { ...item, key: String(item.key), value }
  })
  const groups = ['0', '1'].map(key => rows.filter(item => item.key === key))
  for (const group of groups) {
    if (new Set(group.map(item => item.value.type)).size !== group.length) throw new Error('基础练习存在重复分级')
    const abnormal = group.find(item => item.value.type === 0)
    const positive = group.filter(item => item.value.type > 0)
    if (!abnormal || !positive.length) throw new Error('点和划均须包含异常区间及正区间')
    const min = Math.min(...positive.map(item => item.value.min))
    const max = Math.max(...positive.map(item => item.value.max))
    if (settingMilliseconds(abnormal.value.min.slice(1)) !== min || settingMilliseconds(abnormal.value.max.slice(1)) !== max) throw new Error('异常区间必须覆盖正区间的外边界')
  }
  if (groups[0].length !== groups[1].length || groups[0].some(item => !groups[1].some(other => other.value.type === item.value.type))) throw new Error('点和划的分级必须完整对应')
  return rows
}
