import DOMPurify from 'dompurify'

const ALLOWED_URI_REGEXP = /^(?:(?:https?):|\/|\.\.?\/|#|$)/i
const URI_ATTRIBUTES = new Set(['action', 'href', 'src', 'xlink:href'])

const OPTIONS = {
  ALLOWED_TAGS: [
    'a', 'abbr', 'b', 'blockquote', 'br', 'code', 'col', 'colgroup', 'dd', 'del',
    'div', 'em', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'i', 'img', 'li',
    'ol', 'p', 'pre', 'q', 's', 'small', 'span', 'strong', 'sub', 'sup', 'table',
    'tbody', 'td', 'tfoot', 'th', 'thead', 'tr', 'u', 'ul'
  ],
  ALLOWED_ATTR: [
    'alt', 'colspan', 'height', 'href', 'rel', 'rowspan', 'src', 'target', 'title', 'width'
  ],
  ALLOWED_URI_REGEXP,
  FORBID_ATTR: ['autofocus', 'formaction', 'onerror', 'onload', 'onclick', 'onmouseover'],
  FORBID_TAGS: ['base', 'embed', 'form', 'iframe', 'link', 'math', 'meta', 'object', 'script', 'style', 'svg']
}

DOMPurify.addHook('uponSanitizeAttribute', (_node, data) => {
  if (!URI_ATTRIBUTES.has(data.attrName.toLowerCase())) return
  const value = String(data.attrValue || '').trim()
  const protocol = value.match(/^([a-z][a-z0-9+.-]*):/i)?.[1]?.toLowerCase()
  if (protocol && protocol !== 'http' && protocol !== 'https') data.keepAttr = false
})

export const sanitizeHtml = value => DOMPurify.sanitize(String(value ?? ''), OPTIONS)
