<template>
  <div class="h-full w-full p-2 overflow-auto dashboard-page relative">
    <div class="w-full h-full grouping">
      <div class="w-full h-full grouping_content">
        <!--              顶部搜索-->
        <div class="w-full table_search_box">
          <div class="item_group input">
            <a-input v-model:value="searchData.userName" placeholder="真实姓名" />
            <div class="icon" @click="searchData.userName = ''"></div>
          </div>
          <div class="item_group input">
            <a-input v-model:value="searchData.userAccount" placeholder="用户名" />
            <div class="icon" @click="searchData.userAccount = ''"></div>
          </div>
          <div class="item_group btn" @click="handleSearch"><span class="ico"></span> 检索</div>
          <div class="item_group btn" @click="editUser(false, 2)"><span class="ico add"></span>新增人员</div>
          <!--          <a-dropdown class="mr-1">-->
          <!--            <template #overlay>-->
          <!--              <a-menu @click="onClickDropdown">-->
          <!--                <a-menu-item :key="0">导出模板</a-menu-item>-->
          <!--                <a-menu-divider/>-->
          <!--                <a-menu-item :key="1">导入人员</a-menu-item>-->
          <!--              </a-menu>-->
          <!--            </template>-->
          <!--            <div class="item_group btn">导入导出-->
          <!--              <DownOutlined style="margin-left:4px"/>-->
          <!--            </div>-->
          <!--          </a-dropdown>-->
        </div>
        <div class="w-full" style="height: calc(100% - 48px)">
          <!--              表格-->
          <div class="table_list_box overflow-auto" style="height: calc(100% - 60px)">
            <a-table :columns="columns" :rowKey="record => record.id" :pagination="false" :data-source="searchUsersData">
              <template #userImg="{ text }">
                <!--                        {{ searchUsersData.length>0?searchUsersData:searchData.userName==''&&searchData.userAccount==''?tableList:searchUsersData }}-->
                <div style="display: flex; justify-content: center">
                  <img :src="fileUrl + text" style="height: 28px; width: 28px; border-radius: 50%" />
                </div>
              </template>
              <template #userSex="{ text }">
                <ManOutlined v-if="text == 1" />
                <WomanOutlined v-if="text == 0" />
                {{ text == 0 ? '女' : '男' }}
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" title="查看" @click="editUser(record.id, 0)">
                      <FileSearchOutlined />
                    </div>
                    <div class="table_btn" title="编辑" @click="editUser(record.id, 1)">
                      <FormOutlined />
                    </div>
                    <div class="table_btn" title="重置密码" @click="onResetPassword(record.id,record.userName)">
                      <ReloadOutlined />
                    </div>
                    <div class="table_btn">
                      <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)" />
                    </div>
                  </div>
                </div>
              </template>
            </a-table>
          </div>
          <div class="table_pagination">
            <div class="total">共{{ total }}条数据</div>
            <div class="item prev" @click="selectTablePage('-')"></div>
            <template v-for="(item, i) in Math.ceil(total / 10)" :key="i">
              <div :class="{ item: true, active: item == currTablePage }" v-if="item > currTablePage - 3 && item < currTablePage + 3" @click="selectTablePage(item)">{{ item }}</div>
            </template>
            <div class="item next" @click="selectTablePage('+')"></div>
          </div>
        </div>
        <!--              弹出层-->
        <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none" v-model:visible="showModal">
          <template #title>
            <strong>{{ isEdit == 0 ? '查看' : isEdit == 1 ? '修改' : '新增' }}人员</strong>
          </template>
          <template #footer>
            <div class="w-full layout-right-top">
              <!--                    <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"-->
              <!--                         @click="createDrillInfo"><a-spin v-if="loading" size="small"/> 生成训练</div>-->
              <div class="item_group btn" style="margin-right: 10px" @click="takeModel(false)">取消</div>

              <div class="item_group btn" v-if="isEdit != 0" @click="takeModel(true)">确定</div>
            </div>
          </template>
          <a-form :model="userFormState" ref="userFormRef" style="padding-top: 12px" :rules="rules" :label-col="labelCol" :wrapper-col="wrapperCol">
            <a-form-item label="用户头像">
              <a-upload v-model:file-list="fileList" name="avatar" list-type="picture-card" class="avatar-uploader" :data="{ currentPath: 'userImages' }" :show-upload-list="false" :action="uploadFileUrl" :before-upload="beforeUpload" @change="handleChange">
                <img v-if="userFormState.userImg" :src="fileUrl + userFormState.userImg" alt="avatar" />
                <div v-else>
                  <loading-outlined v-if="loading"></loading-outlined>
                  <plus-outlined v-else></plus-outlined>
                  <div class="ant-upload-text">上传</div>
                </div>
              </a-upload>
            </a-form-item>
            <a-form-item label="用户名" name="userAccount">
              <a-input :disabled="isEdit == 0" v-model:value="userFormState.userAccount" />
            </a-form-item>
            <a-form-item label="真实姓名" name="userName">
              <a-input :disabled="isEdit == 0" v-model:value="userFormState.userName" />
            </a-form-item>
            <a-form-item label="联系电话" name="phone">
              <a-input :disabled="isEdit == 0" v-model:value="userFormState.phone" />
            </a-form-item>
            <a-form-item label="身份证" name="idCard">
              <a-input @change="handleIdCard" :disabled="isEdit == 0" v-model:value="userFormState.idCard" />
            </a-form-item>
            <a-form-item label="性别">
              <a-radio-group :disabled="isEdit == 0" v-model:value="userFormState.userSex" button-style="solid">
                <a-radio-button :value="1">男</a-radio-button>
                <a-radio-button :value="0">女</a-radio-button>
              </a-radio-group>
            </a-form-item>
            <a-form-item label="出生日期" name="bday">
              <a-date-picker :locale="locale" :disabled="isEdit == 0" v-model:value="userFormState.bday" />
            </a-form-item>
            <a-form-item label="入伍日期" name="eday">
              <a-date-picker :locale="locale" :disabled="isEdit == 0" v-model:value="userFormState.eday" />
            </a-form-item>
            <a-form-item label="默认密码" v-if="isEdit===2">
              a123456
            </a-form-item>
            <!--            <a-divider/>-->
            <a-form-item label="角色设置">
              <a-radio-group :disabled="isEdit == 0" v-model:value="userRoleId" button-style="solid">
                <a-radio-button :title="r.role.title" v-for="r of roleList" :key="r.role.id" :value="r.role.id" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ r.role.title }}</a-radio-button>
              </a-radio-group>
            </a-form-item>
          </a-form>
        </a-modal>

        <a-modal title="导入人员" :destroyOnClose="true" width="96%" centered v-model:visible="userImportVisible" style="padding-bottom: 0">
          <template #footer>
            <div class="w-full layout-right-center">
              <a-button key="back" @click="userImportVisible = false">取消</a-button>
              <a-button key="submit" type="primary" @click="userImportSubmitInfo">提交</a-button>
            </div>
          </template>
          <div class="w-full overflow-hidden relative" style="height: calc(100vh - 160px); padding-top: 10px">
            <div class="importLoadBox" v-if="importLoading">
              <div class="layout-center" v-if="errorImportUser.length == 0">
                <a-spin tip="数据正在提交..."></a-spin>
              </div>
              <div class="importErrorBox" v-else>
                <div class="title">导入失败人员</div>
                <div class="errorBox overflow-auto">
                  <div class="item" v-for="(user, index) in errorImportUser" :key="index">
                    <div class="name">{{ user.userName }}</div>
                    {{ user.idCard }}
                  </div>
                </div>
              </div>
            </div>
            <excel ref="userExcelRef" />
          </div>
        </a-modal>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Structure'
}
</script>
<script setup>
import {
  ManOutlined,
  WomanOutlined,
  FormOutlined,
  FileSearchOutlined,
  PlusOutlined,
  LoadingOutlined,
  DownOutlined,
  ReloadOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
// import avatarDef from '../../../assets/HJ/main/avatar-def.png';
import avatarDef from '../../../../assets/HJ/main/avatar-def.png'
import { ref } from 'vue'
import useStructure from './js/useStructure'
import { message } from 'ant-design-vue'
import locale from 'ant-design-vue/es/locale/zh_CN'
import moment from 'moment'
import useUpload from '../../../../common/mixin/useUpload.js'
import { parseIdCard } from '../../../../common/utils/Utils.js'
import { importUser } from '../../../../common/api/UserApi'
import pinyin from 'pinyin'
import 'moment/dist/locale/zh-cn.js'
import Excel from '../../../../components/excel/Excel.vue'

let {
  columns,
  tableData,
  tableList,
  currTablePage,
  selectTablePage,
  // fileUrl,
  isEdit,
  editUser,
  onResetPassword,
  showModal,
  takeModel,
  userFormState,
  labelCol,
  wrapperCol,
  rules,
  // fileList,
  // uploadFileUrl,
  // beforeUpload,
  // handleChange,
  handleIdCard,
  userRoleId,
  findRoleList,
  roleList,
  userFormRef,
  inputValue,
  handleSearch,
  searchData,
  searchUsersData,
  total,
  deleteModel
} = useStructure()
let { loading, fileUrl, uploadFileUrl, fileList, handleChange, beforeUpload } = useUpload(res => {
  userFormState.value.userImg = res
})

const userImportVisible = ref(false)
const importLoading = ref(false)
const userExcelRef = ref(null)
const errorImportUser = ref([])
const onClickDropdown = e => {
  switch (e.key) {
    case 0:
      window.location.href = `${window.fileUrl}/006/userTemp/员工花名册-模板.xlsx`
      break
    case 1:
      userImportVisible.value = true
      errorImportUser.value = []
      setTimeout(() => {
        userExcelRef.value.handleExcelData([{ name: 'sheet1' }])
      }, 100)
      break
    default:
      break
  }
}
const userImportSubmitInfo = () => {
  if (importLoading.value) return false
  let excel = userExcelRef.value.getExcelData(),
    excelData = [],
    subData = []
  importLoading.value = true

  excel.data[0].data.forEach(d => {
    let filter = d.filter(item => item)
    if (filter.length > 0 && filter[0].v && filter[1].v) {
      excelData.push(filter)
    }
  })
  if (excel.data[0].celldata.length === 0 || excelData.length <= 1) {
    message.error('数据不能为空')
    importLoading.value = false
    return
  }

  for (let i = 1, row = null; i < excelData.length; i++) {
    row = excelData[i]
    let sex = parseIdCard(row[1].v, 2)
    subData.push({
      id: '',
      userImg: sex === 1 ? '/userImages/1.png' : '/userImages/0.png',
      userAccount: pinyin(row[0].v, { style: pinyin.STYLE_NORMAL }).join(''),
      userName: row[0].v,
      phone: '',
      idCard: row[1].v,
      password: '123456',
      userSex: sex,
      status: '2',
      bday: parseIdCard(row[1].v, 1),
      eday: ''
    })
  }

  importUser(subData).then(res => {
    if (res.code === 200) {
      if (res.data && res.data.length > 0) {
        errorImportUser.value = res.data
      } else {
        importLoading.value = false
        userImportVisible.value = false
      }
    } else {
      importLoading.value = false
      message.error(res.message)
    }
  })
}
</script>

<style scoped>
.wisdom .ant-radio-button-wrapper-disabled.ant-radio-button-wrapper-checked {
  background-color: #141e28 !important;
}
.grouping {
  overflow: hidden;
}
.ant-modal-body {
  overflow: auto;
}
.grouping_content {
  display: flex;
  flex-direction: column;
}

.importLoadBox {
  background-color: rgba(29, 41, 66, 0.8);
  padding-top: 8%;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
}

.importErrorBox .title {
  font-weight: bolder;
  font-size: 15px;
  color: #d11d1d;
  text-align: center;
}

.importErrorBox {
  width: 320px;
  margin: 20px auto;
}

.errorBox {
  padding-left: 14px;
  height: calc(80vh - 100px);
  max-height: 320px;
  margin-top: 4px;
  width: 320px;
}

.errorBox .item {
  display: flex;
  align-items: center;
  height: 30px;
  color: #d0d6e2;
}

.errorBox .item .name {
  width: 100px;
  text-align: right;
  padding-right: 20px;
  flex-shrink: 0;
}
</style>
