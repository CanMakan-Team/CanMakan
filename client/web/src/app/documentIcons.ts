const LAUNCHER_FAVICON_HREF = '/favicon.webp'

function setHeadIcon(rel: string, href: string, type?: string) {
  let link = document.querySelector(`link[rel="${rel}"]`)
  if (!link) {
    link = document.createElement('link')
    link.setAttribute('rel', rel)
    document.head.appendChild(link)
  }
  if (type) {
    link.setAttribute('type', type)
  }
  link.setAttribute('href', href)
}

setHeadIcon('icon', LAUNCHER_FAVICON_HREF, 'image/webp')
setHeadIcon('apple-touch-icon', LAUNCHER_FAVICON_HREF)
