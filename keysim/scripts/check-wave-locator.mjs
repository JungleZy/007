// 在独立标签页验证实际控制台，不发送报文、不修改用户现有编辑器。
import assert from 'node:assert/strict'
const debugPort = process.argv[2] || '63965'
const consoleUrl = process.argv[3] || 'http://127.0.0.1:18700/'
const browser = await (await fetch(`http://127.0.0.1:${debugPort}/json/version`)).json()
const socket = new WebSocket(browser.webSocketDebuggerUrl)
await new Promise(resolve => socket.addEventListener('open', resolve, {once: true}))
let id = 0
const requests = new Map()
socket.addEventListener('message', event => {
  const message = JSON.parse(event.data)
  const pending = requests.get(message.id)
  if (!pending) return
  requests.delete(message.id); clearTimeout(pending.timer)
  if (message.error) pending.reject(new Error(JSON.stringify(message.error)))
  else pending.resolve(message.result)
})
const call = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
  const next = ++id
  const timer = setTimeout(() => {requests.delete(next);reject(new Error(`${method} timeout`))}, 15000)
  requests.set(next, {resolve, reject, timer})
  socket.send(JSON.stringify({id: next, method, params, sessionId}))
})
const {targetId} = await call('Target.createTarget', {url: 'about:blank'})
const {sessionId} = await call('Target.attachToTarget', {targetId, flatten: true})
try {
  await call('Page.navigate', {url: consoleUrl}, sessionId)
  let ready = false
  for (let n = 0; n < 30; n++) {
    const r = await call('Runtime.evaluate', {expression: 'typeof runPreview === "function"', returnByValue: true}, sessionId)
    if (r.result?.value) {ready = true;break}
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  assert.ok(ready, '控制台脚本应加载完成')
  await new Promise(resolve => setTimeout(resolve, 1000))
  const result = await call('Runtime.evaluate', {awaitPromise: true, returnByValue: true, expression: `(async()=>{
    clearTimeout(previewTimer);$('key').value='hand';$('alphabet').value='short';$('tail').value='none';$('preamble').checked=true;$('rate').value='120';
    $('message').value=Array(101).fill('1234').join(' ');await runPreview();
    const withoutPeriod=$('pageBreakNote').textContent;
    $('message').value=Array(99).fill('1234').join(' ')+' 6023 。7025 7025';await runPreview();
    const withPeriod=$('pageBreakNote').textContent;
    $('locateMode').value='text';$('locateQuery').value='7025';$('btnLocate').click();
    const first=tape.selection.start, firstScroll=tape.view.scrollLeft, matchNote=$('locateNote').textContent;
    const chars=$('locateChars').children.length;
    $('btnLocateNext').click();const second=tape.selection.start;
    $('btnLocatePrev').click();const previous=tape.selection.start;
    $('locateChars').children[1].click();const character=tape.selection.text, charNote=$('locateNote').textContent;
    $('locateMode').value='group';$('locateQuery').value='101';$('btnLocate').click();const groupText=tape.selection.text;
    $('locateQuery').value='99999';$('btnLocate').click();const absent=$('locateNote').textContent;
    $('message').value='9999';previewSoon();$('locateQuery').value='101';$('btnLocate').click();const stale=$('locateNote').textContent;
    return {withoutPeriod,withPeriod,first,second,previous,firstScroll,matchNote,chars,character,charNote,groupText,absent,stale};
  })()`}, sessionId)
  assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails))
  const data = result.result.value
  assert.match(data.withoutPeriod, /0 次/)
  assert.match(data.withPeriod, /1 次/)
  assert.ok(data.firstScroll > 0)
  assert.ok(data.second > data.first)
  assert.equal(data.previous, data.first)
  assert.equal(data.chars, 4)
  assert.equal(data.character, '0')
  assert.equal(data.groupText, '7025')
  assert.match(data.matchNote, /1\/2/)
  assert.match(data.absent, /未找到/)
  assert.match(data.stale, /重新生成/)
  console.log('通过：不自动分页、手动句号、报文定位、重复项切换、组号定位、逐字高亮、未命中及旧预览拦截')
  console.log(JSON.stringify(data, null, 2))
} finally {
  await call('Target.closeTarget', {targetId})
  socket.close()
}
