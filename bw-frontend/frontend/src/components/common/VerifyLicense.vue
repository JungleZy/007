<template>
  <template v-if="licenseState === 'authorized'">
    <slot></slot>
    <div style="position: fixed; right: 16px; bottom: 16px; z-index: 1000; max-width: 520px;">
      <a-alert v-if="nearExpiry" type="warning" show-icon
               :message="`离线授权剩余累计可运行 ${remainingHours} 小时，请联系管理员续发（不是自然日）`" />
      <a-alert v-if="licenseWarning || hardwareError" type="warning" show-icon
               :message="licenseWarning || hardwareError" />
      <a-button size="small" @click="showStatus">离线授权状态</a-button>
    </div>
  </template>
  <!-- 校验中：避免主界面先闪一下再跳授权页 -->
  <div class="w-full h-full layout-center relative license-context" v-else-if="licenseState === 'checking'">
    <img class="absolute" src="../../assets/HJ/lincense/wisdom.png" style="top: 100px"/>
    <div class="layout-center" style="width: 900px; height: 400px; color: #fff">
      <a-spin size="large" tip="正在校验授权信息..."></a-spin>
    </div>
  </div>
  <!--
    存储读取失败：这是「读不到」而不是「没授权」。
    此处必须显示错误页而非授权页，且不得展示设备码、不得接受输入、不得清除任何数据。
  -->
  <div class="w-full h-full layout-center relative license-context" v-else-if="licenseState === 'storage_error' || licenseState === 'hardware_error'">
    <ActionBtn></ActionBtn>
    <!-- 连击 5 次为清除授权信息的隐藏入口，见 VerifyLicense.js 的 onLogoClick -->
    <img class="absolute" src="../../assets/HJ/lincense/wisdom.png" style="top: 100px" @click="onLogoClick"/>
    <div class="layout-side" style="width: 900px; height: 400px;box-shadow: 0px 0px 20px 0px #020f2f;">
      <div class="left h-full">
        <div class="w-full layout-center py-2">{{ licenseState === 'hardware_error' ? '本机硬件标识不可用' : '授权存储不可用' }}</div>
        <div class="w-full layout-center">未主动清除授权记录，请勿重置设备码</div>
      </div>
      <div class="right h-full">
        <div class="w-full layout-left-center" style="font-weight: 600">{{ licenseState === 'hardware_error' ? '无法取得本机硬件设备码' : '无法读取或保存授权信息' }}</div>
        <div class="w-full py-2" style="line-height: 1.8; color: #666">
          当前无法完成离线授权校验，<b>不代表后端登录凭证失效或授权时长耗尽</b>。<br/>
          请检查存储或系统权限后重试；不要清除记录。若重启后仍出现，请联系管理员。
        </div>
        <div class="w-full py-2" style="color: #999; word-break: break-all; font-size: 12px">
          错误信息：{{ hardwareError || storageError || '未知错误' }}
        </div>
        <div class="w-full py-2" style="color: #666; font-size: 12px">{{ identityScope }}</div>
        <div class="layout-center w-full" style="margin-top: 28px">
          <a-button
              type="primary"
              block
              @click="retry"
              style="height: 40px;background: #3670c5;color: #fff;width: 160px;"
          >
            重 试
          </a-button>
        </div>
      </div>
    </div>
  </div>
  <div class="w-full h-full layout-center relative license-context" v-else>
	  <ActionBtn></ActionBtn>
    <!-- 连击 5 次为清除授权信息的隐藏入口，见 VerifyLicense.js 的 onLogoClick -->
    <img
        class="absolute"
        src="../../assets/HJ/lincense/wisdom.png"
        style="top: 100px"
        @click="onLogoClick"
    />
    <div class="layout-side" style="width: 900px; height: 400px;box-shadow: 0px 0px 20px 0px #020f2f;">
      <div class="left h-full">
        <div class="w-full layout-center py-2">{{ tips.title }}</div>
        <div class="w-full layout-center">
          {{ tips.codeTips }}
        </div>
      </div>
      <div class="right h-full">
        <div class="w-full layout-left-center">设备码</div>
        <div class="w-full py-2 ">
          <div class="w-full layout-side pr-2" style="background: #f5f5f5">
            <a-input
                style="height: 40px;width: calc(100% - 40px);padding: 4px 0px 4px 11px !important;"
                v-model:value="tips.code"
                :disabled="true"
            >
            </a-input>
            <a-tooltip title="复制设备码">
              <CopyOutlined class="cursor-pointer-def" style="color: rgb(54, 112, 197)" @click="copy(tips.code)"/>
            </a-tooltip>
            <a-tooltip title="刷新设备码">
              <ReloadOutlined class="cursor-pointer-def" style="color: rgb(54, 112, 197)" @click="resetCode"/>
            </a-tooltip>
          </div>
        </div>
        <div class="w-full" style="color: #666; font-size: 12px; line-height: 1.5">{{ identityScope }}</div>
        <div v-if="hardwareError || licenseWarning" role="alert" style="color: #ad4e00; font-size: 12px">{{ hardwareError || licenseWarning }}</div>
        <div class="w-full layout-left-center py-2" style="margin-top: 28px">
          授权码&nbsp;&nbsp;-&nbsp;&nbsp;<ImportOutlined class="cursor-pointer-def" @click="triggerFileUpload"
                                                         style="color: #3670c5" title="导入授权码"/>
        </div>
        <div class="w-full licenseIpt overflow-auto"
             contenteditable="true"
             id="licenseCodeDiv">{{licenseCode}}</div>
        <div class="layout-center w-full" style="margin-top: 28px">
          <a-button
              type="primary"
              block
              @click="submitLicense"
              style="
                height: 40px;
                background: #3670c5;
                color: #fff;
                width: 160px;
              "
          >
            开 始 授 权
          </a-button>
        </div>

        <!--        <div class="w-full px-4 layout-center">-->
        <!--          <a-button type="primary" block @click="generateLicense"-->
        <!--          >生 成 授 权-->
        <!--          </a-button-->
        <!--          >-->
        <!--        </div>-->
      </div>
    </div>
    <input ref="uploadInput" type="file" @change="handleFileUpload" style="display: none;">
  </div>
</template>

<script>
export default {
  name: 'VerifyLicense'
}
</script>
<script setup>
import {ImportOutlined, CopyOutlined, ReloadOutlined} from "@ant-design/icons-vue"
import VerifyLicense from '../../common/utils/VerifyLicense.js'
import ActionBtn from "./ActionBtn.vue";
const {
  licenseCode,
  licenseState,
  storageError,
  identityScope,
  licenseWarning,
  hardwareError,
  nearExpiry,
  remainingHours,
  showStatus,
  tips,
  uploadInput,
  copy,
  retry,
  onLogoClick,
  resetCode,
  submitLicense,
  generateLicense,
  triggerFileUpload,
  handleFileUpload
} = VerifyLicense()

</script>
<style scoped lang="less">
:deep(.ant-input) {
  cursor: inherit !important;
  opacity: 1 !important;
}

.license-context {
  background-image: url('../../assets/HJ/lincense/bg.jpg');
  background-position: center;
  background-size: cover;
  font-size: 14px;

  .left {
    width: 500px;
    background-image: url('../../assets/HJ/lincense/left.jpg');
    background-position: center;
    background-repeat: no-repeat;
    color: #fff;
    border-radius: 6px 0 0 6px;
  }

  .right {
    background: #fff;
    width: 400px;
    color: #333333;
    padding: 40px 28px;
    overflow: auto;
    border-radius: 0 6px 6px 0;
  }

  .ant-input,
  .ant-input[disabled] {
    background: #f5f5f5 !important;
    border: none !important;
    color: #000 !important;
  }

  .ant-input[disabled]:hover {
    border-bottom: 0;
  }

  .licenseIpt {
    width: 100%;
    height: 98px;
    background: #ffffff;
    border: 1px solid #999999;
    color: #000;
    padding: 4px 11px;
  }
}
</style>
