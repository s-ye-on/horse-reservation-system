function decodeFileName(value: string) {
  try { return decodeURIComponent(value) } catch { return '' }
}

function sanitizeCsvFileName(value: string, fallback: string) {
  const candidate = Array.from(value)
    .filter((character) => {
      const codePoint = character.codePointAt(0) ?? 0
      return codePoint >= 32 && codePoint !== 127
    })
    .join('')
    .replace(/\\(.)/g, '$1')
    .split(/[\\/]/).at(-1) ?? ''
  const baseName = candidate.replace(/[<>:"|?*]/g, '_').trim().replace(/[. ]+$/g, '')
  if (!baseName || baseName === '.' || baseName === '..') return fallback
  const safeBaseName = /^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(baseName) ? `_${baseName}` : baseName
  const fileName = safeBaseName.toLowerCase().endsWith('.csv') ? safeBaseName : `${safeBaseName}.csv`
  return fileName.length <= 120 ? fileName : `${fileName.slice(0, 116)}.csv`
}

export function parseCsvFileName(contentDisposition: string | null, fallback: string) {
  if (!contentDisposition) return fallback
  const encoded = contentDisposition.match(/filename\*\s*=\s*UTF-8'[^']*'("?)([^";]+)\1/i)?.[2]
  if (encoded) {
    const decoded = decodeFileName(encoded)
    if (decoded) return sanitizeCsvFileName(decoded, fallback)
  }
  const quoted = contentDisposition.match(/filename\s*=\s*"((?:\\.|[^"])*)"/i)?.[1]
  if (quoted) return sanitizeCsvFileName(quoted, fallback)
  const plain = contentDisposition.match(/filename\s*=\s*([^;]+)/i)?.[1]
  return plain ? sanitizeCsvFileName(plain, fallback) : fallback
}

export function triggerCsvDownload({ blob, fileName }: { blob: Blob; fileName: string }) {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  let triggered = false
  try {
    link.href = objectUrl
    link.download = fileName
    link.hidden = true
    document.body.append(link)
    link.click()
    triggered = true
  } finally {
    link.remove()
    if (triggered) window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
    else URL.revokeObjectURL(objectUrl)
  }
}
