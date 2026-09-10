<template>
  <div class="w-full h-full relative transition-page animate__animated animate__zoomIn" v-if="route.matched.length === 3">
    <div class="w-full h-full absolute overflow-hidden" style="z-index: 1" id="numRain" v-if="cool"></div>
    <div class="w-full h-full absolute layout-center" v-if="atRoute.children.length === 0">
      <img :src="development" />
    </div>
    <div class="w-full h-full absolute" style="z-index: 2">
      <div class="w-full h-full overflow-hidden layout-center">
        <div class="pr-5 pl-5 layout-center" v-for="(r, index) in atRoute.children">
          <div class="layout-center relative" style="width: 280px; height: 524px">
            <div @mouseenter="handleMenuMouse(index)" class="w-full h-full animate__animated animate__zoomIn" :style="{ background: 'url(' + (cool ? mmb : mmbp) + ')' }">
              <div class="w-full h-full layout-center">
                <div v-show="hoverMenu !== index" class="w-full" style="font-size: 30px; font-weight: bold; color: #6ebdff">
                  <div class="w-full layout-center">{{ r.meta.title }}</div>
                </div>
              </div>
            </div>
            <div v-show="hoverMenu === index" @mouseleave="handleMenuMouse(-1)" class="w-full h-full absolute" :style="{ background: 'url(' + mmbm + ')' }">
              <div class="w-full h-full layout-center" v-if="r.children.length > 0">
                <template v-for="(m, key) in r.children">
                  <div class="w-full layout-center p-3" v-if="m.meta.isMenu" :style="{ height: 'calc(100% / ' + calNum(r) + ')' }">
                    <div class="w-full h-full layout-center cursor-pointer-def menu animate__animated animate__zoomIn" :style="{ background: 'url(' + mb + ')  center center / contain no-repeat' }" @click="handleMenuClick(r, m, key)" style="font-size: 26px; font-weight: bold; color: #e2f2ff">
                      {{ m.meta.title }}
                    </div>
                  </div>
                </template>
              </div>
              <div class="w-full h-full layout-center" v-else>功能正在建设中</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <div class="w-full h-full animate__animated animate__zoomIn" v-else>
    <!--    返回按钮需要用到-->
    <breadCru v-show="false"></breadCru>
    <router-view style="height: calc(100% - 0px)" />
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ref, onMounted, watch, inject } from 'vue'
import breadCru from '../components/breadCrumb/Index.vue'
import store from '../config/store/index'
import mmb from '../assets/HJ/TransitionPage/menu-mask-bg.gif'
import mmbp from '../assets/HJ/TransitionPage/menu-mask-bg2.png'
import mmbm from '../assets/HJ/TransitionPage/menu-mask-bg-movein.png'
import mb from '../assets/HJ/TransitionPage/menu-bg.png'
import mbh from '../assets/HJ/TransitionPage/menu-bg-hover.png'
import useNumRain from '../common/utils/useNumRain.js'
import development from '../assets/HJ/test/development.png'

const route = useRoute()
const router = useRouter()
const cool = inject('cool')
const atRoute = ref({
  children: []
})
const hoverMenu = ref(-1)

if (route.matched.length === 3) {
  router.getRoutes().forEach(r => {
    if (r.path === route.fullPath) {
      atRoute.value = r
    }
  })
}
onMounted(() => {
  if (route.matched.length === 3 && window.cool) {
    useNumRain('numRain')
  }
})
watch(route, () => {
  router.getRoutes().forEach(r => {
    if (r.path === route.fullPath) {
      atRoute.value = r
    }
  })
  setTimeout(() => {
    useNumRain('numRain')
  }, 200)
})
watch(cool, () => {
  if (cool) {
    setTimeout(() => {
      useNumRain('numRain')
    }, 200)
  }
})
const handleMenuMouse = e => {
  hoverMenu.value = e
}
const userRole = localStorage.getItem('userRole')
const handleMenuClick = (r, m, key) => {
  if (r.path === 'theoryStudy') {
    router.push({
      path: atRoute.value.path + '/' + r.path + '/' + m.path +'/basicTheoryList',
      query: {
        studyType: key
      }
    })
  } else {
    router.push(atRoute.value.path + '/' + r.path + '/' + m.path)
  }
}
const calNum = e => {
  let num = 0
  e.children.forEach(c => {
    if (c.meta.isMenu) {
      num++
    }
  })
  return num
}
</script>

<style scoped>
.animate__animated.animate__zoomIn {
  animation-duration: 0.3s;
  backface-visibility: hidden;
}

.transition-page .menu:hover {
  background: url('../assets/HJ/TransitionPage/menu-bg-hover.png') center center / contain no-repeat !important;
  color: #6ebdff !important;
}
</style>
