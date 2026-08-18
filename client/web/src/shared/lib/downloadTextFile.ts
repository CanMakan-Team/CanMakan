/** Triggers a browser download for plain-text content such as CSV exports. */
export function downloadTextFile(
  filename: string,
  mimeType: string,
  text: string,
): void {
  const url = URL.createObjectURL(new Blob([text], { type: mimeType }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  try {
    link.click()
  } finally {
    URL.revokeObjectURL(url)
  }
}
