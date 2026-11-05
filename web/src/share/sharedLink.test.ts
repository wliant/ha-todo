import { describe, expect, it } from 'vitest'
import { captureSharedLink, describeSharedLink } from './sharedLink'

function params(values: Record<string, string>): URLSearchParams {
  return new URLSearchParams(values)
}

describe('captureSharedLink', () => {
  it('takes the url when the share sheet sends one', () => {
    expect(captureSharedLink(params({ url: 'https://example.com', title: 'A recipe' }))).toEqual({
      url: 'https://example.com',
      title: 'A recipe',
      note: null,
    })
  })

  it('recovers the url from the text, which is where Chrome usually puts it', () => {
    expect(
      captureSharedLink(params({ text: 'Look at this https://example.com/pie' })),
    ).toEqual({
      url: 'https://example.com/pie',
      title: null,
      note: 'Look at this',
    })
  })

  it('keeps text that carries no url at all', () => {
    expect(captureSharedLink(params({ text: 'milk, eggs, flour' }))).toEqual({
      url: null,
      title: null,
      note: 'milk, eggs, flour',
    })
  })

  it('is nothing when the share carried nothing usable', () => {
    expect(captureSharedLink(params({}))).toBeNull()
    expect(captureSharedLink(params({ text: '   ' }))).toBeNull()
  })
})

describe('describeSharedLink', () => {
  it('names the item after the title and the link', () => {
    expect(
      describeSharedLink({ url: 'https://example.com', title: 'A recipe', note: null }),
    ).toBe('A recipe - https://example.com')
  })

  it('falls back to the text when there is no link', () => {
    expect(describeSharedLink({ url: null, title: null, note: 'milk' })).toBe('milk')
  })
})
