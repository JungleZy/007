import { onBeforeUnmount, onMounted, ref, nextTick, createVNode } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { PubSub } from '../../../../../../common/utils/PubSub'
import usePageSubmission from '../../js/usePageSubmission'

export default function (countDown, loading) {
  const router = useRouter()
  const route = useRoute()
  const isCountdown = ref(false)
  const duration = ref(120)
  const code = ref([])
  const compileCode = ref([[]])
  const compileCodePage = ref([])
  const activeIndex = ref(0)
  const trainData = ref({ id: '', content: [], groupNumber: 0, speed: 0, duration: 0, status: 0 })
  const page = ref({ current: 1, pageAll: 0 })
  let lastKeyTime = 0
  let startingKeys = null
  const getText = number => (code.value[number - 1] || []).map(event => event.text === 'Enter' ? '\n' : event.text).join('')
  const render = number => {
    compileCode.value = getText(number).split('\n').map(line => line.split(' '))
    const lines = compileCode.value
    activeIndex.value = (lines.length - 1) * 10 + lines[lines.length - 1].length - 1
    compileCodePage.value = []
    nextTick(() => {
      const keys = document.querySelector('.keys')
      if (keys) { keys.scrollTop = keys.scrollHeight; keys.scrollLeft = keys.scrollWidth }
    })
  }
  const flow = usePageSubmission({
    id: route.query.id, trainData, page, loading, countDown, getText,
    countdown: { enabled: isCountdown, duration },
    restoreText: (number, text) => {
      code.value[number - 1] = Array.from(text || '', text => ({ text: text === '\n' ? 'Enter' : text, time: 0 }))
    },
    showPage: (number, content) => { trainData.value.content = content; render(number); lastKeyTime = flow.elapsedMilliseconds() },
    onFinished: () => router.push({ path: route.matched[4].path + '/trainScore', query: { id: route.query.id } })
  })
  const appendKey = key => {
    const events = code.value[page.value.current - 1] ||= []
    const now = flow.elapsedMilliseconds()
    events.push({ text: key.length === 1 ? key.toUpperCase() : key, time: Math.max(0, now - lastKeyTime) / 100 })
    lastKeyTime = now
    render(page.value.current)
    const count = code.value.reduce((sum, events) => sum + events.filter(event => event.text.length === 1 && !/\s|\//.test(event.text)).length, 0)
    trainData.value.speed = flow.elapsed() > 0 ? Number((count * 60 / flow.elapsed()).toFixed(1)) : 0
    if (key === 'Enter' && events[events.length - 2]?.text === 'Enter') flow.next()
  }
  const keyDownStart = event => {
    if (event.ctrlKey || event.metaKey || event.altKey || ['INPUT', 'TEXTAREA'].includes(event.target?.tagName)) return
    if (event.target?.tagName === 'BUTTON' && ['Enter', ' '].includes(event.key)) return
    if (event.code === 'F4' || event.code === 'F5') {
      event.preventDefault()
      if (!event.repeat) event.code === 'F4' ? flow.prev() : flow.next()
      return
    }
    if (event.key.length !== 1 && event.key !== 'Enter') return
    event.preventDefault()
    if (startingKeys) { startingKeys.push(event.key); return }
    if (trainData.value.status === 0 && flow.ready.value && !flow.failure.value && !flow.busy.value) {
      startingKeys = [event.key]
      flow.startTest().then(started => {
        const keys = startingKeys
        startingKeys = null
        keys.forEach(key => {
          const events = code.value[page.value.current - 1] ||= []
          events.push({ text: key.length === 1 ? key.toUpperCase() : key, time: 0 })
        })
        render(page.value.current)
        lastKeyTime = flow.elapsedMilliseconds()
        if (!started) message.warning('开始训练未确认，已保留输入内容，请重试开始后继续。')
      })
      return
    }
    if (!flow.canCapture()) {
      message.warning('采集已暂停，请等待当前提交确认或点击重试后继续输入')
      return
    }
    appendKey(event.key)
  }
  onMounted(() => {
    window.addEventListener('keydown', keyDownStart)
    flow.initialise()
  })
  PubSub.subscribe('send_telexTrainPage', () => {
    if (trainData.value.status === 3) {
      PubSub.publish('callback_closeTelexTrainPage', true)
      return
    }
    if ([1, 2].includes(trainData.value.status) && !flow.authBlocked.value) {
      Modal.confirm({ class: 'init_modal_style', content: '当前训练还未完成确认，是否提交并结束训练？',
        icon: () => createVNode(ExclamationCircleOutlined), okText: '结束', cancelText: '取消',
        onOk: async () => {
          if (flow.failure.value && !await flow.retry()) return
          return flow.endTest()
        } })
    } else if (flow.busy.value || flow.failure.value) {
      Modal.confirm({ content: '当前请求未确认。离开会丢弃尚未提交的本地输入，服务端已收到的内容仍保留。是否离开？',
        okText: '离开', cancelText: '留在页面', onOk: () => PubSub.publish('callback_closeTelexTrainPage', true) })
    } else PubSub.publish('callback_closeTelexTrainPage', true)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', keyDownStart)
    PubSub.unsubscribe('send_telexTrainPage')
  })
  return { ...flow, trainData, code, compileCode, compileCodePage, activeIndex, page, isCountdown, duration }
}
