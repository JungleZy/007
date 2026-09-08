<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box" style="padding: 10px 10px 0px 10px">
          <!--          <div class="item_group btn" @click="goWordTrain">单字训练</div>-->
          <div class="item_group btn" @click="addDrillModalInfo"><span class="ico add"></span>新增设备</div>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px); padding: 0 10px">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
              <template #isShow="{ record }">
                {{ record.isEnable == 1 ? '启用' : '停用' }}
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" title="编辑设备" @click="editEquipment(record)">
                      <IconFont type="icon-bianji1" style="font-size: 20px; color: #6ebdff"></IconFont>
                    </div>

                    <a-popconfirm placement="top" ok-text="删除" title="是否删除该设备。" cancel-text="取消" @confirm="deleteEquipment(record.id)">
                      <div class="table_btn" title="删除设备" @click="">
                        <IconFont type="icon-delete" style="font-size: 20px; color: red"></IconFont>
                      </div>
                    </a-popconfirm>
                    <!--<div class="table_btn" title="删除">
                      <DeleteOutlined/>
                    </div>-->
                  </div>
                </div>
              </template>
            </a-table>
          </div>
          <div class="table_pagination">
            <div class="total">共{{ tableData.length }}条数据</div>
            <div class="item prev" @click="selectTablePage('-')"></div>
            <template v-for="(item, i) in Math.ceil(tableData.length / 10)">
              <div :class="{ item: true, active: item == currTablePage }" v-if="item > currTablePage - 3 && item < currTablePage + 3" @click="selectTablePage(item)">{{ item }}</div>
            </template>
            <div class="item next" @click="selectTablePage('+')"></div>
          </div>
        </div>
      </div>
    </div>
    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="720" class="init_modal_style footer-border-none" destroyOnClose="true" v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>{{ data.id ? '修改' : '添加' }}设备</strong>
      </template>
      <template #footer>
        <div class="layout-center">
          <a-button @click="addEquipment">{{ data.id ? '修改' : '添加' }}</a-button>
          <!--             <div class="item_group btn" style="width: max-content;margin-right: 20px"><span class="ico add"></span>取消</div>-->
          <!--             <div class="item_group btn" style="width: max-content"><span class="ico add"></span>确定</div>-->
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox" style="padding: 20px 20px">
          <div class="layout-left-top" style="flex-wrap: wrap; justify-content: space-around">
            <div class="layout-left-center w-full">
              <div class="modelTitle">设备名称 :</div>
              <a-input style="width: 200px" v-model:value="data.name"></a-input>
            </div>
            <div class="layout-left-center w-full">
              <div class="modelTitle">设备ID :</div>
              <a-input style="width: 200px" v-model:value="data.deviceId"></a-input>
            </div>
            <div class="layout-left-center w-full" style="margin-top: 10px">
              <div class="modelTitle">是否启用 :</div>
              <a-switch v-model:checked="data.isEnable" checked-children="启用" un-checked-children="停用"></a-switch>
            </div>
            <div class="layout-left-center w-full" style="margin-top: 10px">
              <div class="modelTitle">上传图片 :</div>
              <div style="width: 200px">
                <a-upload v-model:file-list="fileList" name="avatar" list-type="picture-card" :data="{ currentPath: 'userImages' }" :show-upload-list="false" :action="uploadFileUrl" :before-upload="beforeUpload" @change="handleChange">
                  <img v-if="data.image" :src="fileUrl + '/' + data.image" alt="avatar" />
                  <div v-else>
                    <div class="ant-upload-text">上传</div>
                  </div>
                </a-upload>
              </div>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted, provide } from 'vue'
import { deleteEquipmentById, addEquipments, editEquipments } from '../../../../../common/api/equipment.js'
import equipmentList from './js/equipment'
import { createFromIconfontCN } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const addDrillModal = ref(false)
const basicTrainDeployModal = ref(false)
const action = ref('http://' + window.httpUrl + '/api/postEnteringExerciseWordStock/view')
const token = window.localStorage.getItem('token')
const deviceId = window.localStorage.getItem('deviceId')
const headers = ref({ token, deviceId })
const data = ref({
  name: '',
  isEnable: true,
  image: '',
  option: '',
  deviceId: ''
})
const fileList = ref([])
const uploadFileUrl = window.uploadFileUrl
const fileUrl = window.fileUrl
const handleChange = info => {
  if (info.file.status === 'uploading') {
    return
  }
  if (info.file.status === 'done') {
    data.value.image = info.file.response.data
  }
  if (info.file.status === 'error') {
    message.error('上传错误')
  }
}
const beforeUpload = file => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('上传文件格式错误，只支持jpg或者png图片!')
  }
  const isLt2M = file.size / 1024 / 1024 < 4
  if (!isLt2M) {
    message.error('上传文件超出4MB大小限制！')
  }
  return isJpgOrPng && isLt2M
}

const { columns, tableData, tableList, currTablePage, selectTablePage, tableLoading, selectType, initContent, trainData, init } = equipmentList(addDrillModal)
const editEquipment = list => {
  addDrillModal.value = true
  data.value.name = list.name
  data.value.id = list.id
  data.value.isEnable = list.isEnable == 1 ? true : false
  data.value.image = list.image
  data.value.deviceId = list.deviceId
}
/**
 * 新增训练
 */
const addDrillModalInfo = () => {
  addDrillModal.value = true
}
const deleteEquipment = id => {
  deleteEquipmentById({ id }).then(res => {
    message.success('删除成功。')
    init()
  })
}
const addEquipment = () => {
  data.value.isEnable = data.value.isEnable ? 1 : 0
  const data2 = data.value
  data2.content = data2.content
  if (!data.value.id) {
    addEquipments(data2).then(res => {
      addDrillModal.value = false
      message.success('添加成功。')
      init()
      data.value = {
        name: '',
        isEnable: true,
        image: '',
        option: '',
        deviceId: ''
      }
    })
  } else {
    editEquipments(data2).then(res => {
      addDrillModal.value = false
      message.success('编辑成功。')
      init()
      data.value = {
        name: '',
        isEnable: true,
        image: '',
        option: '',
        deviceId: ''
      }
    })
  }
}
/**
 * 关闭弹窗
 */
const cancelTrainModal = () => {
  addDrillModal.value = false
  data.value = {
    name: '',
    content: '',
    type: 0
  }
}
</script>

<style scoped>
.modelTitle {
  margin-right: 50px;
  width: 65px;
  text-align: right;
}
.ant-popover-content {
  background: rgba(255, 255, 255, 0.4) !important;
}
.hanziItems {
  width: 175px;
  height: 118px;
  margin: 10px;
  cursor: pointer;
}
.grouping_content {
  display: flex;
  flex-direction: column;
  padding: 0;
}
.createDrillBtn {
  width: 96px;
  height: 30px;
  color: #e2f2ff;
  font-size: 15px;
  text-align: center;
  line-height: 28px;
  box-shadow: 2px 2px 3px rgba(0, 0, 0, 0.2);
  border-radius: 2px;
  background-image: linear-gradient(#70a3b8, #4c7595);
}
.createDrillBtn.loadingBtn {
  cursor: no-drop;
  opacity: 0.8;
}
.configurationBox {
  padding: 10px 20px 20px;
  color: #fff;
}
.configurationBox .rowItem {
  padding: 8px 0;
  display: flex;
  align-items: center;
}
.configurationBox .rowItem.title {
  padding: 16px 0 0;
}
.configurationBox .rowItem .lab {
  width: 100px;
  flex-shrink: 0;
  font-size: 13px;
  color: #7b90af;
  text-align: right;
}
.configurationBox .rowItem .item {
  font-size: 13px;
  color: #7b90af;
  width: 120px;
  text-align: center;
}
.configurationBox .rowItem .item + .item {
  margin-left: 30px;
}
.configurationBox .rowItem .item .absolute {
  left: 0;
  top: 0;
  line-height: 32px;
  z-index: 9;
  padding: 0 8px;
  font-size: 12px;
  color: #7b90af;
  border-right: 1px solid #354971;
}
.configurationBox .rowItem.mini {
  padding-left: 6px;
}
.configurationBox .rowItem.mini .lab {
  width: 86px;
}
.configurationBox .rowItem.mini .item {
  width: 120px;
  margin-left: 12px;
}
.configurationBox .rowItem.mini .item .close {
  display: flex;
  margin-top: -7px;
  color: #d11d1d;
  position: absolute;
  right: -24px;
  top: 50%;
}
.configurationBox .rowItem .msg {
  height: 24px;
  font-size: 12px;
  color: #f8cf6f;
  display: flex;
  align-items: center;
  padding: 0 8px;
  background-color: #374a63;
}
.groupBoxs {
  position: relative;
  border: 1px solid #3d586f;
  margin-top: 20px;
  padding-bottom: 8px;
}
.groupBoxs .groupTitle {
  font-size: 13px;
  color: #bbcdef;
  line-height: 20px;
  padding: 0 10px;
  background-color: #2e4559;
  position: absolute;
  left: 10px;
  top: -10px;
}
.init_modal_style >>> .ant-modal-footer {
  border-top: none !important;
}
</style>
