<template>
  <div class="w-full h-full layout-left-center pageContent">
    <div class="termDataList">
      <div class="toolBox">
        <div class="item" @click="exportModalInfo()"><ExportOutlined class="ico" /> 导出模板</div>
        <a-upload name="file" :showUploadList="false" accept=".xls,.xlsx" :customRequest="uploadChange">
          <div class="item fs_dispose" :style="{fontSize: (fs * 2 + 14) + 'px'}">
            <ImportOutlined class="ico"/>
            导入数据
          </div>
        </a-upload>
      </div>
      <div style="height: calc(100% - 32px)" class="layout-side">
        <div class="itemBox">
          <div class="searchBox">
            <a-input v-model:value="inputsData.militaryType" @change="searchMenu1" placeholder="请输入检索关键字"></a-input>
            <div class="item_group btn" style="margin-left: 16px; height: 30px" @click="addMili(0)"><PlusOutlined style="margin-right: 5px" />添加</div>
            <!--            @click="addMilitary(0)"-->
          </div>
          <div class="box itemBox1 relative">
            <a-spin class="spin" size="large" v-if="sprinShow" />
            <div class="layout-center h-full" v-if="listData.length == 0 && !sprinShow">
              <a-empty />
            </div>
            <div class="item layout-side relative" draggable="true" @dragstart="dragStart(v)" @drop="dragDrop(v, listData, index, 0)" @dragover="drgaOver($event)" v-for="(v, index) of listData" @click="selectItem(index)">
              <div style="width: 100%" class="boxl">
                <div class="mark">{{ index + 1 }}</div>
                <div class="text" :class="[listDataIndex == index ? 'active' : '']" :title="v.key">{{ v.key }}</div>
              </div>
              <a-popconfirm title="是否删除该节点！" ok-text="确定" cancel-text="取消" @confirm="deleteType(v.id)">
                <IconFont type="icon-delete" class="icon"></IconFont>
              </a-popconfirm>
            </div>
          </div>
        </div>
        <div class="itemBox">
          <div class="searchBox">
            <a-input v-model:value="inputsData.militarySecret" @change="searchMenu2" placeholder="请输入检索关键字"></a-input>
            <div class="item_group btn" style="margin-left: 16px; height: 30px" @click="addMili(1)"><PlusOutlined style="margin-right: 5px" />添加</div>
            <!--            @click="addMilitary(1)"-->
          </div>
          <div class="box itemBox2 relative">
            <a-spin class="spin" size="large" v-if="sprinShow" />
            <div class="layout-center h-full" v-if="listData2.length == 0 && !sprinShow">
              <a-empty />
            </div>
            <div class="item layout-side relative" v-for="(v, index) of listData2" draggable="true" @dragstart="dragStart(v)" @drop="dragDrop(v, listData2, index, 1)" @dragover="drgaOver($event)" @click="selectItem2(index)">
              <!--              <div class="text" :title="v.key">{{index+1}}、{{v.key}}</div>-->
              <div style="width: 100%" class="boxl">
                <div class="mark">{{ index + 1 }}</div>
                <div class="text" :title="v.key" :class="[listDataIndex2 == index ? 'active' : '']">{{ v.key }}</div>
              </div>
              <a-popconfirm title="是否删除该节点！" ok-text="确定" cancel-text="取消" @confirm="deleteMilitary(v.id)">
                <IconFont type="icon-delete" class="icon"></IconFont>
              </a-popconfirm>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="termCont">
      <div class="cont" style="height: calc(100% - 100px)">
        <div class="iptBox keys" >
          <a-textarea v-model:value="itemInfo.key" style="color: white" placeholder="请输入类型名称" class="iptKey"></a-textarea>
        </div>
        <!--        <img :src="parting" class="parting">-->
        <!--        <div style="height: 119px" class="parting"></div>-->
        <div class="iptBox value">
          <a-textarea v-model:value="itemInfo.value"  :disabled="addtype.type == 0" :placeholder="addtype.type == 1 ? '请输入内容' : ''" type="textarea" class="iptKey text" style="font-size: 26px;color: white"></a-textarea>
        </div>
      </div>
      <div class="btns">
        <div class="bCont">
          <div class="addBtn" v-if="!addtype.show" @click="saveTermItemDataInfo()">提交</div>
          <div class="addBtn" v-else @click="addMilitary">添加</div>
          <!--          <div class="addBtn" @click="resetTermItemDataInfo()"><RedoOutlined style="margin-right: 5px;"/>重置</div>-->
          <!--          <div class="addBtn" @click="emptyFormDataInfo()"><ClearOutlined style="margin-right: 5px;"/>清空</div>-->
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MilitaryDeploy'
}
</script>
<script setup>
import { PlusOutlined, EditOutlined, DeleteOutlined, CheckOutlined, ClearOutlined, RedoOutlined, ImportOutlined, ExportOutlined, ExclamationCircleOutlined, createFromIconfontCN } from '@ant-design/icons-vue'
import { deepClone } from '../../../../../common/utils/Utils'
import { onMounted, ref, createVNode, nextTick, onBeforeUnmount } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getMilitaryAll, addMilitarys, updateMilitarys, deleteMilitarys, moveMilitarys,saveBatchData } from '../../../../../common/api/MilitaryTermApi'
import parting from '../../../../../assets/HJ/term/parting.png'
import * as xlsx from "xlsx";
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const listData = ref([])
let serchData1 = []
let serchData2 = []
const listData2 = ref([])
const listDataIndex = ref(0)
const listDataIndex2 = ref(0)
const sprinShow = ref(true)
const itemInfo = ref({
  key: '',
  value: ''
})
const addtype = ref({
  show: false,
  type: 0
})
const inputsData = ref({
  militaryType: '',
  militarySecret: ''
})
const fs = ref(JSON.parse(localStorage.getItem('fs')));
onMounted(() => {
  init()
})
const upload = (file) => {
  return new Promise(resolve => {
    let reader = new FileReader()
    reader.readAsBinaryString(file)
    reader.onload = (ev) => {
      resolve(ev.target.result)
    }
  })
}
const uploadChange = async (e) => {
  if (!e.file) return false
  let reader = await upload(e.file)
  const worker = xlsx.read(reader, {type: 'binary'})
  let arr = [];
  worker.SheetNames.forEach((el) => {
    let temp = xlsx.utils.sheet_to_json(worker.Sheets[el])
    temp.forEach((item) => {
      arr.push({
        parentName: item['一级目录'],
        childName: item['二级目录'],
        content: item['详情']
      })
    })
  })
  e.file.status = 'done';
  e.onSuccess()

  saveBatchData(arr).then(res => {
    if (res.code === 200) {
      message.success('数据导入成功！')
      init();
    }
  })
}
let dragItem = null
const dragStart = d => {
  dragItem = d
}
const drgaOver = e => {
  e.preventDefault()
}
const dragDrop = (v, arr, index, type) => {
  const data = {
    sourceId: dragItem.id,
    targetId: v.id
  }
  const num = arr.findIndex(item => item.id == dragItem.id)
  const n = arr[num]
  arr[num] = arr[index]
  arr[index] = n
  if (type == 0) {
    selectItem(index)
  } else {
    selectItem2(index)
  }
  moveMilitarys(data).then(res => {
    if (res.code !== 200) {
      init()
      message.error('拖动失败！')
    }
  })
}
//初始化菜单
const init = index => {
  getMilitaryAll().then(res => {
    sprinShow.value = false
    listData.value = res.data
    serchData1 = res.data
    if (index || index == 0) {
      selectItem(index)
    } else {
      selectItem(listDataIndex.value)
    }
    nextTick(() => {
      document.querySelectorAll('.itemBox1')[0].scrollTop = 0
    })
  })
}
//选择左侧菜单
const selectItem = index => {
  listData2.value = listData.value[index].child
  serchData2 = listData.value[index].child
  listDataIndex.value = index
  selectItem2(0)
  nextTick(() => {
    document.querySelectorAll('.itemBox2')[0].scrollTop = 0
  })
}
//选择右侧菜单
const selectItem2 = index => {
  addtype.value.type = 1
  if (listData2.value.length > 0) {
    itemInfo.value = listData2.value[index]
    listDataIndex2.value = index
  } else {
    itemInfo.value.key = ''
    itemInfo.value.value = ''
  }
}
//检索左侧菜单
const searchMenu1 = () => {
  if (inputsData.value.militaryType.trim() !== '') {
    listData.value = serchData1.filter(item => item.key.indexOf(inputsData.value.militaryType.trim()) > -1)
  } else {
    listData.value = serchData1
  }
  if (listData.value.length > 0) {
    selectItem(0)
  }
}
//检索右侧菜单
const searchMenu2 = () => {
  if (inputsData.value.militarySecret.trim() !== '') {
    listData2.value = serchData2.filter(item => item.key.indexOf(inputsData.value.militarySecret.trim()) > -1)
  } else {
    listData2.value = serchData2
  }
  if (listData2.value.length > 0) {
    selectItem2(0)
  }
}
//添加军语
const addMilitary = type => {
  const data = {
    type: addtype.value.type,
    parentId: '0',
    key: itemInfo.value.key,
    value: '',
    sort: 1
  }
  if (addtype.value.type == 1) {
    data.parentId = listData.value[listDataIndex.value].id
    data.key = inputsData.value.militarySecret
    data.value = itemInfo.value.value
    data.key = itemInfo.value.key
  }
  addMilitarys(data).then(res => {
    init()
    message.success('添加成功！')
    inputsData.value.militarySecret = ''
    inputsData.value.militaryType = ''
    addtype.value.show = false
  })
}
const addMili = type => {
  addtype.value.show = true
  itemInfo.value = deepClone(itemInfo.value)
  itemInfo.value.key = ''
  itemInfo.value.value = ''
  addtype.value.type = type
}
//删除类型
const deleteType = id => {
  const list = listData.value.filter(item => item.id == id)
  if (list[0].child.length > 0) {
    message.error('该类型下还存在军语密语，禁止删除！')
    return
  }
  deleteMilitarys({ id }).then(res => {
    init(0)
    message.success('删除成功！')
  })
}
//删除类型
const deleteMilitary = id => {
  deleteMilitarys({ id }).then(res => {
    init()
  })
}

/**
 * 修改通报用语数据
 */
const saveTermItemDataInfo = () => {
  if (listData2.value.length == 0) {
    message.error('请先选择需要修改的节点！')
    return
  }
  const data = {
    id: listData2.value[listDataIndex2.value].id,
    key: itemInfo.value.key,
    value: itemInfo.value.value,
    parentId: listData.value[listDataIndex.value].id
  }
  updateMilitarys(data).then(res => {
    init()
  })
}

/**
 * 导出模板
 */
const exportModalInfo = () => {
  window.location.href = `${window.fileUrl}/006/userTemp/军语谜语-模板.xlsx`
}
</script>
<style scoped lang="less">
@import "./css/index";
</style>
