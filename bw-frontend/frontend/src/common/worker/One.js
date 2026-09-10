let i = 0

function sc() {
  i++
  self.postMessage(i)
  setTimeout(sc, 2000)
}

sc()
self.onmessage = (e) => {
  console.log(e.data)
}