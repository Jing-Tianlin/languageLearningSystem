export const LANG_NAMES = {
  en: '英语', ja: '日语', ko: '韩语', fr: '法语', de: '德语',
  es: '西班牙语', it: '意大利语', pt: '葡萄牙语', ru: '俄语', zh: '中文',
}
export const langName = (code) => LANG_NAMES[code] || code
