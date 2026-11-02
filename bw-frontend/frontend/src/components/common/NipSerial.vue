<template>
	<div class="equipment">
		<a-tooltip>
			<template #title
			>
				<div style="font-size: 12px">{{ wsOnline ? '报训软件已连接' : '报训软件未连接' }}</div>
			</template
			>
			<div class="equipment2 equActive" v-if="wsOnline" @click="linkWsOnInfo"></div>
			<div class="equipment1" v-else @click="linkWsOnInfo"></div>
		</a-tooltip>
		<a-tooltip>
			<template #title>
        <div style="font-size: 12px">{{ devOnline ? '拍发设备已连接（' + (serialSource === 'virtual' ? '虚拟设备' : '真实设备') + '）' : '拍发设备未连接' }}，点击切换</div>
			</template>
      <img class="equipment4 equActive" :src="ico_state_dev_on" v-if="devOnline" alt="切换拍发设备" @click="linkWsOnInfo"/>
      <img class="equipment3" :src="ico_state_dev" v-else alt="连接拍发设备" @click="linkWsOnInfo"/>
		</a-tooltip>
		<div v-if="serialShow" class="serialBox">
      <div class="w-full h-full">
				<div style="height: 30px" class="layout-side-n">
          <div>{{ devOnline ? '当前：' + (serialSource === 'virtual' ? '虚拟设备' : '真实设备') : '选择拍发设备' }}</div>
					<CloseOutlined @click="serialShow=false"/>
				</div>
        <div style="width: 100%;height: calc(100% - 30px);overflow: auto">
          <button class="sourceButton" :disabled="serialConnecting" @click="selectRealDevice">{{ ipc ? '刷新真实串口列表' : '选择真实设备' }}</button>
          <button class="sourceButton" :disabled="serialConnecting || !virtualAvailable" @click="selectVirtualDevice">切换到虚拟设备</button>
          <div class="grantNotice" v-if="!virtualAvailable">虚拟设备需先连接模拟器</div>
          <div class="grantNotice" v-if="serialConnecting">正在连接设备...</div>
          <div class="grantNotice" v-if="serialNotice">{{ serialNotice }}</div>
          <div class="grantNotice" v-if="loading">搜索串口中...</div>
          <div class="grantNotice" v-else-if="ipc && !serialProtList.length">未发现真实串口，请检查设备连接后刷新</div>
          <div v-if="ipc && localSerial">已选串口：{{ localSerial }}</div>
					<div class="listItem" v-for="(v,index) of serialProtList" :key="index">
						<div class="listItemName" @click="linkPort(v, true)">{{ v.path ? v.path : v }}</div>
						<div class="listItemGrant" v-if="v && v.accessible === false">
							<span class="grantTag">需要授权</span>
							<span class="grantBtn" :class="{grantBtnBusy: granting}" @click.stop="grantAccess(v)">授权</span>
						</div>
					</div>
					<div class="grantNotice" v-if="grantNotice">{{ grantNotice }}</div>
				</div>
			</div>
		</div>
	</div>
</template>
<script setup>
import {CloseOutlined} from "@ant-design/icons-vue";
import ico_state_ws from '../../assets/LJ/ico/ico-state-ws.png'
import ico_state_ws_on from '../../assets/LJ/ico/ico-state-ws-on.png'
import ico_state_dev from '../../assets/LJ/ico/ico-state-dev.png'
import ico_state_dev_on from '../../assets/LJ/ico/ico-state-dev-on.png'
import {ref, onMounted, onUnmounted} from "vue";
import {ipcApi, ipcRenderer} from "../../electron";
import webSerialChannel, {shutdownWebSerialChannel, serialSource, serialConnecting, serialNotice} from "../../common/ws/WebSerialChannel";
import useTraffic from "../../common/mixin/useTraffic";

let ico_state_wsLJ = ico_state_ws, ico_state_ws_onLJ = ico_state_ws_on,
	ico_state_devLJ = ico_state_dev, ico_state_dev_onLJ = ico_state_dev_on

const ipc = ref(ipcRenderer.isEE)
const serialProtList = ref([])//桌面端串口列表
const serialShow = ref(false)
const localSerial = ref(localStorage.getItem('serial'))
const {wsOnline, devOnline} = useTraffic()
const loading = ref(false)
const granting = ref(false)
const grantNotice = ref('')
const virtualAvailable = ref(false)
const updateVirtualAvailable = () => {
  virtualAvailable.value = !!(window.__keysimSerial || sessionStorage.getItem('keysimSerialOrigin'))
}
let mounted = false

onMounted(() => {
  mounted = true
  updateVirtualAvailable()
  window.addEventListener('keysim-serial-ready', updateVirtualAvailable)
	if (ipc.value) {
		ipcRenderer.ipc.on(ipcApi.ipcApiRoute.getSerialPorts, (event, data) => {
			serialProtList.value = data
      loading.value = false
			localSerial.value = localStorage.getItem('serial')
		})
		// 授权是异步路由：pkexec 会等用户输密码，用 sendSync 会把渲染进程冻住。
		ipcRenderer.ipc.on(ipcApi.ipcApiRoute.grantAccess, (event, result) => {
			granting.value = false
			const ok = !!(result && result.ok)
			const detail = result && result.message ? result.message : (ok ? '授权成功' : '授权失败')
			grantNotice.value = `${detail}。加入 dialout 组需要重新登录（注销后重新登录或重启）才会生效。`
			if (ok) {
				loading.value = true
				ipcRenderer.ipc.send(ipcApi.ipcApiRoute.getSerialPorts)
			}
		})
	}
  if (ipc.value && localSerial.value !== null) {
    linkPort(localSerial.value)
  } else {
    // 首次使用桌面端也需要监听模拟器就绪事件。
    webSerialChannel()
  }
})

const linkWsOnInfo = () => {
  serialShow.value = !serialShow.value
  if (!serialShow.value) return
  updateVirtualAvailable()
  if (ipc.value) selectRealDevice()
}

const selectRealDevice = () => {
  if (serialConnecting.value) return
	//桌面端
	if (ipc.value) {
		loading.value = true
		ipcRenderer.ipc.send(ipcApi.ipcApiRoute.getSerialPorts)

	} else {
		localStorage.setItem('serialChrome', true)
		// This branch is reached by the user's device click.  Mark it as the
		// explicit permission path; refresh/reconnect paths must never call
		// navigator.serial.requestPort() without a user gesture.
    webSerialChannel('reset', 'resetLine', 'real')
  }
}
const selectVirtualDevice = () => {
  if (serialConnecting.value) return
  webSerialChannel('reset', undefined, 'virtual')
}
//连接串口
const linkPort = async (portName, select = false) => {
  if (!ipc.value || serialConnecting.value) return
  try {
    const name = portName.path ? portName.path.split(/[\\/]/).pop() : portName
    const data = await ipcRenderer.ipc.invoke(ipcApi.ipcApiRoute.linkPort, name)
    if (!mounted) return
    if (data) {
      localStorage.setItem('serial', data)
      localSerial.value = data
      await webSerialChannel('reset', select ? 'resetLine' : undefined, select ? 'real' : undefined)
    } else serialNotice.value = '串口选择失败，请重试'
  } catch (error) {
    serialNotice.value = `串口连接失败：${error.message}`
  }
}
//串口无读写权限时由用户点击显式提权
const grantAccess = (port) => {
	if (granting.value) return
	granting.value = true
	grantNotice.value = '正在申请授权，请在系统提权框中确认...'
	ipcRenderer.ipc.send(ipcApi.ipcApiRoute.grantAccess, port && port.path ? [port.path] : [])
}

// 串口通道的清理必须在 setup 同步期注册：linkPort 走 ipc.invoke，重置通道发生在 await 之后，
// 那时注册 onUnmounted 会被 Vue 丢弃（组件卸载后串口不关）。
onUnmounted(() => {
  mounted = false
  window.removeEventListener('keysim-serial-ready', updateVirtualAvailable)
	shutdownWebSerialChannel()
	if (ipc.value) {
		ipcRenderer.ipc.off(ipcApi.ipcApiRoute.getSerialPorts)
		ipcRenderer.ipc.off(ipcApi.ipcApiRoute.grantAccess)
	}
})

</script>
<style scoped lang="less">


.equActive {
	position: relative;

	&:after {
		content: '';
		position: absolute;
		width: 62px;
		height: 36px;
		background: url('../../assets/HJJ/home/equActiveBG.png');
		bottom: -5px;
		left: -22px;
	}
}

.sourceButton {
  display: block;
  width: 100%;
  padding: 8px 5px;
  margin-bottom: 4px;
  border: 1px solid #667b96;
  border-radius: 3px;
  color: #fff;
  background: #142547;
  cursor: pointer;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

// 串口列表项的授权信号：各主题皮肤共用，不随 .HJJ/.KJ/.HJ 重复定义
.listItem {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 6px;
}

.listItemName {
	flex: 1;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.grantTag {
	color: #ffb020;
	font-size: 12px;
	margin-right: 4px;
}

.grantBtn {
	display: inline-block;
	padding: 1px 6px;
	font-size: 12px;
	border: 1px solid #ffb020;
	border-radius: 3px;
	color: #ffb020;
	cursor: pointer;
}

.grantBtnBusy {
	opacity: 0.5;
	cursor: not-allowed;
}

.grantNotice {
	margin-top: 6px;
	padding: 4px 5px;
	font-size: 12px;
	line-height: 16px;
	color: #ffd591;
	background: rgba(20, 37, 71, 0.6);
	border-radius: 3px;
	white-space: normal;
}

.HJJ,.LJ,.GD{
  .equipment {
    position: absolute;
    right: 16%;
    top: 11px;
    background: url('../../assets/HJJ/home/equipmentType.png');
    width: 158px;
    height: 37px;
    display: flex;
    padding: 0 30px;
    justify-content: space-between;
    align-items: center;

    .serialBox {
      position: fixed;
      width: 240px;
      height: 280px;
      background: #2e2f31;
      z-index: 9999;
      top: 47px;
      right: 307px;
      overflow: auto;
      padding: 10px;
      border-bottom-right-radius: 6px;
      border-bottom-left-radius: 6px;

      .listItem {
        padding: 10px 5px;
        cursor: pointer;
        background: rgba(20, 37, 71, 1);
        margin-bottom: 2px;
        border-radius: 3px;
      }

      .listItem:hover {
        background: rgba(20, 37, 71, 0.5)
      }
    }

    img {
      height: 20px;
      width: 20px;
      cursor: pointer;
      position: relative;
    }

    .equipment1 {
      background: url('../../assets/HJJ/ico/ico-state-ws.png');
      width: 20px;
      height: 20px;
    }

    .equipment2 {
      background: url('../../assets/HJJ/ico/ico-state-ws-on.png');
      width: 20px;
      height: 20px;
    }

    .equipment3 {
      background: url('../../assets/HJJ/ico/ico-state-dev.png');
      width: 20px;
      height: 20px;
    }

    .equipment4 {
      background: url('../../assets/HJJ/ico/ico-state-dev-on.png');
      width: 20px;
      height: 20px;
    }
  }
}

.KJ{
  .equipment {
    position: absolute;
    right: 16%;
    top: 11px;
    background: url('../../assets/KJ/home/equipmentType.png');
    width: 158px;
    height: 37px;
    display: flex;
    padding: 0 30px;
    justify-content: space-between;
    align-items: center;

    .serialBox {
      position: fixed;
      width: 240px;
      height: 280px;
      background: #2e2f31;
      z-index: 9999;
      top: 47px;
      right: 307px;
      overflow: auto;
      padding: 10px;
      border-bottom-right-radius: 6px;
      border-bottom-left-radius: 6px;

      .listItem {
        padding: 10px 5px;
        cursor: pointer;
        background: rgba(20, 37, 71, 1);
        margin-bottom: 2px;
        border-radius: 3px;
      }

      .listItem:hover {
        background: rgba(20, 37, 71, 0.5)
      }
    }

    img {
      height: 20px;
      width: 20px;
      cursor: pointer;
      position: relative;
    }

    .equipment1 {
      background: url('../../assets/KJ/ico/ico-state-ws.png');
      width: 20px;
      height: 20px;
    }

    .equipment2 {
      background: url('../../assets/KJ/ico/ico-state-ws-on.png');
      width: 20px;
      height: 20px;
    }

    .equipment3 {
      background: url('../../assets/KJ/ico/ico-state-dev.png');
      width: 20px;
      height: 20px;
    }

    .equipment4 {
      background: url('../../assets/KJ/ico/ico-state-dev-on.png');
      width: 20px;
      height: 20px;
    }
  }
}
  .HJ{
    .equipment {
      position: absolute;
      right: 45%;
      top: 2px;
      width: 132px;
      height: 37px;
      display: flex;
      padding: 0 30px;
      justify-content: space-between;
      align-items: center;

      .serialBox {
        position: fixed;
        width: 240px;
        height: 280px;
        background: #2e2f31;
        z-index: 9999;
        top: 47px;
        right: 307px;
        overflow: auto;
        padding: 10px;
        border-bottom-right-radius: 6px;
        border-bottom-left-radius: 6px;

        .listItem {
          padding: 10px 5px;
          cursor: pointer;
          background: rgba(20, 37, 71, 1);
          margin-bottom: 2px;
          border-radius: 3px;
        }

        .listItem:hover {
          background: rgba(20, 37, 71, 0.5)
        }
      }

      img {
        height: 20px;
        width: 20px;
        cursor: pointer;
        position: relative;
      }

      .equipment1 {
        background: url('../../assets/HJJ/ico/ico-state-ws.png');
        width: 20px;
        height: 20px;
      }

      .equipment2 {
        background: url('../../assets/HJJ/ico/ico-state-ws-on.png');
        width: 20px;
        height: 20px;
      }

      .equipment3 {
        background: url('../../assets/HJJ/ico/ico-state-dev.png');
        width: 20px;
        height: 20px;
      }

      .equipment4 {
        background: url('../../assets/HJJ/ico/ico-state-dev-on.png');
        width: 20px;
        height: 20px;
      }
    }
  }


</style>
