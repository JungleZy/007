<template>
  <a-modal
    :destroyOnClose="true"
    :width="350"
    :title="dataChecked?'网络配置(单机版)':'网络配置（局域网版）'"
    class="init_modal_style footer-border-none"
    destroyOnClose="true"
    v-model:visible="isOpen"
  >
    <template #footer>
      <div class="layout-right-center">
        <a-button @click="cancelTypeModal()">取消</a-button>
        <a-button @click="updateSettingData" :loading="confirmLoading"
        >确定
        </a-button>
      </div>
    </template>
    <div style="padding: 20px 20px; color: white" class="ip-style">
      <div class="w-full layout-left-center py-1">
        <span>本机地址：</span><span>{{ localIP }}</span>
      </div>
      <div class="w-full layout-left-center pt-1">
        <span>数据服务：</span>
        <a-switch
          checked-children="本机"
          un-checked-children="远程"
          v-model:checked="dataChecked"
          @change="changeDataChecked"
        />
      </div>
      <div class="w-full layout-left-center pt-1" v-if="!dataChecked">
        <a-input-group compact>
          <a-input
            addon-after="."
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(0,'data')"
            v-model:value="dus[0]"
          ></a-input>
          <a-input
            addon-after="."
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(1,'data')"
            v-model:value="dus[1]"
          ></a-input>
          <a-input
            addon-after="."
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(2,'data')"
            v-model:value="dus[2]"
          ></a-input>
          <a-input
            addon-after=":"
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(3,'data')"
            v-model:value="dus[3]"
          ></a-input>
          <a-input
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(4,'data','port')"
            v-model:value="dus[4]"
          ></a-input>
        </a-input-group>
      </div>
      <div class="w-full layout-left-center pt-1">
        <span>资源服务：</span>
        <a-switch
          checked-children="本机"
          un-checked-children="远程"
          v-model:checked="fileChecked"
          @change="changeFileChecked"
        />
      </div>
      <div class="w-full layout-left-center pt-1" v-if="!fileChecked">
        <a-input-group compact v-show="!fileChecked">
          <a-input
            addon-after="."
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(0,'file')"
            v-model:value="fus[0]"
          ></a-input>
          <a-input
            addon-after="."
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(1,'file')"
            v-model:value="fus[1]"
          ></a-input>
          <a-input
            addon-after="."
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(2,'file')"
            v-model:value="fus[2]"
          ></a-input>
          <a-input
            addon-after=":"
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(3,'file')"
            v-model:value="fus[3]"
          ></a-input>
          <a-input
            style="width: 20%"
            :disabled="confirmLoading"
            @change="handleInput(4,'file','port')"
            v-model:value="fus[4]"
          ></a-input>
        </a-input-group>
      </div>
    </div>
  </a-modal>
</template>

<script>
  export default {
    name: 'NetSetting'
  }
</script>
<script setup>
  import {ref, onMounted, inject,defineExpose} from 'vue'
  import {useRouter} from 'vue-router'
  import {message} from 'ant-design-vue'
  import {useToString} from '@vueuse/core'
  import {ipcRenderer, ipcApi} from '../../electron/index'

  const router = useRouter()
  const ipc = ref(ipcRenderer.isEE)
  const dataChecked = ref(true)
  const fileChecked = ref(true)
  const confirmLoading = ref(false)
  const isOpen = inject('isOpen')
  const emit = defineEmits(['changeChecked'])
  defineExpose({
    dataChecked,
  });
  const settingData = ref({
    dataUrl: {
      url: 'localhost',
      port: '12412'
    },
    fileUrl: {
      url: 'localhost',
      port: '12413'
    }
  })
  const dus = ref(['127', '0', '0', '1', '18001'])
  const fus = ref(['127', '0', '0', '1', '8000'])
  const localIP = ref('')

  const ipRegex =
      /^(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])$/

  onMounted(() => {
    if(ipc.value){
      getLocalIP()
      getSettingData()
    }
  })
  const num2String = (d, b) => {
    return [
      useToString(Number(d[0])).value,
      useToString(Number(d[1])).value,
      useToString(Number(d[2])).value,
      useToString(Number(d[3])).value,
      useToString(Number(b)).value
    ]
  }
  const changeDataChecked = () => {
    if (settingData.value.dataUrl.url !== 'localhost') {
      let s1 = settingData.value.dataUrl.url.split('.')
      dus.value = num2String(s1, settingData.value.dataUrl.port)
    }
    emit('changeChecked', dataChecked.value)
  }
  const changeFileChecked = () => {
    if (settingData.value.fileUrl.url !== 'localhost') {
      let s1 = settingData.value.fileUrl.url.split('.')
      fus.value = num2String(s1, settingData.value.fileUrl.port)
    }
  }
  const getSettingData = () => {
    const sd = ipcRenderer.ipc.sendSync(ipcApi.ipcApiRoute.getConfig)
    // const sd = await invoke('get_config')
    settingData.value.dataUrl = sd.dataUrl
    if (sd.dataUrl.url !== 'localhost') {
      dataChecked.value = false
      let s1 = sd.dataUrl.url.split('.')
      dus.value = [...s1, sd.dataUrl.port]
    }
    settingData.value.fileUrl = sd.fileUrl
    if (sd.fileUrl.url !== 'localhost') {
      fileChecked.value = false
      let s1 = sd.fileUrl.url.split('.')
      fus.value = [...s1, sd.fileUrl.port]
    }
  }

  const getLocalIP = () => {
    const sd = ipcRenderer.ipc.sendSync(ipcApi.ipcApiRoute.getLocalIP)
    localIP.value = sd
  }

  const handleInput = (index, type, port) => {
    let text = "";
    if (type == 'data') {
      text = dus.value[index].replace(/[^\d]/g, "");
      dus.value[index] = text > 255 && port !== 'port' ? 255 : text;
    } else {
      text = fus.value[index].replace(/[^\d]/g, "");
      fus.value[index] = text > 255 && port !== 'port' ? 255 : text;
    }

  };

  const updateSettingData = () => {
    confirmLoading.value = true
    if (!dataChecked.value) {
      const dip = dus.value.slice(0, 4).join('.')
      if (
          !ipRegex.test(dip) ||
          !Number(dus.value[4]) ||
          Number(dus.value[4]) < 0 ||
          Number(dus.value[4]) > 65536
      ) {
        message.error('数据服务地址格式不正确')
        confirmLoading.value = false
        return
      }
    }
    if (!fileChecked.value) {
      const fip = fus.value.slice(0, 4).join('.')
      if (
          !ipRegex.test(fip) ||
          !Number(fus.value[4]) ||
          Number(fus.value[4]) < 0 ||
          Number(fus.value[4]) > 65536
      ) {
        message.error('资源服务地址格式不正确')
        confirmLoading.value = false
        return
      }
    }
    let a = ipcRenderer.ipc.sendSync(ipcApi.ipcApiRoute.changeConfig, {
      dataUrl: {
        url: dataChecked.value ? 'localhost' : `${dus.value[0]}.${dus.value[1]}.${dus.value[2]}.${dus.value[3]}`,
        port: dus.value[4]
      },
      fileUrl: {
        url: fileChecked.value ? 'localhost' : `${fus.value[0]}.${fus.value[1]}.${fus.value[2]}.${fus.value[3]}`,
        port: fus.value[4]
      }
    })
    confirmLoading.value = false
    window.location.reload(true)
    router.replace('/login').then()
  }
  const cancelTypeModal = () => {
    isOpen.value = false
  }
</script>

<style>
  .ip-style .ant-input-group-addon {
    padding: 0 1px !important;
    background: transparent;
    color: #fff;
    font-size: 17px;
  }
</style>
