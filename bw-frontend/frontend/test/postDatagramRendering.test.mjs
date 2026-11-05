import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse, compileScript, compileTemplate} from '@vue/compiler-sfc'
import * as Vue from 'vue'

// 编译并挂载实际页面模板和计时组件，覆盖脚本绑定与组件标签同名的渲染错误。
function component(path, dependencies = {}) {
  const filename = new URL(path, import.meta.url).pathname
  const {descriptor} = parse(readFileSync(new URL(path, import.meta.url), 'utf8'), {filename})
  const compiled = compileScript(descriptor, {
    id: path, genDefaultAs: '__component', inlineTemplate: true
  })
  let source = compiled.content
  if (!descriptor.scriptSetup) {
    const template = compileTemplate({id: path, filename, source: descriptor.template.content})
    assert.deepEqual(template.errors, [])
    source += '\n' + template.code.replace('export function render', 'function render') + '\n__component.render = render'
  }
  source = source.replace(/import \{([^}]+)\} from ['"]vue['"];?/g,
    (_, names) => `const {${names.replace(/\bas\b/g, ':')}} = Vue`)
    .replace(/^\s*import .*$/gm, '')
  const context = vm.createContext({Vue, window: {interfaceStyle: 'HJJ'}, clearInterval, ...dependencies})
  vm.runInContext(source + '\nglobalThis.component = __component', context)
  return context.component
}

const node = (type, text = '') => ({type, text, children: [], props: {}, style: {}, parent: null})
const renderer = Vue.createRenderer({
  createElement: type => node(type), createText: text => node('text', text), createComment: () => node('comment'),
  setText: (target, text) => { target.text = text },
  setElementText: (target, text) => { target.text = text; target.children = [] },
  patchProp: (target, key, previous, value) => { target.props[key] = value },
  insert(target, parent, anchor = null) {
    if (target.parent) this.remove(target)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, target)
    target.parent = parent
  },
  remove(target) {
    const index = target.parent?.children.indexOf(target) ?? -1
    if (index >= 0) target.parent.children.splice(index, 1)
    target.parent = null
  },
  parentNode: target => target.parent,
  nextSibling: target => target.parent?.children[target.parent.children.indexOf(target) + 1] ?? null
})
const text = target => target.text + target.children.map(text).join('')
const CountDown = component('../src/components/common/CountDown.vue')
const TrainLeft = {setup: (_, {slots}) => () => Vue.h('aside', [slots.top?.(), slots.bottom?.()])}
const NipMagicSpace = {setup: (_, {slots}) => () => Vue.h('main', [slots.one?.(), slots.two?.()])}

for (const kind of ['datagramTrain', 'telexTrain']) {
  test(`个人岗位${kind === 'datagramTrain' ? '数据报' : '电传'}：计时器可挂载和更新，数据回填不产生空组件警告`, async t => {
    const trainData = Vue.ref({status: 0, content: [], speed: 0, duration: 0, groupNumber: 100})
    let clock
    const page = component(`../src/views/manage/postJob/datagram/${kind}/Index.vue`, {
      CountDown, TrainLeft, NipMagicSpace, iconImage: () => ({}),
      telexTrain(countDown, loading) {
        clock = countDown
        loading.value = false
        return {
          trainData, page: Vue.ref({current: 1, pageAll: 1}), pageCodes: Vue.ref(['']),
          code: Vue.ref([]), compileCode: Vue.ref([[]]), compileCodePage: Vue.ref([]), activeIndex: Vue.ref(0),
          isCountdown: Vue.ref(false), duration: Vue.ref(120), ready: Vue.ref(true), inputLocked: Vue.ref(true)
        }
      }
    })
    const app = renderer.createApp(page)
    app.component('CountDown', CountDown)
    for (const name of ['a-spin', 'a-alert', 'a-button', 'a-popconfirm', 'a-switch', 'a-input-number', 'a-textarea']) {
      app.component(name, {setup: (_, {slots}) => () => Vue.h('div', slots.default?.())})
    }
    // 错误绑定会触发渲染循环，首条警告即失败，避免回归测试本身被循环拖住。
    app.config.warnHandler = warning => { throw new Error(warning) }
    const root = node('root')
    t.after(() => app.unmount())
    app.mount(root)
    await Vue.nextTick()
    const mountedClock = clock.value
    assert.equal(typeof mountedClock.autoSetTimeAdd, 'function')

    // 模拟 hydrate 回填和随后的250ms计时更新，组件实例应持续可用。
    Object.assign(trainData.value, {status: 1, speed: 120, duration: 65})
    for (const seconds of [65, 64, 63]) {
      clock.value.autoSetTimeAdd(seconds)
      await Vue.nextTick()
      assert.equal(clock.value, mountedClock)
      assert.equal(clock.value.m, '01')
      assert.equal(clock.value.s, String(seconds - 60).padStart(2, '0'))
    }
    // 电传默认展示改错提示，切换到速率面板后验证可见单位。
    if (kind === 'telexTrain') {
      const find = target => target.props.class?.includes('tipBtn') ? target : target.children.map(find).find(Boolean)
      find(root).props.onClick()
      await Vue.nextTick()
    }
    assert.match(text(root), /120码\/分/)
    assert.match(text(root), /速率/)
    assert.doesNotMatch(text(root), /预估速度/)
  })
}
