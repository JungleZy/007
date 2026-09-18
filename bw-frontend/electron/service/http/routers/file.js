const express = require('express')
const getFileController = require('../controllers/file')
const jsonParser = require('body-parser').json()
module.exports = () => {
  let router = express.Router()
  // 这些处理器都是普通函数、不依赖 this，原先的 .bind() 空参调用没有任何作用
  router.get('/', getFileController.file)
  router.get('/getFile/*', getFileController.getFile)
  router.get('/getReadStream/*', getFileController.getReadStream)

  router.post('/upload', jsonParser, getFileController.fileUpload)

  return router
}
