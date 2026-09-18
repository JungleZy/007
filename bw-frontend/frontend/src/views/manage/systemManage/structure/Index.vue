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
  ReloadOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
// import avatarDef from '../../../assets/HJ/main/avatar-def.png';
import avatarDef from '../../../../assets/HJ/main/avatar-def.png'
import useStructure from './js/useStructure'
import locale from 'ant-design-vue/es/locale/zh_CN'
import moment from 'moment'
import useUpload from '../../../../common/mixin/useUpload.js'
import 'moment/dist/locale/zh-cn.js'

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
</style>
