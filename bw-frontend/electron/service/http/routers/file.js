const express = require('express')
const getFileController = require('../controllers/file')
const jsonParser = require('body-parser').json()
module.exports = () => {
  let router = express.Router()
  router.get('/', getFileController.file.bind())
  router.get('/getFile/*', getFileController.getFile.bind())
  router.get('/getReadStream/*', getFileController.getReadStream.bind())

  router.post('/upload', jsonParser, getFileController.fileUpload.bind())

  return router
}
