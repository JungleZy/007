const fs = require('fs')
const path = require('path')
const NodeCoreUtils = require('../core/node_core_utils')
const { formidable } = require('formidable')
const context = require('../core/node_core_ctx')

function file(req, res, next) {
  res.json({ data: 'file ok' })
}

function getFile(req, res, next) {
  const realPath = path.join(context.appPath, req.params['0']) // 获取物理路径
  NodeCoreUtils.sendFile(realPath, res, req).then()
}

//视频流播放
// 前端请求 http://10.10.0.99:8000/api/file/getReadStream/file/doc/knowledge/video/16-06-26.mp4
function getReadStream(req, res, next) {
  const realPath = path.join(context.appPath, req.params[0])
  let stat = fs.statSync(realPath)
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
    let filePath = context.appPath
    let currentPath = req.query.currentPath
    if (req.header('currentPath')) {
      currentPath = req.header('currentPath')
    }

    if (currentPath) {
      currentPath = currentPath
        .replace(/\//g, path.sep)
        .replace(/^[\\\/]+|[\\\/]+$/, '')
      const dirs = currentPath.split(path.sep)
      let str = ''
      for (let i in dirs) {
        str += `${path.sep}${dirs[i]}`
        if (!fs.existsSync(path.join(filePath, str))) {
          try {
            fs.mkdirSync(path.join(filePath, str), { recursive: true })
          } catch (e) {
            process.send(`- [upload file error] ${e}`)
          }
        }
      }
    } else {
      currentPath = ''
    }
    filePath = path.join(filePath, path.sep, currentPath)
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
      files.forEach((file) => {
        file = file.file
        const originalFilename = generateFilename(file.originalFilename)
        names.push(
          path.join(currentPath, currentPath ? path.sep : '', originalFilename)
        )
        try {
          fs.renameSync(
            file.filepath,
            path.join(filePath, path.sep, originalFilename)
          )
          process.send(`- [upload file] end`)
        } catch (e) {
          process.send(`- [upload file error] ${e}`)
        }
      })
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
