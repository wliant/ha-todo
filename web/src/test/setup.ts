import '@testing-library/jest-dom/vitest'

/**
 * MUI's useMediaQuery needs window.matchMedia and jsdom does not provide it.
 *
 * Defaults to "does not match", so components render their phone layout unless
 * a test says otherwise - the narrower case, and the one with behaviour worth
 * asserting. Override per test by replacing this implementation.
 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }),
})
