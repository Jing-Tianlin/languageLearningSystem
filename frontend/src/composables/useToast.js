import { ref } from 'vue'

const toasts = ref([])
let idCounter = 0

export function useToast() {
  function show(message, type = 'info', duration = 2500) {
    const id = ++idCounter
    const toast = { id, message, type, show: true }
    toasts.value.push(toast)
    setTimeout(() => remove(id), duration)
    return id
  }

  function remove(id) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx > -1) {
      toasts.value[idx].show = false
      setTimeout(() => {
        toasts.value = toasts.value.filter(t => t.id !== id)
      }, 300)
    }
  }

  function success(message, duration) { return show(message, 'success', duration) }
  function error(message, duration) { return show(message, 'error', duration) }
  function warning(message, duration) { return show(message, 'warning', duration) }
  function info(message, duration) { return show(message, 'info', duration) }

  return { toasts, show, success, error, warning, info, remove }
}

// 全局单例，用于非 setup 上下文
const globalToast = useToast()
export const toast = {
  show: globalToast.show,
  success: globalToast.success,
  error: globalToast.error,
  warning: globalToast.warning,
  info: globalToast.info,
}
