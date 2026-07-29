export function useMousePosition() {
  let x = 0
  let y = 0

  function onMove(e) {
    x = e.clientX
    y = e.clientY
  }

  function get() {
    return { x, y }
  }

  return { onMove, get }
}
