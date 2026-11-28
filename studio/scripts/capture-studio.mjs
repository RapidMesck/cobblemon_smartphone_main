import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright'

const BASE_URL = process.env.STUDIO_URL || 'http://localhost:5173'
const OUT_DIR = process.env.OUT_DIR || path.resolve(process.cwd(), '.screenshots')

fs.mkdirSync(OUT_DIR, { recursive: true })

const browser = await chromium.launch({
  headless: true,
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'],
})
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

const errors = []
page.on('pageerror', (err) => errors.push(`PAGEERROR: ${err.message}`))
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(`CONSOLE ERROR: ${msg.text()}`)
})

async function snap(name, waitMs = 600) {
  await page.waitForTimeout(waitMs)
  const file = path.join(OUT_DIR, `${name}.png`)
  await page.screenshot({ path: file })
  console.log(`Captured ${file}`)
}

const nav = page.locator('aside')

await page.goto(BASE_URL, { waitUntil: 'networkidle' })
await snap('01-dashboard', 700)

await nav.getByRole('button', { name: 'Theme Studio' }).click()
await snap('02-theme-home', 900)

await page.getByRole('button', { name: 'Settings (Large)' }).click()
await snap('03-theme-settings', 600)

await page.getByRole('button', { name: 'Social (211×207)' }).click()
await snap('04-theme-large', 600)

await nav.getByRole('button', { name: 'UI Screens' }).click()
await snap('05-ui-screen-hd', 900)

await page.getByRole('button', { name: 'Pixel Style' }).click()
await snap('05b-ui-screen-pixel', 600)

await nav.getByRole('button', { name: 'Actions & Apps' }).click()
await snap('06-actions', 600)

await nav.getByRole('button', { name: '3D Skins' }).click()
await snap('07-skin-hand-3d', 1400)

await page.getByRole('button', { name: 'Rotom Phone 3D' }).click()
await snap('08-skin-rotom-3d', 1600)

await browser.close()

if (errors.length > 0) {
  console.error('Errors detected during capture:\n' + errors.join('\n'))
  process.exitCode = 1
} else {
  console.log('Zero console/page errors!')
}
