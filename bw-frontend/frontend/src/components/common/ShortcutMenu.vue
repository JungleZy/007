<template>
  <div class="shortcut-menu" :style="styleTag">
    <div class="layout-side relative">
      <div class="layout-side menu" :style="{flexDirection: isLeft?'':'row-reverse'}" ref="floatDrag"
           @mousedown="handleOpenMenuDown"
           @mouseup="handleOpenMenuUp">
        <div class="menu-bg" :style="{transform: isLeft?'scale(-1,1)':'scale(1,1)'}">

        </div>
        <div class="shortcut-menu-icon layout-center">
        </div>
        <div class="shortcut-menu-span "
             :class="[isLeft?'span-left':'span-right',isLeft?'layout-left-center':'layout-right-center']">
          <div class="layout-right-center" style="width: 150px;height: 20px">远程：{{ serverTime }}</div>
          <div class="layout-right-center" style="width: 150px;height: 20px">本机：{{ localTime }}</div>
        </div>
      </div>
      <div class="shortcut-menu-context" :style="{left: isLeft?'18px':'12px'}"
           :class="[isTop?'context-top':'context-bottom']" v-show="isOpenMenu">
        <div class="context-title layout-side">
          <div class="context-home layout-center" @click="topButton(0)">
            <HomeFilled/>
          </div>
          <div class="context-setting layout-center" @click="topButton(1)"><img src="../../assets/HJ/shortcut/s.png"></div>
        </div>
        <div class="w-full context-center">
          <div class="item" v-for="m in menus">
            <div class="children layout-center w-full h-full" @click="skipMenu(m)">
              {{ m.title }}
            </div>
          </div>
        </div>
      </div>
      <div class="shortcut-menu-shrink" v-show="isShrink"></div>
    </div>
  </div>
</template>

<script>
export default {
  name: "ShortcutMenu"
}
</script>
<script setup>
import {ref, onMounted} from 'vue'
import {MenuOutlined, SettingOutlined, HomeFilled} from '@ant-design/icons-vue'
import {useDraggable, useDateFormat, useNow} from '@vueuse/core'
import {getNowTime} from '../../common/api/ToolsApi.js'
import {useRouter} from 'vue-router'

const router = useRouter()
const isOpenMenu = ref(false)
const floatDrag = ref(null)
const styleTag = ref("left:0px;top:62px;")
const moveDis = ref({x: 0, y: 62})
const serverTime = ref('')
const localTime = ref(useDateFormat(useNow(), 'YYYY/MM/DD HH:mm:ss').value)
const constTime = ref(100)
const menus = ref([
  {title: '个人岗前', path: ''},
])
const isShrink = ref(false)
const isLeft = ref(false)
const isTop = ref(false)

const shortcutMenus = localStorage.getItem("shortcutMenu")
if (shortcutMenus) {
  menus.value = JSON.parse(shortcutMenus)
} else {
  menus.value = [
    {title: '综合分析', path: 'basicTheoretical/studyManagement/comprehensiveAnalyze'},
    {title: '综合测试', path: 'basicTheoretical/theoryTest/theoryTestt/theoryTestList'},
    {title: '岗前收报', path: 'basicSkill/preJob/receive/receiveExplain'},
    {title: '岗前发报', path: 'basicSkill/preJob/telegram/focusExplain'},
    {title: '岗位收报', path: 'basicSkill/postJob/receive/receivePostPractise'},
    {title: '岗位发报', path: 'basicSkill/postJob/telegram/handKeyPostJob'},
  ]
}
getNowTime().then((e) => {
  serverTime.value = useDateFormat(e.data, 'YYYY/MM/DD HH:mm:ss').value
  startServerTiming()
  startLocalTiming()
})
const startServerTiming = () => {
  if (constTime.value <= 100) {
    setTimeout(() => {
      serverTime.value = useDateFormat(dayjs(new Date(serverTime.value)).add(1, "second").toDate(), 'YYYY/MM/DD HH:mm:ss').value
      startServerTiming()
    }, 1000)
  } else {
    constTime.value = 0
    getNowTime().then((e) => {
      serverTime.value = useDateFormat(e.data, 'YYYY/MM/DD HH:mm:ss').value
      startServerTiming()
    })
  }
  constTime.value++
}
const startLocalTiming = () => {
  setTimeout(() => {
    localTime.value = useDateFormat(useNow(), 'YYYY/MM/DD HH:mm:ss').value
    startLocalTiming()
  }, 1000)

}
let w = document.body.clientWidth - 250
let h = document.body.clientHeight - 71
styleTag.value = `left:${w}px;top:${h}px;`
moveDis.value = {x: w, y: h}
const {x, y, style} = useDraggable(floatDrag, {
  initialValue: {x: w, y: h},
  onMove: () => {
    styleTag.value = style.value
    isTop.value = y.value <= ((document.body.clientHeight - 61) / 2);
    isLeft.value = x.value <= ((document.body.clientWidth - 240) / 2);
  },
  onEnd: () => {
    let left = x.value < 0 ? 0 : x.value > document.body.clientWidth - 240 ? document.body.clientWidth - 240 : x.value
    let right = y.value < 0 ? 0 : y.value > document.body.clientHeight - 61 ? document.body.clientHeight - 61 : y.value

    styleTag.value = `left:${left}px;top:${right}px;`
  }
})
const handleOpenMenuDown = () => {
  moveDis.value = {
    x: x.value, y: y.value
  }
}
const handleOpenMenuUp = () => {
  if (Math.abs(moveDis.value.x - x.value) < 1 && Math.abs(moveDis.value.y - y.value) < 1) {
    isOpenMenu.value = !isOpenMenu.value
  }
}
const skipMenu = (m) => {
  router.push(`/preview/${m.path}`)
}
const topButton = (t) => {
  if (t === 0) {
    router.push("/preview/dashboard");
  } else {

  }
}
</script>

<style scoped lang="less">
.shortcut-menu {
  width: 240px;
  height: 61px;
  z-index: 999999 !important;
  position: fixed;

  .menu {
    position: relative;
    z-index: 9;

    .menu-bg {
      position: absolute;
      top: 0;
      bottom: 0;
      left: 0;
      right: 0;
      background-image: url('../../assets/HJ/shortcut/menu.png');
    }

    .shortcut-menu-icon {
      width: 61px;
      height: 61px;
      cursor: pointer;
      z-index: 9;
    }

    .shortcut-menu-span {
      cursor: pointer;
      z-index: 8;
      font-size: 12px;
      width: 179px;
      height: 61px;
    }

    .span-left {
      padding: 10px 0 10px 10px;
    }

    .span-right {
      padding: 10px 10px 10px 0;
    }
  }


  .context-top {
    top: 54px;
    padding: 0 10px 0 10px;
  }

  .context-bottom {
    top: -155px;
    padding: 0 10px 10px 10px;

  }

  .shortcut-menu-context {
    width: 210px;
    height: 162px;
    background-image: url("../../assets/HJ/shortcut/list.png");
    position: absolute;

    .context-title {
      height: 32px;

      .context-home {
        cursor: pointer;
        color: #92d7fc;
        font-weight: bolder;
        height: 30px;
        width: 30px;
      }

      .context-setting {
        cursor: pointer;
        height: 30px;
        width: 30px;
      }
    }

    .context-center {
      height: 120px;
      overflow: auto;

      .item {
        cursor: pointer;
        width: 50%;
        height: 40px;
        float: left;
        padding: 4px 2px;

        .children {
          border-radius: 4px;
          background-image: url('../../assets/HJ/shortcut/bg.png')
        }

        .children:hover {
          background-image: url('../../assets/HJ/shortcut/bc.png')
        }
      }
    }

  }

  .shortcut-menu-shrink {
    position: absolute;
    height: 61px;
    width: 8px;
    right: 0;
    top: 0;
    background: green;
    z-index: 99;
    border-top-right-radius: 6px;
    border-bottom-right-radius: 6px;
    cursor: pointer;
  }
}
</style>