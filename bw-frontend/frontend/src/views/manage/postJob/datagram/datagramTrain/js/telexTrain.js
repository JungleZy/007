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
  const isFocus = ref(false)
  const code = ref([])
  const activeIndex = ref(0)
  const pageCodes = ref([])
  const acceptedText = new Map()
  const trainData = ref({ id: '', content: [], groupNumber: 0, speed: 0, duration: 0, status: 0 })
  const page = ref({ current: 1, pageAll: 0 })
  let enterCount = 0
  const flow = usePageSubmission({
    id: route.query.id, trainData, page, loading, countDown,
    countdown: { enabled: isCountdown, duration },
    getText: number => pageCodes.value[number - 1] || '',
    restoreText: (number, text) => { pageCodes.value[number - 1] = text; acceptedText.set(number, text) },
    showPage: (number, content) => {
      trainData.value.content = content
      activeIndex.value = 0
      enterCount = 0
      nextTick(() => document.querySelector('textarea')?.focus())
    },
    onFinished: () => router.push({ path: route.matched[4].path + '/datagramTrainScore', query: { id: route.query.id } })
  })
  const keyDownStart = event => {
    if (isFocus.value || event.ctrlKey || event.metaKey || event.altKey) return
    if (event.code === 'F4' || event.code === 'F5') {
      event.preventDefault()
      if (event.repeat) return
      event.code === 'F4' ? flow.prev() : flow.next()
      return
    }
    if (event.target?.tagName === 'TEXTAREA' && trainData.value.status === 0 && flow.ready.value && !flow.failure.value && !flow.busy.value && (event.key.length === 1 || event.key === 'Enter')) {
      event.preventDefault()
      const text = event.key === 'Enter' ? '\n' : event.key.toUpperCase()
      pageCodes.value[page.value.current - 1] = (pageCodes.value[page.value.current - 1] || '') + text
      acceptedText.set(page.value.current, pageCodes.value[page.value.current - 1])
      flow.startTest()
      return
    }
    if (event.target?.tagName !== 'TEXTAREA') return
    if (!flow.canCapture()) {
      event.preventDefault()
      if (event.key.length === 1 || event.key === 'Enter') message.warning('采集已暂停，请等待提交确认或点击重试后继续输入')
      return
    }
    enterCount = event.key === 'Enter' ? enterCount + 1 : 0
  }
  const beforeInput = event => {
    if (!flow.canCapture()) {
      event.preventDefault()
      message.warning('当前不能采集新输入，请等待提交确认、恢复训练或核对截止结果')
    }
  }
  const textareaChange = () => {
    if (!flow.canCapture()) {
      pageCodes.value[page.value.current - 1] = acceptedText.get(page.value.current) || ''
      message.warning('采集已暂停或截止，未接受本次输入')
      return
    }
    pageCodes.value[page.value.current - 1] = (pageCodes.value[page.value.current - 1] || '').toUpperCase()
    acceptedText.set(page.value.current, pageCodes.value[page.value.current - 1])
    const characters = pageCodes.value.reduce((sum, text) => sum + Array.from(text || '').filter(value => !/\s|\//.test(value)).length, 0)
    trainData.value.speed = flow.elapsed() > 0 ? Number((characters * 60 / flow.elapsed()).toFixed(1)) : 0
    if (enterCount >= 2) {
      enterCount = 0
      flow.next()
    }
  }
  const changeCountdown = () => {
    if (trainData.value.status === 0) countDown.value?.autoSetTimeAdd(isCountdown.value ? Number(duration.value || 0) * 60 : 0)
  }
  const getFocus = () => { isFocus.value = true }
  const lackFocus = () => {
    isFocus.value = false
    nextTick(() => document.querySelector('textarea')?.focus())
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
  return { ...flow, trainData, code, activeIndex, page, pageCodes, isCountdown, duration,
    changeCountdown, textareaChange, beforeInput, getFocus, lackFocus }
}
