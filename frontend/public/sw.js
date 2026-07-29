/* sw.js — PWA Service Worker：应用外壳离线缓存 + 每日学习提醒通知 */
const CACHE_NAME = 'amazing-teaching-v1'
const APP_SHELL = ['/']

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).catch(() => {})
  )
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api')) return

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached
      return fetch(request).then((response) => {
        const clone = response.clone()
        caches.open(CACHE_NAME).then((cache) => cache.put(request, clone)).catch(() => {})
        return response
      }).catch(() => cached || caches.match('/'))
    })
  )
})

/* 每日学习提醒：页面通过 postMessage 告知提醒时间，到点触发通知 */
self.addEventListener('message', (event) => {
  const data = event.data || {}
  if (data.type === 'SCHEDULE_REMINDER') {
    scheduleReminder(data.remindAt, data.title, data.body)
  } else if (data.type === 'CLEAR_REMINDER') {
    clearScheduledReminder()
  }
})

let reminderTimer = null

function scheduleReminder(remindAt, title, body) {
  clearScheduledReminder()
  const delay = remindAt - Date.now()
  if (delay <= 0) return
  reminderTimer = setTimeout(() => {
    self.registration.showNotification(title || '该学习啦', {
      body: body || '今日学习目标还未完成，快来打卡吧',
      icon: '/favicon.ico',
      badge: '/favicon.ico',
      tag: 'daily-study-reminder',
      renotify: true,
    })
  }, delay)
}

function clearScheduledReminder() {
  if (reminderTimer) {
    clearTimeout(reminderTimer)
    reminderTimer = null
  }
}

/* 点击通知回到应用 */
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    for (const client of list) {
      if ('focus' in client) return client.focus()
    }
    return self.clients.openWindow('/')
  }))
})
