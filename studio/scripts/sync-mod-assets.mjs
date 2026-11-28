import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const studioRoot = path.resolve(__dirname, '..')
const modResourcesDir = path.resolve(
  studioRoot,
  '../common/src/main/resources/assets/cobblemon_smartphone'
)
const outDir = path.resolve(studioRoot, 'public/mod-assets')

function copyDirRecursive(src, dest) {
  if (!fs.existsSync(src)) return 0
  fs.mkdirSync(dest, { recursive: true })
  let count = 0
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const srcPath = path.join(src, entry.name)
    const destPath = path.join(dest, entry.name)
    if (entry.isDirectory()) {
      count += copyDirRecursive(srcPath, destPath)
    } else if (entry.isFile()) {
      fs.copyFileSync(srcPath, destPath)
      count++
    }
  }
  return count
}

if (!fs.existsSync(modResourcesDir)) {
  console.warn(`[sync-mod-assets] Mod resources not found at ${modResourcesDir}. Skipping sync.`)
  process.exit(0)
}

fs.mkdirSync(outDir, { recursive: true })

const guiCount = copyDirRecursive(
  path.join(modResourcesDir, 'textures/gui'),
  path.join(outDir, 'textures/gui')
)
const itemTexCount = copyDirRecursive(
  path.join(modResourcesDir, 'textures/item'),
  path.join(outDir, 'textures/item')
)
const modelsCount = copyDirRecursive(
  path.join(modResourcesDir, 'models/item'),
  path.join(outDir, 'models/item')
)
const themesCount = copyDirRecursive(
  path.join(modResourcesDir, 'themes'),
  path.join(outDir, 'themes')
)

const iconSrc = path.join(modResourcesDir, 'icon.png')
if (fs.existsSync(iconSrc)) {
  fs.copyFileSync(iconSrc, path.join(outDir, 'icon.png'))
}

console.log(
  `[sync-mod-assets] Synced ${guiCount} GUI textures, ${itemTexCount} item textures, ${modelsCount} 3D models, and ${themesCount} built-in themes into public/mod-assets/`
)
