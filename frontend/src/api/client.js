import { rawClient } from './http'

/**
 * 统一 axios 风格客户端：code === 200 时直接返回 data，否则 reject。
 * 供 src/api/* 模块使用；页面级请求请使用 fetchJson。
 */
function unwrap(promise) {
  return promise.then((env) => {
    if (env && env.code === 200) {
      return env.data || true
    }
    throw new Error((env && env.message) || '请求失败')
  })
}

export default {
  get(url, config) {
    return unwrap(rawClient.get(url, config))
  },
  post(url, data, config) {
    return unwrap(rawClient.post(url, data, config))
  },
  put(url, data, config) {
    return unwrap(rawClient.put(url, data, config))
  },
  delete(url, config) {
    return unwrap(rawClient.delete(url, config))
  },
}
