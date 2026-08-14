import { rawClient } from './http'

/**
 * 统一请求封装：返回后端 {code, message, data} 信封对象（任何 HTTP 状态码都会 resolve，
 * 与原生 fetch 的语义一致，页面可继续用 j.code === 200 判断）。
 *
 * body 为对象时自动 JSON 序列化；字符串按 JSON 原样发送。
 */
export default function fetchJson(url, options = {}) {
  const config = {
    method: (options.method || 'GET').toUpperCase(),
    url,
    headers: { ...(options.headers || {}) },
  }
  if (options.body != null) {
    if (typeof options.body === 'string') {
      config.data = options.body
      config.headers['Content-Type'] = 'application/json'
    } else {
      config.data = options.body
    }
  }
  return rawClient.request(config)
}
