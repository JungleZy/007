const possible = 'ABCDEFGHIJKLMNOPQRSTUVWKYZ0123456789'
const numPossible = possible.length
const fs = require('fs')
const path = require('path')
const IS_OPEN_CACHE = true // 是否开启缓存功能
const CACHE_TIME = 10 // 告诉浏览器多少时间内可以不用请求服务器，单位：秒

function genRandomName() {
  let name = ''
  for (let i = 0; i < 4; i++) {
    name += possible.charAt((Math.random() * numPossible) | 0)
  }
  return name
}

function getMime(ext) {
  let mime = {
    css: 'text/css',
    gif: 'image/gif',
    html: 'text/html',
    ico: 'image/x-icon',
    jpeg: 'image/jpeg',
    jpg: 'image/jpeg',
    js: 'text/javascript',
    json: 'application/json',
    pdf: 'application/pdf',
    png: 'image/png',
    svg: 'image/svg+xml',
    swf: 'application/x-shockwave-flash',
    tiff: 'image/tiff',
    txt: 'text/plain',
    wav: 'audio/x-wav',
    wma: 'audio/x-ms-wma',
    wmv: 'video/x-ms-wmv',
    xml: 'text/xml'
  }
  return mime[ext] || 'text/plain'
}

function sendFile(realPath, res, req) {
  return new Promise((resolve, reject) => {
    fs.stat(realPath, (err, stats) => {
      if (err || stats.isDirectory()) {
        res.writeHead(404, 'not found', {
          'Content-Type': 'text/plain;charset=UTF-8'
        })
        res.write(`the request ${realPath} is not found`)
        res.end()
      } else {
        let ext = path.extname(realPath)
        ext = ext ? ext.slice(1) : 'unknown'
        let contentType = getMime(ext) + ';charset=utf-8'
        let endFilePath = realPath
        if (!IS_OPEN_CACHE) {
          let raw = fs.createReadStream(endFilePath)
          res.writeHead(200, { 'content-type': contentType })
          raw.pipe(res)
        } else {
          let lastModified = stats.mtime.toUTCString()
          const ifModifiedSince = 'if-modified-since'
          let expires = new Date()
          expires.setTime(expires.getTime() + CACHE_TIME * 1000)
          res.setHeader('Expires', expires.toUTCString())
          res.setHeader('Cache-Control', 'max-age=' + CACHE_TIME)
          let last = realPath.split('.')[1]
          if (last === 'mp4' || last === 'ogg' || last === 'webm') {
            res.setHeader('Accept-Ranges', 'bytes')
          }
          if (
              req.headers[ifModifiedSince] &&
              lastModified === req.headers[ifModifiedSince]
          ) {
            res.writeHead(304, 'Not Modified')
            res.end()
          } else {
            res.setHeader('Last-Modified', lastModified)
            let raw = fs.createReadStream(endFilePath)
            res.writeHead(200, { 'content-type': contentType })
            raw.pipe(res)
          }
        }
      }
    })
  })
}

module.exports = {
  genRandomName,
  sendFile
}
