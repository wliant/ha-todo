#!/usr/bin/env node
/**
 * Draws the app icons into public/icons/.
 *
 * Hand-rolled rather than pulled from a design tool so that every size - and
 * especially the maskable variant, whose artwork has to sit inside Android's
 * safe zone - is derived from the same geometry and cannot drift. Writes PNGs
 * with Node's built-in zlib and nothing else, so `pnpm icons` needs no
 * dependency and no install step in CI.
 *
 * Re-run after changing BRAND or the geometry, and commit the output:
 *   node scripts/generate-icons.mjs
 *
 * See docs/adr/0007-installable-pwa-without-offline-caching.md.
 */
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const BRAND = [0x1f, 0x5c, 0x3f, 0xff] // deep green, the app's theme colour
const INK = [0xff, 0xff, 0xff, 0xff]

const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons')

/** The checkmark, in a unit square, as a stroked polyline. */
const CHECK = {
  points: [
    [0.27, 0.52],
    [0.43, 0.69],
    [0.75, 0.32],
  ],
  width: 0.115,
}

const SAMPLES = 4 // per axis; 16 samples a pixel is plenty for two shapes

function distanceToSegment(px, py, [ax, ay], [bx, by]) {
  const dx = bx - ax
  const dy = by - ay
  const lengthSquared = dx * dx + dy * dy
  const t =
    lengthSquared === 0
      ? 0
      : Math.min(1, Math.max(0, ((px - ax) * dx + (py - ay) * dy) / lengthSquared))
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/** Inside the rounded square of corner radius `r` (unit space)? */
function insideRoundedSquare(x, y, r) {
  if (x < 0 || x > 1 || y < 0 || y > 1) return false
  const cx = Math.min(Math.max(x, r), 1 - r)
  const cy = Math.min(Math.max(y, r), 1 - r)
  return Math.hypot(x - cx, y - cy) <= r + 1e-9
}

function insideCheck(x, y, scale) {
  // Scale the artwork about the centre, which is how the maskable variant keeps
  // the mark inside Android's safe zone without being redrawn.
  const px = (x - 0.5) / scale + 0.5
  const py = (y - 0.5) / scale + 0.5
  const half = CHECK.width / 2
  for (let i = 0; i < CHECK.points.length - 1; i += 1) {
    if (distanceToSegment(px, py, CHECK.points[i], CHECK.points[i + 1]) <= half)
      return true
  }
  return false
}

/**
 * @param {{size: number, radius: number, markScale: number}} spec
 *   `radius` is the background corner radius in unit space; 0.5 is a circle and
 *   0 a full-bleed square (what iOS and Android's mask want, since they apply
 *   their own shape).
 */
function renderPng({ size, radius, markScale }) {
  const raw = Buffer.alloc(size * (size * 4 + 1))
  const step = 1 / (SAMPLES + 1)

  for (let y = 0; y < size; y += 1) {
    const rowStart = y * (size * 4 + 1)
    raw[rowStart] = 0 // filter type: none
    for (let x = 0; x < size; x += 1) {
      let background = 0
      let mark = 0
      for (let sy = 1; sy <= SAMPLES; sy += 1) {
        for (let sx = 1; sx <= SAMPLES; sx += 1) {
          const u = (x + sx * step) / size
          const v = (y + sy * step) / size
          if (insideRoundedSquare(u, v, radius)) {
            background += 1
            if (insideCheck(u, v, markScale)) mark += 1
          }
        }
      }
      const total = SAMPLES * SAMPLES
      const alpha = background / total
      const inkShare = mark / total
      // Composite the mark over the background, then the whole thing over
      // transparency, so the rounded corners stay soft.
      const offset = rowStart + 1 + x * 4
      for (let channel = 0; channel < 3; channel += 1) {
        const colour =
          alpha === 0
            ? 0
            : (BRAND[channel] * (alpha - inkShare) + INK[channel] * inkShare) / alpha
        raw[offset + channel] = Math.round(colour)
      }
      raw[offset + 3] = Math.round(alpha * 255)
    }
  }

  return encodePng(size, raw)
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})

function crc32(buffer) {
  let c = 0xffffffff
  for (const byte of buffer) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([length, body, crc])
}

function encodePng(size, raw) {
  const header = Buffer.alloc(13)
  header.writeUInt32BE(size, 0)
  header.writeUInt32BE(size, 4)
  header[8] = 8 // bit depth
  header[9] = 6 // colour type: RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

const ICONS = [
  { file: 'icon-192.png', size: 192, radius: 0.22, markScale: 1 },
  { file: 'icon-512.png', size: 512, radius: 0.22, markScale: 1 },
  // Maskable: full bleed, artwork inside the safe zone Android may crop to.
  { file: 'icon-maskable-512.png', size: 512, radius: 0, markScale: 0.72 },
  // iOS ignores the manifest and applies its own mask, so: square, no radius.
  { file: 'apple-touch-icon-180.png', size: 180, radius: 0, markScale: 0.86 },
]

mkdirSync(OUT_DIR, { recursive: true })
for (const { file, ...spec } of ICONS) {
  const png = renderPng(spec)
  writeFileSync(join(OUT_DIR, file), png)
  console.log(`${file}  ${spec.size}x${spec.size}  ${png.length} bytes`)
}
