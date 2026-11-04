import { beforeEach, describe, expect, it } from 'vitest'
import { captureSharedLink, discard, park, parkedLinks } from './parkedLinks'

function params(values: Record<string, string>): URLSearchParams {
  return new URLSearchParams(values)
}

describe('captureSharedLink', () => {
  it('takes the URL from the dedicated parameter when there is one', () => {
    const link = captureSharedLink(
      params({ title: 'A recipe', url: 'https://example.com/pie' }),
    )

    expect(link).toMatchObject({
      title: 'A recipe',
      url: 'https://example.com/pie',
      note: null,
    })
  })

  it('recovers the URL from the text, which is where Chrome often puts it', () => {
    const link = captureSharedLink(params({ text: 'Lemon pie https://example.com/pie' }))

    expect(link).toMatchObject({ url: 'https://example.com/pie', note: 'Lemon pie' })
  })

  it('keeps a share that carried no URL at all rather than dropping it', () => {
    const link = captureSharedLink(params({ text: 'buy more flour' }))

    expect(link).toMatchObject({ url: null, note: 'buy more flour' })
  })

  it('is nothing when the share was empty', () => {
    expect(captureSharedLink(params({}))).toBeNull()
    expect(captureSharedLink(params({ title: '  ', text: '' }))).toBeNull()
  })
})

describe('the parked store', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('holds links per user, newest first', () => {
    const first = captureSharedLink(params({ url: 'https://example.com/one' }))!
    const second = captureSharedLink(params({ url: 'https://example.com/two' }))!

    park('user-1', first)
    park('user-1', second)

    expect(parkedLinks('user-1').map((link) => link.url)).toEqual([
      'https://example.com/two',
      'https://example.com/one',
    ])
    // Two members sharing a tablet do not see each other's.
    expect(parkedLinks('user-2')).toEqual([])
  })

  it('discards one link and leaves the rest', () => {
    const link = captureSharedLink(params({ url: 'https://example.com/one' }))!
    park('user-1', link)

    expect(discard('user-1', link.id)).toEqual([])
    expect(parkedLinks('user-1')).toEqual([])
  })

  it('survives a corrupt store instead of taking the screen down', () => {
    window.localStorage.setItem('list.parkedLinks.user-1', 'not json')

    expect(parkedLinks('user-1')).toEqual([])
  })
})
