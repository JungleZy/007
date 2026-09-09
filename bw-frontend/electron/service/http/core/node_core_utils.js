const Crypto = require('crypto')
const { spawn } = require('child_process')
const net = require('net'),
    socket = net.Socket
const possible = 'ABCDEFGHIJKLMNOPQRSTUVWKYZ0123456789'
const numPossible = possible.length
const fs = require('fs')
const path = require('path')
const IS_OPEN_CACHE = true // 是否开启缓存功能
const CACHE_TIME = 10 // 告诉浏览器多少时间内可以不用请求服务器，单位：秒
const shortid = require('shortid')

function generateNewSessionID() {
  return shortid.generate()
}

function genRandomName() {
  let name = ''
  for (let i = 0; i < 4; i++) {
    name += possible.charAt((Math.random() * numPossible) | 0)
  }
  return name
}

function verifyAuth(signStr, streamId, secretKey) {
  if (signStr === undefined) {
    return false
  }
  let now = (Date.now() / 1000) | 0
  let exp = parseInt(signStr.split('-')[0])
  let shv = signStr.split('-')[1]
  let str = streamId + '-' + exp + '-' + secretKey
  if (exp < now) {
    return false
  }
  let md5 = Crypto.createHash('md5')
  let ohv = md5.update(str).digest('hex')
  return shv === ohv
}

function getFFmpegVersion(ffpath) {
  return new Promise((resolve, reject) => {
    let ffmpeg_exec = spawn(ffpath, ['-version'])
    let version = ''
    ffmpeg_exec.on('error', (e) => {
      reject(e)
    })
    ffmpeg_exec.stdout.on('data', (data) => {
      try {
        version = data
            .toString()
            .split(/(?:\r\n|\r|\n)/g)[0]
            .split(' ')[2]
      } catch (e) {}
    })
    ffmpeg_exec.on('close', (code) => {
      resolve(version)
    })
  })
}

function getFFmpegUrl() {
  let url = ''
  switch (process.platform) {
    case 'darwin':
      url =
          'https://ffmpeg.zeranoe.com/builds/macos64/static/ffmpeg-latest-macos64-static.zip'
      break
    case 'win32':
      url =
          'https://ffmpeg.zeranoe.com/builds/win64/static/ffmpeg-latest-win64-static.zip | https://ffmpeg.zeranoe.com/builds/win32/static/ffmpeg-latest-win32-static.zip'
      break
    case 'linux':
      url =
          'https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz | https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-i686-static.tar.xz'
      break
    default:
      url = 'http://ffmpeg.org/download.html'
      break
  }
  return url
}

function validIP(host, port, cb) {
  let nsk = new socket()
  nsk.setTimeout(1000)
  nsk
      .on('connect', function () {
        //连接状态
        nsk.destroy() //销毁
        cb(true)
      })
      .on('timeout', function () {
        //连接超时
        nsk.destroy()
        cb(false)
      })
      .on('error', function () {
        //连接错误
        nsk.destroy()
        cb(false)
      })
  nsk.connect(port, host) //执行连接
}

function secondsToDhms(seconds) {
  seconds = Number(seconds)
  let d = Math.floor(seconds / (3600 * 24))
  let h = Math.floor((seconds % (3600 * 24)) / 3600)
  let m = Math.floor((seconds % 3600) / 60)
  let s = Math.floor(seconds % 60)

  let dDisplay = d > 0 ? d + ' 天 ' : ''
  let hDisplay = h > 0 ? h + ' 小时 ' : ''
  let mDisplay = m > 0 ? m + ' 分钟 ' : ''
  let sDisplay = s > 0 ? s + ' 秒' : ''
  return dDisplay + hDisplay + mDisplay + sDisplay
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

function saveFile(path, content) {
  return new Promise((resolve, reject) => {
    const stream = fs.createWriteStream(path)
    stream.on('error', (err) => {
      reject(err)
    })
    stream.on('finish', () => {
      resolve(true)
    })
    stream.write(content)
    stream.end()
  })
}

function isDuringDate(diff, s, e) {
  let curDate = new Date(diff),
      beginDate = new Date(s),
      endDate = new Date(e)
  return curDate >= beginDate && curDate <= endDate
}

module.exports = {
  generateNewSessionID,
  verifyAuth,
  genRandomName,
  getFFmpegVersion,
  getFFmpegUrl,
  validIP,
  isDuringDate,
  secondsToDhms,
  sendFile,
  saveFile
}
