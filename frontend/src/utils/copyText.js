export async function copyText(value, preferredInput = null) {
  const text = String(value ?? '')
  if (typeof document !== 'undefined') {
    const input = preferredInput || document.createElement('textarea')
    const temporary = !preferredInput
    if (temporary) {
      input.value = text
      input.setAttribute('readonly', '')
      input.style.position = 'fixed'
      input.style.opacity = '0'
      document.body.appendChild(input)
    }
    input.focus()
    input.select()
    input.setSelectionRange?.(0, input.value.length)
    try {
      if (document.execCommand('copy')) return true
    } catch {
      // Clipboard API below remains available in browsers that block execCommand.
    } finally {
      if (temporary) input.remove()
    }
  }
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return true
  }
  return false
}
