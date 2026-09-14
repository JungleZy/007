#!/usr/bin/env node
/**
 * keysim：手键/电子键拍发模拟器。
 * 一个核心（真实节拍的帧时间轴）+ 四个注入层（字节 / 18765 桥接 / 帧 / 后端载荷）。
 */
import {parseArgs} from 'node:util'
import {writeFileSync} from 'node:fs'
import {electronKeyPlan, electronKeyTimeline} from './electronkey.mjs'
import {handKeyPlan, handKeyTimeline} from './handkey.mjs'
import {applyFaults} from './faults.mjs'
import {toByteStream, toHex} from './sinks/bytes.mjs'
import {toBridgeMessages, toFrames} from './sinks/frames.mjs'
import {startBridge} from './sinks/bridge.mjs'
import {electronUploadPayload, handUploadPayload} from './sinks/payload.mjs'
import {randomMessage} from './random.mjs'
import {startServer} from './server.mjs'

const USAGE = `用法：
  keysim serve    [--http 18700] [--port 18765]   打开网页控制台（虚拟串口开关 / 随机或手输报文）
  keysim hand     --text "ABCD EFGH" [选项]
  keysim electron --text "ABCD EFGH" [选项]
  keysim bridge   [--port 18765]

通用选项
  --rate <n>        手键单位 字符/分（默认 100），电子键单位 组/分（默认 20）
  --alphabet <名>   letter | short | long | mix（默认 letter）
  --jitter <0..1>   节拍抖动比例（默认 0，必须 ≤ 评分规则 skew/100）
  --seed <n>        抖动随机种子（默认 1）
  --skew <n>        评分规则偏移量，手键校验用（默认 50）
  --single-page     单页训练：跳过"翻页后 codeGap 夹到 60ms"的可行性校验
  --no-preamble     不发开始符（默认发）
  --tail <名>       手键 turn|end|none；电子键 page|end|none
  --fault <列表>    dupDown,missingUp,microPress,unknownByte（逗号分隔）
  --sink <名>       frames（默认）| bytes | bridge | payload
  --out <路径>      输出写文件而非标准输出

bytes 选项    --chunk exact|split|merge|random  --window <ms>
bridge 选项   --port <n>  --speed <倍数>  --hold（回放后继续驻留）
serve 选项    --http <n> 控制台端口（默认 18700）  --port <n> 桥接端口（默认 18765）
              --link <路径> 额外的设备符号链接，可重复；如 --link /dev/ttyUSB0（需 root）
报文可用 --random <组数> 现生成，省掉 --text
payload 选项  --train-id <n>  --page <n>  --attempt <n>  --server-elapsed <ms>  --scale <0..1>
`

const {values, positionals} = parseArgs({
  allowPositionals: true,
  options: {
    text: {type: 'string'},
    rate: {type: 'string'},
    alphabet: {type: 'string'},
    jitter: {type: 'string'},
    seed: {type: 'string'},
    skew: {type: 'string'},
    'single-page': {type: 'boolean'},
    preamble: {type: 'boolean', default: true},
    tail: {type: 'string'},
    fault: {type: 'string'},
    sink: {type: 'string'},
    out: {type: 'string'},
    chunk: {type: 'string'},
    window: {type: 'string'},
    port: {type: 'string'},
    speed: {type: 'string'},
    hold: {type: 'boolean'},
    http: {type: 'string'},
    link: {type: 'string', multiple: true},
    random: {type: 'string'},
    'train-id': {type: 'string'},
    page: {type: 'string'},
    attempt: {type: 'string'},
    'server-elapsed': {type: 'string'},
    scale: {type: 'string'},
    help: {type: 'boolean', short: 'h'}
  }
})

const command = positionals[0]
if (values.help || !command) {
  process.stdout.write(USAGE)
  process.exit(values.help ? 0 : 1)
}

const number = (value, fallback) => value === undefined ? fallback : Number(value)
const tailOf = fallback => values.tail === undefined ? fallback : values.tail === 'none' ? null : values.tail

function buildTimeline() {
  const alphabet = values.alphabet ?? 'letter'
  const jitter = number(values.jitter, 0)
  const seed = number(values.seed, 1)
  const skew = number(values.skew, 50)
  const shared = {text: values.text, alphabet, jitter, seed, preamble: values.preamble}
  if (command === 'hand') {
    const plan = handKeyPlan({rate: number(values.rate, 100), type: alphabet, skew, jitter, pageTurns: !values['single-page']})
    return handKeyTimeline({...shared, plan, tail: tailOf(null)})
  }
  const plan = electronKeyPlan({rate: number(values.rate, 20)})
  return electronKeyTimeline({...shared, plan, tail: tailOf('end')})
}

function faultSpec() {
  if (!values.fault) return {}
  return Object.fromEntries(values.fault.split(',').map(name => [name.trim(), true]).filter(([name]) => name))
}

const emit = payload => {
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)
  if (values.out) writeFileSync(values.out, `${text}\n`)
  else process.stdout.write(`${text}\n`)
}

if (command === 'serve') {
  const server = await startServer({
    port: number(values.http, 18700),
    bridgePort: number(values.port, 18765),
    deviceLinks: values.link && values.link.length ? values.link : undefined
  })
  process.stderr.write(`控制台已启动：${server.url}（Ctrl-C 退出）\n`)
  process.on('SIGINT', async () => { await server.close(); process.exit(0) })
} else if (command === 'bridge') {
  const bridge = await startBridge({port: number(values.port, 18765), log: message => process.stderr.write(`${message}\n`)})
  process.stderr.write(`桥接已启动：${bridge.url}（Ctrl-C 退出）\n`)
  process.on('SIGINT', async () => { await bridge.close(); process.exit(0) })
} else if (command === 'hand' || command === 'electron') {
  if (!values.text && values.random) {
    values.text = randomMessage({alphabet: values.alphabet ?? 'letter', groups: number(values.random, 4), seed: number(values.seed, null)})
    process.stderr.write(`随机报文：${values.text}\n`)
  }
  if (!values.text) { process.stderr.write('缺少 --text（或用 --random <组数>）\n'); process.exit(1) }
  const timeline = applyFaults(buildTimeline(), faultSpec())
  const sink = values.sink ?? 'frames'

  if (sink === 'frames') {
    emit({key: timeline.key, plan: timeline.plan, duration: timeline.duration, frames: toFrames(timeline)})
  } else if (sink === 'bytes') {
    const chunks = toByteStream(timeline, {mode: values.chunk ?? 'exact', window: number(values.window, 0), seed: number(values.seed, 1)})
    emit({key: timeline.key, duration: timeline.duration, chunks: chunks.map(chunk => ({at: chunk.at, hex: toHex([chunk])[0]}))})
  } else if (sink === 'payload') {
    const capture = {attempt: number(values.attempt, 0), serverElapsedMs: number(values['server-elapsed'], 0)}
    const options = {timeline, trainId: number(values['train-id'], 0), capture, scale: number(values.scale, 1)}
    emit(timeline.key === 'hand'
      ? handUploadPayload({...options, floorNumber: number(values.page, 1)})
      : electronUploadPayload({...options, pageNumber: number(values.page, 1)}))
  } else if (sink === 'bridge') {
    const bridge = await startBridge({port: number(values.port, 18765), log: message => process.stderr.write(`${message}\n`)})
    process.stderr.write(`桥接已启动：${bridge.url}，等待客户端接入…\n`)
    await bridge.waitForClient({timeout: 600000})
    process.stderr.write(`客户端已接入，开始回放 ${toBridgeMessages(timeline).length} 帧（约 ${(timeline.duration / 1000).toFixed(1)}s）\n`)
    const result = await bridge.replay(timeline, {speed: number(values.speed, 1)})
    process.stderr.write(`回放完成：${JSON.stringify(result)}\n`)
    if (!values.hold) await bridge.close()
    else process.on('SIGINT', async () => { await bridge.close(); process.exit(0) })
  } else {
    process.stderr.write(`未知 sink ${sink}\n`)
    process.exit(1)
  }
} else {
  process.stderr.write(`未知命令 ${command}\n${USAGE}`)
  process.exit(1)
}
