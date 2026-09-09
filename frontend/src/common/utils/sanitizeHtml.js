import DOMPurify from 'dompurify'

const ALLOWED_URI_REGEXP = /^(?:(?:https?):|\/|\.\.?\/|#|$)/i

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
  FORBID_ATTR: [
    'autofocus', 'formaction', 'onerror', 'onload', 'onclick', 'onmouseover'
  ],
  FORBID_TAGS: ['base', 'embed', 'form', 'iframe', 'link', 'math', 'meta', 'object', 'script', 'style', 'svg']
}

export const sanitizeHtml = value => DOMPurify.sanitize(String(value ?? ''), OPTIONS)
