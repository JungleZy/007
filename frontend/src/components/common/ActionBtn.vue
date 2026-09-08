<template>
	<div
		class="top-close-zoom layout-right-center">
		<a-tooltip placement="top">
			<template #title>
				<div style="font-size: 12px">重载页面</div>
			</template>
			<div
				class="item layout-center iconItemOne"
				@click="reloadWindow"
			></div>
		</a-tooltip>
		<a-tooltip placement="top" v-if="interfaceStyle==='HJ'">
			<template #title>
				<div style="font-size: 12px">字体大小</div>
			</template>
			<div
				class="item layout-center iconItemSix"
				@click="fontSizeVisible = true"
			></div>
		</a-tooltip>
		<a-tooltip placement="top" v-if="ipc && isLogin">
			<template #title>
				<div style="font-size: 12px">网络设置</div>
			</template>
			<div
				id="openSettingWindow"
				class="item layout-center iconItemTwo"
				@click="openSettingWindow"
			></div>
		</a-tooltip>
		<a-tooltip placement="top" v-if="interfaceStyle==='HJ'">
			<template #title>
				<div style="font-size: 12px">
					{{ cool ? '关闭特效' : '开启特效' }}
				</div>
			</template>
			<div
				v-if="cool"
				class="item-red layout-center iconItemThree"
				@click="closeAnimation"
			></div>
			<div
				v-else
				class="item-red layout-center iconItemFour"
				@click="closeAnimation"
			></div>
		</a-tooltip>
		<a-tooltip placement="top" v-if="ipc">
			<template #title>
				<div style="font-size: 12px">关闭软件</div>
			</template>
			<div
				class="item-red layout-center iconItemFive"
				@click="closeWindow"
			></div>
		</a-tooltip>
	</div>
	<a-modal
		:destroyOnClose="true"
		:width="350"
		title="字体大小配置"
		class="init_modal_style footer-border-none"
		v-model:visible="fontSizeVisible"
	>
		<template #footer>
			<div class="layout-right-center">
				<a-button @click="fontSizeVisible = false">取消</a-button>
				<a-button @click="settingFontSize">确定</a-button>
			</div>
		</template>
		<div>
			<div class="fontSizeBox" style="padding: 10px; color: white">
				<a-slider
					v-model:value="fontSizeScale"
					:min="0"
					:max="3"
					:step="1"
					:marks="fontSizeText"
					:tooltipVisible="false"
				></a-slider>
			</div>
			<div
				class="modelText"
				:style="{ fontSize: fontSizeScale * 2 + 13 + 'px', color: '#fff' }"
			>
				<div class="tit">示例</div>
				<div class="cont">
					捍卫祖国领海&nbsp;&nbsp;&nbsp;&nbsp;建设强大海军
				</div>
			</div>
		</div>
	</a-modal>
</template>

<script>
export default {
	name: "ActionBtn"
}
</script>
<script setup>
import useFontSize from "../../common/mixin/useFontSize";
import {createVNode, inject, ref, onMounted} from "vue";
import {ipcRenderer, ipcApi} from '../../electron/index'
import {Modal} from "ant-design-vue";
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";
import {useRoute} from "vue-router";

const fontSizeText = ref({0: '标准', 1: '较大', 2: '大', 3: '特大'})
const ipc = ref(ipcRenderer.isEE)
const {fontSizeScale, fontSizeVisible, reloadWindow, settingFontSize} = useFontSize()
const closeWindow = () => {
	Modal.confirm({
		title: () => '是否确认退出报务综合训练系统？',
		icon: () => createVNode(ExclamationCircleOutlined),
		okText: () => '退出',
		cancelText: () => '取消',
		onOk() {
			ipcRenderer.ipc.invoke(ipcApi.ipcApiRoute.closeApp)
		},
		onCancel() {
		}
	})
}
const interfaceStyle = window.interfaceStyle
const cool = inject('cool')
const openSettingWindow = inject('openSettingWindow')
const closeAnimation = inject('closeAnimation')
const isLogin = ref(false)
const route = useRoute()

onMounted(()=>{
	isLogin.value = route.fullPath.includes("login")
})
</script>

<style scoped lang="less">
.HJ, .GD {
	.setting-modal {
		width: 250px;
		position: fixed;
		z-index: 10;
		right: 40px;
		top: 65px;
	}

	.setting-modal .content {
		background: #0c1c3c;
		color: #fff;
		border: 2px solid #063c7e;
		border-radius: 8px;
		position: relative;
	}

	.setting-modal .content:after {
	}

	.iconItemOne {
		background-image: url('../../assets/HJ/main/ico2_03.png');
	}

	.iconItemOne:hover {
		background-image: url('../../assets/HJ/main/icohover_03.png');
	}

	.iconItemTwo {
		background-image: url('../../assets/HJ/main/ico2_06.png');
	}

	.iconItemTwo:hover {
		background-image: url('../../assets/HJ/main/icohover_06.png');
	}

	.iconItemThree {
		background-image: url('../../assets/HJ/main/ico2_09.png');
	}

	.iconItemThree:hover {
		background-image: url('../../assets/HJ/main/icohover_09.png');
	}

	.iconItemFour {
		background-image: url('../../assets/HJ/main/ico2_08.png');
	}

	.iconItemFour:hover {
		background-image: url('../../assets/HJ/main/icohover_08.png');
	}

	.iconItemFive {
		background-image: url('../../assets/HJ/main/ico2_11.png');
	}

	.iconItemFive:hover {
		background-image: url('../../assets/HJ/main/icohover_11.png');
	}

	.iconItemSix {
		background: url('../../assets/HJ/main/ico2_13.png') no-repeat center;
	}

	.iconItemSix:hover {
		background: url('../../assets/HJ/main/icohover_13.png') no-repeat center;
	}

	.iconSingOut {
		background-image: url('../../assets/HJ/main/singout.png');
	}

	.iconSingOut:hover {
		background-image: url('../../assets/HJ/main/singout_hover.png');
	}
}

.HJJ {
	.iconItemOne {
		background-image: url('../../assets/HJJ/main/ico2_03.png');
	}

	.iconItemOne:hover {
		background-image: url('../../assets/HJJ/main/icohover_03.png');
	}

	.iconItemTwo {
		background-image: url('../../assets/HJJ/main/ico2_06.png');
	}

	.iconItemTwo:hover {
		background-image: url('../../assets/HJJ/main/icohover_06.png');
	}

	.iconItemThree {
		background-image: url('../../assets/HJJ/main/ico2_09.png');
	}

	.iconItemThree:hover {
		background-image: url('../../assets/HJJ/main/icohover_09.png');
	}

	.iconItemFour {
		background-image: url('../../assets/HJJ/main/ico2_08.png');
	}

	.iconItemFour:hover {
		background-image: url('../../assets/HJJ/main/icohover_08.png');
	}

	.iconItemFive {
		background-image: url('../../assets/HJJ/main/ico2_11.png');
	}

	.iconItemFive:hover {
		background-image: url('../../assets/HJJ/main/icohover_11.png');
	}

	.iconItemSix {
		background: url('../../assets/HJ/main/ico2_13.png') no-repeat center;
	}

	.iconItemSix:hover {
		background: url('../../assets/HJ/main/icohover_13.png') no-repeat center;
	}

	.iconSingOut {
		background-image: url('../../assets/HJJ/main/singout.png');
	}

	.iconSingOut:hover {
		background-image: url('../../assets/HJJ/main/singout_hover.png');
	}
}

.LJ {
	.iconItemOne {
		background-image: url('../../assets/LJ/main/ico2_03.png');
	}

	.iconItemOne:hover {
		background-image: url('../../assets/LJ/main/icohover_03.png');
	}

	.iconItemTwo {
		background-image: url('../../assets/LJ/main/ico2_06.png');
	}

	.iconItemTwo:hover {
		background-image: url('../../assets/LJ/main/icohover_06.png');
	}

	.iconItemThree {
		background-image: url('../../assets/LJ/main/ico2_09.png');
	}

	.iconItemThree:hover {
		background-image: url('../../assets/LJ/main/icohover_09.png');
	}

	.iconItemFour {
		background-image: url('../../assets/LJ/main/ico2_08.png');
	}

	.iconItemFour:hover {
		background-image: url('../../assets/LJ/main/icohover_08.png');
	}

	.iconItemFive {
		background-image: url('../../assets/LJ/main/ico2_11.png');
	}

	.iconItemFive:hover {
		background-image: url('../../assets/LJ/main/icohover_11.png');
	}

	.iconItemSix {
		background: url('../../assets/HJ/main/ico2_13.png') no-repeat center;
	}

	.iconItemSix:hover {
		background: url('../../assets/HJ/main/icohover_13.png') no-repeat center;
	}

	.iconSingOut {
		background-image: url('../../assets/LJ/main/singout.png');
	}

	.iconSingOut:hover {
		background-image: url('../../assets/LJ/main/singout_hover.png');
	}
}
.KJ {
  .iconItemOne {
    background-image: url('../../assets/KJ/main/ico2_03.png');
  }

  .iconItemOne:hover {
    background-image: url('../../assets/KJ/main/icohover_03.png');
  }

  .iconItemTwo {
    background-image: url('../../assets/KJ/main/ico2_06.png');
  }

  .iconItemTwo:hover {
    background-image: url('../../assets/KJ/main/icohover_06.png');
  }

  .iconItemThree {
    background-image: url('../../assets/KJ/main/ico2_09.png');
  }

  .iconItemThree:hover {
    background-image: url('../../assets/KJ/main/icohover_09.png');
  }

  .iconItemFour {
    background-image: url('../../assets/KJ/main/ico2_08.png');
  }

  .iconItemFour:hover {
    background-image: url('../../assets/KJ/main/icohover_08.png');
  }

  .iconItemFive {
    background-image: url('../../assets/KJ/main/ico2_11.png');
  }

  .iconItemFive:hover {
    background-image: url('../../assets/KJ/main/icohover_11.png');
  }

  .iconItemSix {
    background: url('../../assets/HJ/main/ico2_13.png') no-repeat center;
  }

  .iconItemSix:hover {
    background: url('../../assets/HJ/main/icohover_13.png') no-repeat center;
  }

  .iconSingOut {
    background-image: url('../../assets/KJ/main/singout.png');
  }

  .iconSingOut:hover {
    background-image: url('../../assets/KJ/main/singout_hover.png');
  }
}
</style>