import { createElement } from 'react'
import type { ListIcon as ListIconToken } from '../api/lists'
import { iconComponent } from './listIconTokens'

/**
 * Falls back rather than throwing for a token this build does not know. The
 * server validates against its own enum, so the two lists drifting shows up as
 * a generic icon instead of a blank screen.
 *
 * createElement rather than a capitalised local: the mapped components are
 * module-level constants, but binding one to a local inside render reads to a
 * linter - reasonably - as declaring a component during render.
 */
export function ListIcon({
  token,
  fontSize,
}: {
  token: ListIconToken
  fontSize?: 'small' | 'medium' | 'large'
}) {
  return createElement(iconComponent(token), { fontSize })
}
