<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box" style="padding: 10px 10px 0px 0px">
          <div class="item_group btn" @click="addDrillModalInfo"><span class="ico add"></span>新增文章</div>
          <a-select style="width: 150px; height: 34px" v-model:value="wztype" @change="init">
            <a-select-option value="0">全部文章</a-select-option>
            <a-select-option value="1">英文文章</a-select-option>
            <a-select-option value="2">中文文章</a-select-option>
          </a-select>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
              <template #type="{ text }">
                {{ text === 0 ? '中文文章' : '英文文章' }}
              </template>
              <template #speed="{ text }">
                {{ text === null ? '--' : text + '组/分' }}
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" title="编辑文章" @click="editArticle(record)">
                      <IconFont type="icon-bianji1" style="font-size: 20px; color: #6ebdff"></IconFont>
                    </div>
                    <div class="table_btn"  >
                      <DeleteOutlined style="color: red;" @click="deleteModel(record.id)" />
                    </div>
<!--                    <a-popconfirm placement="top" ok-text="删除" title="是否删除该文章。" cancel-text="取消" @confirm="deleteArticle(record.id)">-->
<!--                      <div class="table_btn" title="删除文章">-->
<!--                        <IconFont type="icon-delete" style="font-size: 20px; color: red"></IconFont>-->
<!--                      </div>-->
<!--                    </a-popconfirm>-->
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
            <template v-for="(item, i) in Math.ceil(tableData.length / 10)" :key="i">
              <div :class="{ item: true, active: item === currTablePage }" v-if="item > currTablePage - 3 && item < currTablePage + 3" @click="selectTablePage(item)">{{ item }}</div>
            </template>
            <div class="item next" @click="selectTablePage('+')"></div>
          </div>
        </div>
      </div>
    </div>
    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="720" class="init_modal_style footer-border-none" destroyOnClose="true" v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>新增文章</strong>
      </template>
      <template #footer>
        <div class="layout-right-center">
          <a-button @click="cancelTrainModal">取消</a-button>
          <a-button @click="addArticle">{{ data.id ? '修改' : '确定' }}</a-button>
          <!--             <div class="item_group btn" style="width: max-content;margin-right: 20px"><span class="ico add"></span>取消</div>-->
          <!--             <div class="item_group btn" style="width: max-content"><span class="ico add"></span>确定</div>-->
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox" style="padding: 20px 20px">
          <div class="layout-left-top" style="flex-wrap: wrap; justify-content: space-around">
            <div class="layout-left-center w-full">
              <span style="margin-right: 50px">文章名称 : </span>
              <a-input style="width: 200px" v-model:value="data.name"></a-input>
            </div>
            <div class="layout-left-center w-full" style="margin-top: 10px">
              <span style="margin-right: 50px">文章类型 : </span>
              <a-select style="width: 200px" v-model:value="data.type">
                <a-select-option v-for="v of articleTYpe" :value="v.value" :key="v">{{ v.text }}</a-select-option>
              </a-select>
            </div>
            <div class="layout-left-center w-full" style="margin-top: 10px">
              <span style="margin-right: 50px">导入文章 : </span>
              <a-upload name="file" :showUploadList="false" accept=".docx,.txt" :customRequest="uploadChange" >
                <a-button>导入</a-button>
              </a-upload>
            </div>
            <div class="layout-left-top w-full" style="margin-top: 10px">
              <span style="margin-right: 50px">文章内容 : </span>
              <a-textarea style="width: 80%; min-height: 400px" v-model:value="data.content"></a-textarea>
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
import {ref, onMounted, provide, createVNode} from 'vue'
import { addPostArticle, deleteArticleByID } from '../../../../../common/api/postHanZi'
import telegramList from './js/telex'
import {createFromIconfontCN, ExclamationCircleOutlined,DeleteOutlined} from '@ant-design/icons-vue'
import {message, Modal} from 'ant-design-vue'
import { isUploadSizeAllowed, uploadSizeMessage } from '../../../../../common/utils/uploadLimits.js'
import * as mammoth from "mammoth";
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const addDrillModal = ref(false)
const basicTrainDeployModal = ref(false)
const data = ref({
  name: '',
  content: '',
  type: 0
})
const deleteModel = (v)=>{
  Modal.confirm({
    class: 'init_modal_style',
    content: '是否删除该类型？',
    icon: () => createVNode(ExclamationCircleOutlined),
    okType: 'danger',
    okText: () => '确定',
    cancelText: () => '取消',
    maskClosable: true,
    onOk: () => {
      deleteArticle(v)
    }
  })
}
const uploadChange = async (e)=>{
  if (!e.file) return false
  let i = e.file.name.lastIndexOf('.')
  if (!isUploadSizeAllowed(e.file)) {
    message.error(uploadSizeMessage())
    if (e.onError) e.onError(new Error(uploadSizeMessage()))
    return false
  }
  data.value.name = e.file.name.substr(0, i)
  let reader = new FileReader()
  if (e.file.type === 'text/plain') {
    reader.readAsText(e.file,'UTF-8')
    reader.onload = (ev) => {
      data.value.content = ev.target.result
    }
  } else {
    reader.readAsArrayBuffer(e.file)
    reader.onload = (ev) => {
      console.log(e);
      mammoth.extractRawText({arrayBuffer: ev.target.result}).then(res => {
        data.value.content = res.value
      })
    }
  }
  e.file.status = 'done';
  e.onSuccess()

  /*if(e.file.response){
    data.value.name = e.file.response.data.name
    data.value.content = e.file.response.data.content
  }*/
}

const articleTYpe = ref([
  {
    value: 0,
    text: '中文文章'
  },
  {
    value: 2,
    text: '英文文章'
  }
])
const { columns, tableData, tableList, currTablePage, selectTablePage, tableLoading, selectType, initContent, trainData, addTelexTrain, init, wztype } = telegramList(addDrillModal)
const editArticle = list => {
  addDrillModal.value = true
  data.value.name = list.name
  const text = JSON.parse(list.content)
  // data.value.content =  JSON.parse(list.content)
  for (let v of text) {
    data.value.content = data.value.content === '' ? data.value.content + v : data.value.content + '\r' + v
  }
  data.value.type = list.type
  data.value.id = list.id
}
/**
 * 新增训练
 */
const addDrillModalInfo = () => {
  addDrillModal.value = true
}
const deleteArticle = id => {
  deleteArticleByID({ id }).then(res => {
    message.success('删除成功。')
    init()
  })
}
const addArticle = () => {
  const data2 = data.value
  data2.content = data2.content
  addPostArticle(data2).then(res => {
    addDrillModal.value = false
    if (data.value.id) {
      message.success('编辑成功。')
    } else {
      message.success('新增成功。')
    }
    init()
    data.value = {
      name: '',
      content: '',
      type: 0
    }
  })
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
  /*background-image: linear-gradient(#6cebfc, #006ea4);*/
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
