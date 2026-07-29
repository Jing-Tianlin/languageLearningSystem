import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const isNavbarCollapsed = ref(false)
  const mousePosition = ref({ x: 0, y: 0 })

  function updateMousePosition(x, y) {
    mousePosition.value = { x, y }
  }

  return { isNavbarCollapsed, mousePosition, updateMousePosition }
})
