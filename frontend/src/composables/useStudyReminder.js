/**
 * useStudyReminder.js — 每日学习提醒（PWA 通知）
 *
 * - 注册 Service Worker（生产环境生效）
 * - 读取 localStorage 中的提醒时间（studyReminderTime: "HH:MM"）
 * - 有提醒时间时通过 SW 调度通知；SW 不可用时页面内兜底轮询
 */
const KEY = 'studyReminderTime'

export function getReminderTime() {
  return localStorage.getItem(KEY) || ''
}

export function setReminderTime(hhmm) {
  if (hhmm) localStorage.setItem(KEY, hhmm)
  else localStorage.removeItem(KEY)
  schedule()
}

export function isReminderEnabled() {
  return !!getReminderTime()
}

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
}

let lastFiredDay = ''
let pollTimer = null

async function notifyFallback() {
  if (!('Notification' in window)) return
  if (Notification.permission !== 'granted') return
  // 避免一天重复提醒
  if (lastFiredDay === todayStr()) return
  lastFiredDay = todayStr()
  try {
    const reg = await navigator.serviceWorker?.getRegistration()
    if (reg) {
      await reg.showNotification('该学习啦', {
        body: '今日学习目标还未完成，来打卡吧',
        icon: '/favicon.ico',
        badge: '/favicon.ico',
        tag: 'daily-study-reminder',
      })
    } else {
      new Notification('该学习啦', {
        body: '今日学习目标还未完成，来打卡吧',
        icon: '/favicon.ico',
      })
    }
  } catch (e) { /* 静默 */ }
}

function checkNow() {
  const t = getReminderTime()
  if (!t) return
  const [h, m] = t.split(':').map(Number)
  const now = new Date()
  if (now.getHours() === h && now.getMinutes() === m) notifyFallback()
}

function schedule() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (!isReminderEnabled()) return
  pollTimer = setInterval(checkNow, 60 * 1000)
  checkNow()
}

export function initStudyReminder() {
  if (typeof window === 'undefined') return

  // 注册 Service Worker（仅生产环境，避免开发期缓存干扰）
  if ('serviceWorker' in navigator && import.meta.env.PROD) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => {})
    })
  }
  schedule()
}
