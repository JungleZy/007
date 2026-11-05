<template>
  <section class="pat-record-panel" :aria-label="title">
    <div class="record-heading"><strong>{{ title }}</strong><span>{{ strokes.length }} 次按键</span></div>
    <div v-if="!strokes.length" class="record-empty">暂无拍发记录</div>
    <template v-else>
      <div ref="wave" class="record-wave" tabindex="0" aria-label="可横向滚动的拍发波形" @wheel="scrollWave">
        <div class="wave-track">
          <template v-for="(stroke, index) in strokes" :key="index">
            <span class="wave-gap" :style="{width: Math.min(stroke.gap / 10, 200) + 'px'}" :title="`间隔 ${stroke.gap}ms`"></span>
            <span class="wave-stroke" :data-stroke-index="index" :class="{'is-selected': selected && index >= selected.startStroke && index <= selected.endStroke}" :style="{width: Math.max(3, Math.min(stroke.diff / 10, 200)) + 'px'}"
              :title="`第${index + 1}次 · 第${stroke.pageNumber}页 · 按下${stroke.diff}ms · 间隔${stroke.gap}ms`"></span>
          </template>
        </div>
      </div>
      <div class="decode-settings">
        <strong>系统译码</strong>
        <label><input v-model="autoCalibrate" type="checkbox" />自动校准</label>
        <label>点基准(ms)<input :value="Number(effectiveStandard.dot.toFixed(1))" type="number" min="11" max="5000" step="0.1" @change="setStandard('dot', $event.target.value)" /></label>
        <label>码间隔(ms)<input :value="Number(effectiveStandard.codeGap.toFixed(1))" type="number" min="11" max="10000" step="0.1" @change="setStandard('codeGap', $event.target.value)" /></label>
        <label>偏移(%)<input v-model.number="skew" type="number" min="0" max="100" /></label>
      </div>
      <div ref="decodedBox" class="decoded-record" tabindex="0" aria-label="系统译码结果">
        <div v-for="group in decodedGroups" :key="group[0].startStroke" class="decoded-group" role="group" :aria-label="group.map(item => item.character).join('')">
          <button v-for="item in group" :key="item.startStroke" type="button" class="decoded-character"
            :class="{unknown: item.unknown, 'is-selected': selected?.startStroke === item.startStroke && selected?.endStroke === item.endStroke}"
            :aria-label="`第${item.pageNumber}页 ${item.character}，定位拍发记录`"
            :aria-pressed="selected?.startStroke === item.startStroke && selected?.endStroke === item.endStroke"
            :title="`第${item.pageNumber}页 · ${item.code.replaceAll('0', '·').replaceAll('1', '－')}`" @click="locateCharacter(item)">
            {{ item.character }}
          </button>
        </div>
        <span v-if="!decoded.length">暂无可译码的有效按键</span>
      </div>
    </template>
  </section>
</template>

<script setup>
import {computed, nextTick, ref, watch} from 'vue'
import {decodePatRecord, inferPatStandard} from '../js/decodePatRecord.js'
import {DEFAULT_HAND_KEY_STANDARD, DEFAULT_HAND_KEY_SKEW} from '../../../../../common/utils/handKeyTiming.js'
const props = defineProps({strokes: {type: Array, default: () => []}, bwType: {type: Number, default: 1}, title: {type: String, default: '我的拍发记录'}, followLatest: {type: Boolean, default: false}})
const dot = ref(DEFAULT_HAND_KEY_STANDARD.dot)
const codeGap = ref(DEFAULT_HAND_KEY_STANDARD.codeGap)
const skew = ref(DEFAULT_HAND_KEY_SKEW)
const autoCalibrate = ref(true)
const inferredStandard = computed(() => inferPatStandard(props.strokes || []))
const effectiveStandard = computed(() => autoCalibrate.value ? inferredStandard.value : {
  dot: Math.max(11, Number(dot.value) || DEFAULT_HAND_KEY_STANDARD.dot),
  codeGap: Math.max(11, Number(codeGap.value) || DEFAULT_HAND_KEY_STANDARD.codeGap)
})
const setStandard = (field, value) => {
  const current = effectiveStandard.value
  dot.value = current.dot
  codeGap.value = current.codeGap
  autoCalibrate.value = false
  if (field === 'dot') dot.value = Number(value)
  else codeGap.value = Number(value)
}
const wave = ref(null)
const decodedBox = ref(null)
const selected = ref(null)
const decoded = computed(() => decodePatRecord(props.strokes, {
  bwType: props.bwType,
  standard: effectiveStandard.value,
  skew: Math.max(0, Math.min(100, Number(skew.value) || 0))
}))
const decodedGroups = computed(() => {
  const groups = []
  for (const item of decoded.value) {
    if (!groups.length || item.groupStart) groups.push([])
    groups[groups.length - 1].push(item)
  }
  return groups
})
watch([decoded, () => props.followLatest, decodedBox], async () => {
  await nextTick()
  if (props.followLatest && decodedBox.value) decodedBox.value.scrollTop = decodedBox.value.scrollHeight
}, {immediate: true, flush: 'post'})
const locateCharacter = item => {
  const element = wave.value
  if (!element) return
  const first = element.querySelector(`[data-stroke-index="${item.startStroke}"]`)
  const last = element.querySelector(`[data-stroke-index="${item.endStroke}"]`)
  if (!first || !last) return
  selected.value = {startStroke: item.startStroke, endStroke: item.endStroke}
  // 使用实际渲染位置，兼容长间隔压缩、最小点宽和当前滚动位置。
  const viewportLeft = element.getBoundingClientRect().left + element.clientLeft
  const start = first.getBoundingClientRect().left - viewportLeft + element.scrollLeft
  const end = last.getBoundingClientRect().right - viewportLeft + element.scrollLeft
  const target = end - start > element.clientWidth ? start - 16 : (start + end - element.clientWidth) / 2
  element.scrollTo({left: Math.max(0, Math.min(element.scrollWidth - element.clientWidth, target)), behavior: 'smooth'})
  element.scrollIntoView({block: 'nearest', inline: 'nearest', behavior: 'smooth'})
}
watch([dot, codeGap, skew, autoCalibrate, () => props.bwType], () => { selected.value = null })
const scrollWave = event => {
  const element = wave.value
  if (!element || element.scrollWidth <= element.clientWidth || event.ctrlKey) return
  const previous = element.scrollLeft
  element.scrollLeft += (event.deltaX || event.deltaY) * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? element.clientWidth : 1)
  if (element.scrollLeft !== previous) event.preventDefault()
}
watch(() => props.strokes.length, async () => {
  const element = wave.value
  const follow = !selected.value && (!element || element.scrollWidth - element.clientWidth - element.scrollLeft < 8)
  await nextTick()
  if (follow && !selected.value && wave.value) wave.value.scrollLeft = wave.value.scrollWidth
})
</script>

<style scoped>
.pat-record-panel { min-width: 0; flex-shrink: 0; padding: 10px; border: 1px solid #4c7595; color: #e2f2ff; box-sizing: border-box; }
.record-heading { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
.record-empty { padding: 8px 0; font-size: 12px; }
.record-wave { width: 100%; min-width: 0; overflow-x: auto; overflow-y: hidden; border: 1px solid #4c7595; }
.wave-track { display: flex; align-items: center; width: max-content; min-width: 100%; height: 54px; padding: 0 8px; box-sizing: border-box; }
.wave-gap, .wave-stroke { display: block; flex: 0 0 auto; }
.wave-stroke { height: 18px; background: #e9deb2; border-radius: 2px; }
.wave-stroke.is-selected { background: #70c9ff; outline: 2px solid #fff; }
.decode-settings { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; margin: 10px 0; font-size: 12px; }
.decode-settings input[type="number"] { width: 64px; margin-left: 4px; background: #213141; color: #fff; border: 1px solid #4c7595; }
.decoded-record { max-height: 120px; overflow: auto; display: flex; flex-wrap: wrap; align-content: flex-start; gap: 8px 16px; padding: 6px; border: 1px solid #4c7595; }
.decoded-group { display: inline-flex; flex: 0 0 auto; flex-wrap: nowrap; gap: 0; white-space: nowrap; padding: 4px 8px; border: 1px solid #4c7595; border-radius: 3px; }
.decoded-character { display: inline-block; flex: 0 0 auto; padding: 0; font-family: monospace; font-size: 22px; line-height: 1.4; color: inherit; background: transparent; border: 1px solid transparent; border-radius: 3px; cursor: pointer; }
.decoded-character:hover, .decoded-character:focus-visible, .decoded-character.is-selected { border-color: #70c9ff; background: #36556b; }
.unknown { color: #ff867e; }
</style>
