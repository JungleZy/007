import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse, compileScript} from '@vue/compiler-sfc'
import {ref} from 'vue'

const filename = new URL('../src/views/manage/organization/datagramZuXun/train/student/student.vue', import.meta.url)
const {descriptor} = parse(readFileSync(filename, 'utf8'))
const compiled = compileScript(descriptor, {id: 'datagram-student', genDefaultAs: 'component'})

function setup(status) {
  const unmount = [], removed = [], unsubscribed = [], storage = new Map()
  let trainData
  const dependencies = {
    ref, onMounted() {}, onUnmounted: callback => unmount.push(callback), defineEmits() {},
    useRoute: () => ({query: {id: 'train'}}),
    PubSub: {unsubscribe: topic => unsubscribed.push(topic)}, iconImage: () => ({}),
    details: data => {
      trainData = data
      return {pageCodes: ref(['1234']), currPatKeyIndex: ref(0)}
    },
    getDatagramDetail() {}
  }
  // 保留实际 import 的绑定名，避免移除 import 后意外使用原生 Number 掩盖命名冲突。
  const source = compiled.content.replace(/^\s*import\s+(.+?)\s+from\s+['"]([^'"]+)['"];?/gm, (_, binding, path) => {
    if (path === 'vue') return `const {${binding.slice(1, -1)}} = dependencies`
    if (binding.startsWith('{')) return `const ${binding} = dependencies`
    if (binding === 'details' || binding === 'iconImage') return `const ${binding} = dependencies.${binding}`
    return `const ${binding} = {}`
  })
  const context = vm.createContext({dependencies,
    localStorage: {getItem: () => JSON.stringify({id: 'student'})},
    window: {
      addEventListener() {}, removeEventListener: name => removed.push(name),
      localStorage: {setItem: (key, value) => storage.set(key, JSON.parse(value))}
    }
  })
  vm.runInContext(source + '\ncomponent.setup({}, {expose() {}, emit() {}})', context)
  Object.assign(trainData.value, {trainId: 'train', status, floorNow: 1, validTime: 10, speed: 24})
  return {unmount: () => unmount.forEach(callback => callback()), storage, removed, unsubscribed}
}

for (const status of [1, '1', 2]) {
  test(`学员训练页状态 ${JSON.stringify(status)} 卸载时正常保存快照并清理监听`, () => {
    const h = setup(status)
    assert.doesNotThrow(h.unmount)
    assert.deepEqual(h.removed, ['beforeunload'])
    assert.deepEqual(h.unsubscribed, ['message'])
    if (Number(status) === 1) {
      assert.deepEqual(h.storage.get('datagramZuXuntrain'), {
        patPage: 1, patKeyIndex: 0, time: 10, speed: 24, pageCodes: ['1234']
      })
    } else {
      assert.equal(h.storage.size, 0)
    }
  })
}
