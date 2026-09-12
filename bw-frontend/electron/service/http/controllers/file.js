const fs = require('fs')
const path = require('path')
const NodeCoreUtils = require('../core/node_core_utils')
const { formidable } = require('formidable')
const context = require('../core/node_core_ctx')

// 文件服务的根目录。
// 注意：这里的 context 是 service/http/core/node_core_ctx.js —— fork 出来的文件服务
// **子进程自己**的上下文模块，其 appPath 由 service/http/index.js:9 设为
// `<安装目录>/bin/file`，比 Electron 主进程的 appPath（main.js:12 的 dirname(exe)）
// 窄一层。绝不可改用主进程的 appPath：那会把可读写范围扩大到包含 bin/server（后端
// 原生二进制）、bin/nip.db（授权与地址库）和 resources/app.asar。
// appPath 在本模块被 require 之后才赋值，因此只能惰性求值。
let rootPath = null
let realRootPath = null

function getRoot() {
  if (rootPath === null) {
    rootPath = path.resolve(context.appPath)
  }
  return rootPath
}

// 根目录自身的 realpath（安装目录可能位于符号链接之下）。目录还没建好时不缓存。
function getRealRoot() {
  if (realRootPath === null) {
    try {
      realRootPath = fs.realpathSync(getRoot())
    } catch (e) {
      return getRoot()
    }
  }
  return realRootPath
}

function isWithin(base, target) {
  return target === base || target.startsWith(base + path.sep)
}

// 把请求里的相对路径归一为根目录内的物理路径；越界（`..` 穿越、绝对路径、符号链接
// 逃逸）一律返回 null，由调用方统一回 404 —— 不区分「越界」与「不存在」，不泄露
// 根外文件的存在性，也不回显路径。
function resolveWithinRoot(requestPath) {
  const root = getRoot()
  const target = path.resolve(root, requestPath || '')
  if (!isWithin(root, target)) {
    return null
  }
  let realTarget
  try {
    realTarget = fs.realpathSync(target)
  } catch (e) {
    return target // 不存在：没有链接可逃逸，交由调用方按 404 处理
  }
  return isWithin(getRealRoot(), realTarget) ? realTarget : null
}

// 上传目录的非法段：空段由 split 过滤，`.`/`..` 与盘符段直接丢弃。
const INVALID_SEGMENT = /^(?:\.\.?|[A-Za-z]:)$/

// 上传目标目录逐段过滤后再归一，并强制落在根目录内；越界返回 null。
function sanitizeCurrentPath(currentPath) {
  const segments = String(currentPath || '')
    .split(/[\\/]+/)
    .filter((seg) => seg !== '' && !INVALID_SEGMENT.test(seg))
  const relative = segments.join(path.sep)
  const absolute = path.resolve(getRoot(), relative)
  return isWithin(getRoot(), absolute) ? { relative, absolute } : null
}

function file(req, res, next) {
  res.json({ data: 'file ok' })
}

function getFile(req, res, next) {
  const realPath = resolveWithinRoot(req.params['0']) // 根目录内的物理路径
  if (!realPath) {
    return res.sendStatus(404)
  }
  NodeCoreUtils.sendFile(realPath, res, req).then()
}

//视频流播放
// 前端请求 /api/file/getReadStream/file/doc/knowledge/video/16-06-26.mp4
function getReadStream(req, res, next) {
  const realPath = resolveWithinRoot(req.params[0])
  if (!realPath) {
    return res.sendStatus(404)
  }
  let stat
  try {
    stat = fs.statSync(realPath)
  } catch (e) {
    return res.sendStatus(404)
  }
  if (stat.isDirectory()) {
    return res.sendStatus(404)
  }
  let fileSize = stat.size
  let range = req.headers.range
  if (range) {
    let parts = range.substring(range.indexOf('=') + 1, range.length).split('-')
    let start = parseInt(parts[0], 10)
    let end = parts[1] ? parseInt(parts[1], 10) : start + 9999999
    end = end > fileSize - 1 ? fileSize - 1 : end
    let chunkSize = end - start + 1
    let file = fs.createReadStream(realPath, { start, end })
    let head = {
      'Content-Range': `bytes ${start}-${end}/${fileSize}`,
      'Accept-Ranges': 'bytes',
      'Content-Length': chunkSize,
      'Content-Type': 'video/mp4'
    }
    res.writeHead(206, head)
    file.pipe(res)
  } else {
    res.writeHead(200, { 'Content-Type': 'video/mp4' })
    fs.createReadStream(realPath).pipe(res)
  }
}

//上传文件
function fileUpload(req, res, next) {
  try {
    process.send(`- [upload file] start`)
    let currentPath = req.query.currentPath
    if (req.header('currentPath')) {
      currentPath = req.header('currentPath')
    }
    const target = sanitizeCurrentPath(currentPath)
    if (!target) {
      process.send(`- [upload file error] 上传目录越界`)
      return res.status(400).json({ data: [], message: '上传目录非法' })
    }
    currentPath = target.relative
    const filePath = target.absolute
    try {
      fs.mkdirSync(filePath, { recursive: true })
    } catch (e) {
      // 建目录失败必须报错：此前异常被吞掉后仍返回成功，前端会拿到一个不存在的路径
      process.send(`- [upload file error] ${e}`)
      return res.status(500).json({ data: [], message: '上传目录创建失败' })
    }
    const form = formidable({
      multiples: true,
      keepExtensions: false,
      maxFileSize: 1024 * 1024 * 1024 * 1024,
      maxFieldsSize: 20 * 1024 * 1024,
      uploadDir: filePath
    })
    const files = []
    const fields = []
    form.on('fileBegin', (formName, file) => {
      // Log.info('start')
    })
    form.on('progress', (bytesReceived, bytesExpected) => {
      // Log.info(`[upload file] schedule: ${bytesReceived}/${bytesExpected}`)
    })
    form.on('field', (fieldName, value) => {
      process.send(`- [upload file] field`)
      fields.push({ fieldName, value })
    })
    form.on('file', (fieldName, file) => {
      process.send(`- [upload file] file`)
      files.push({ fieldName, file })
    })
    form.on('end', () => {
      process.send(`- [upload file] handle`)
      let names = []
      let failed = 0
      files.forEach((file) => {
        file = file.file
        // 客户端可以在 multipart 的 filename 里塞分隔符，只取基名
        const originalFilename = generateFilename(
          path.basename(String(file.originalFilename || ''))
        )
        const targetFile = path.resolve(filePath, originalFilename)
        if (!isWithin(getRoot(), targetFile)) {
          failed++
          process.send(`- [upload file error] 目标文件越界`)
          return
        }
        try {
          fs.renameSync(file.filepath, targetFile)
          names.push(
            path.join(
              currentPath,
              currentPath ? path.sep : '',
              originalFilename
            )
          )
          process.send(`- [upload file] end`)
        } catch (e) {
          failed++
          process.send(`- [upload file error] ${e}`)
        }
      })
      if (failed > 0) {
        // 落盘失败不得再返回成功：前端拿 data 直接拼资源地址
        return res.status(500).json({ data: names, message: '文件保存失败' })
      }
      res.json({ data: names })
    })
    form.parse(req)
  } catch (e) {
    process.send(`- [upload file error] ${e}`)
    next(e)
  }
}

function generateFilename(filename) {
  let names = filename.split('.')
  if (names.length > 1) {
    return `${filename.substring(
      0,
      filename.lastIndexOf('.')
    )}-${NodeCoreUtils.genRandomName()}.${names[names.length - 1]}`
  } else {
    return `${filename.substring(
      0,
      filename.lastIndexOf('.')
    )}-${NodeCoreUtils.genRandomName()}`
  }
}

module.exports = {
  file,
  getFile,
  fileUpload,
  getReadStream
}
