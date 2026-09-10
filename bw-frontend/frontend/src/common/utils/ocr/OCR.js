export default class OCR {
  constructor(img) {
    this.img = img
  }
  dispose(img, callback) {
    fetch(window.ocrUrl, {
      method: 'POST',
      body: JSON.stringify({
        keyword: img
      })
    })
      .then(data => {
        return data.text()
      })
      .then(res => {
        res = res.replace(/\(/g, '')
        res = res.replace(/\)/g, '')
        res = res.replace(/'/g, '"')
        let arrEval = JSON.parse(res)
        let list = []
        for (let i = 0; i < arrEval.length; i++) {
          if (list[arrEval[i][0]] === undefined) {
            list[arrEval[i][0]] = []
          }
          list[arrEval[i][0]].push(arrEval[i][2])
        }
        callback(list)
      })
  }
}
