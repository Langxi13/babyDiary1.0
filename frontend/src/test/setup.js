import { config } from '@vue/test-utils'
import { afterEach } from 'vitest'

config.global.renderStubDefaultSlot = true

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

class IntersectionObserverStub extends ResizeObserverStub {}

globalThis.ResizeObserver = ResizeObserverStub
globalThis.IntersectionObserver = IntersectionObserverStub
window.matchMedia = window.matchMedia || (() => ({
  matches: false,
  addEventListener() {},
  removeEventListener() {},
  addListener() {},
  removeListener() {}
}))
Element.prototype.scrollIntoView = Element.prototype.scrollIntoView || (() => {})

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  document.body.innerHTML = ''
})
