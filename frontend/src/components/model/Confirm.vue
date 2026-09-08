<template>
  <div
      style="width: 100%;height: 100%;background: rgba(0,0,0,0.3);position: absolute;top: 0;z-index: 9999;padding-top: 5%"
      class="layout-center-top" :class="[classStyle]" @click="maskCloseModal">
    <div class="confirmBox">
      <div class="layout-left-top">
        <icon class="anticon" :class="[okType=='danger'?'danger':'']"></icon>
        <div style="width: calc(100% - 65px)">
          <div class="titleText" v-if="title&&title!=''">{{ title }}</div>
          <div class="contenText" v-if="content&&content!=''">{{ content }}</div>
        </div>
      </div>
      <div class="cofirmbtn layout-right-center">
        <a-button @click="onCancel">{{ cancelText }}</a-button>
        <a-button class="ml-1" :class="[okType=='danger'?'dangerBtn':'']" @click="onConfirm">{{ okText }}</a-button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "Test"
}
</script>
<script setup>
import {createVNode} from 'vue'
import {ExclamationCircleOutlined, CloseCircleOutlined} from "@ant-design/icons-vue";

const props = defineProps({
  okType: {
    type: String,
    default: "danger",
  },
  classStyle: {
    type: String,
    default: "",
  },
  content: {
    type: String,
    default: "",
  },
  title: {
    type: String,
    default: "",
  },
  okText: {
    type: String,
    default: "确定",
  },
  cancelText: {
    type: String,
    default: "取消",
  },
  maskClosable: {
    type: Boolean,
    default: true,
  },
  icon: {
    type: Function,
    default: () => createVNode(ExclamationCircleOutlined),
  },
  onConfirm: {
    type: Function,
    default: () => {
    },
  },
  onCancel: {
    type: Function,
    default: () => {
    },
  },
})

const maskCloseModal = () => {
  if (props.maskClosable) {
    props.onCancel()
  }
}
</script>

<style scoped>
.danger {
  color: #f6bd70;
}

.success {
}

.confirmBox {
  width: max-content;
  min-width: 400px;
  background: #203a61;
  padding: 32px 44px 24px 44px;
  border: 1px solid;
  border-color: transparent transparent #354971 #354971;
  position: relative;
  box-shadow: inset 0 -120px 200px -120px rgba(22, 85, 154, .8);
}

.titleText {
  color: #dfeeff;
  font-size: 15px;
  line-height: 1.5;
  margin-bottom: 5px;
}

.contenText {
  color: #dfeeff;
  font-size: 13px;
}

.anticon {
  float: left;
  margin-right: 16px;
  font-size: 22px;
}

.cofirmbtn {
  margin-top: 24px;
}

:deep(.dangerBtn) {
  color: #ff7875 !important;
  background: #fff !important;
  border-color: #ff7875 !important;
}
</style>