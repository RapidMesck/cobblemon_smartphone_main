import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export const SMARTPHONE_COLORS = [
  { id: 'red', label: 'Red', hex: '#dc2626' },
  { id: 'blue', label: 'Blue', hex: '#2563eb' },
  { id: 'yellow', label: 'Yellow', hex: '#eab308' },
  { id: 'green', label: 'Green', hex: '#16a34a' },
  { id: 'black', label: 'Black', hex: '#18181b' },
  { id: 'white', label: 'White', hex: '#f4f4f5' },
  { id: 'cyan', label: 'Cyan', hex: '#06b6d4' },
  { id: 'light_blue', label: 'Light Blue', hex: '#38bdf8' },
  { id: 'purple', label: 'Purple', hex: '#9333ea' },
  { id: 'magenta', label: 'Magenta', hex: '#d946ef' },
  { id: 'pink', label: 'Pink', hex: '#ec4899' },
  { id: 'orange', label: 'Orange', hex: '#f97316' },
  { id: 'lime', label: 'Lime', hex: '#84cc16' },
  { id: 'brown', label: 'Brown', hex: '#92400e' },
  { id: 'gray', label: 'Gray', hex: '#52525b' },
  { id: 'light_gray', label: 'Light Gray', hex: '#a1a1aa' },
] as const

export type SmartphoneColorId = (typeof SMARTPHONE_COLORS)[number]['id']

export function slugify(input: string): string {
  return input
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/^_+|_+$/g, '') || 'custom'
}

export function createId(prefix: string): string {
  return `${prefix}_${Math.random().toString(36).slice(2, 9)}`
}

export function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}
