<template>
  <div class="h-full w-full overflow-hidden">
    <div class="w-full h-full p-2 overflow-auto table_list_box">
      <div class="w-full layout-side p-1 mb-1" style="height: 48px">
        <div class="h-full layout-left-center">
          <div class="item_group btn" @click="showModal(0)"><span class="ico add"></span>新增角色</div>
        </div>
        <div class="h-full layout-right-center">
          <a-tooltip>
            <template #title>表格斑马纹</template>
            <a-switch checked-children="开"
                      un-checked-children="关"
                      v-model:checked="isTableStriped"/>
          </a-tooltip>
          <a-tooltip>
            <template #title>刷新</template>
            <icon-font :style="{fontSize: '18px'}"
                       @click="getRoleList()"
                       class=" layout-center ml-2 cursor-pointer-def"
                       type="icon-reload"/>
          </a-tooltip>
          <a-tooltip>
            <template #title>密度</template>
            <a-dropdown :trigger="['click']" placement="bottomRight">
              <icon-font :style="{fontSize: '18px'}" class=" layout-center ml-2 cursor-pointer-def"
                         type="icon-colum-height"/>
              <template #overlay>
                <a-menu style="width: 80px">
                  <a-menu-item @click="changeTableSize('default')">
                    默认
                  </a-menu-item>
                  <a-menu-item @click="changeTableSize('middle')">
                    中等
                  </a-menu-item>
                  <a-menu-item @click="changeTableSize('small')">
                    紧凑
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-tooltip>
        </div>
      </div>
      <a-table :columns="columns"
               :size="tableSize"
               :loading="tableLoading"
               :pagination="page"
               class="ant-table-striped"
               :rowClassName="(record, index) => (isTableStriped?index % 2 === 1 ? 'table-striped' : null:null)"
               :data-source="roleData">
        <template #menus="{ record,text }">
          {{ record.role.isAdmin === 0 ? "全部" : text.length }}
        </template>
        <template #roleIsAdmin="{ text }">
          {{ text === 0 ? '是' : '否' }}
        </template>
        <template #roleIsDefault="{ text }">
          {{ text === 0 ? '是' : '否' }}
        </template>
        <template #action="{ record }">
          <div class="flex layout-center">
            <div class="table_action_btn">
              <div class="table_btn" title="角色详情" @click="showModal(1,record)">
                <FileTextOutlined/>
              </div>
              <div class="table_btn" title="编辑角色" @click="showModal(2,record)">
                <FormOutlined/>
              </div>
            </div>
          </div>
        </template>
      </a-table>
    </div>
    <a-modal
            :title="state.modelTitle"
            :destroyOnClose="true"
            :width="620"
            class="init_modal_style"
            v-model:visible="state.visible">
      <template #footer>
        <div class="w-full layout-right-center">
          <div class="item_group btn" style="margin-right: 10px"  @click="handleCancel">取消</div>
          <div class="item_group btn"  v-if="!state.onlyRead"  :loading="state.confirmLoading"
               @click="handleOk">提交
          </div>
          <!--          <a-button key="back" @click="handleCancel">取消</a-button>-->
          <!--          <a-button v-if="!state.onlyRead" key="submit" type="primary" :loading="state.confirmLoading"-->
          <!--                    @click="handleOk">提交-->
          <!--          </a-button>-->
        </div>
      </template>
      <a-form :model="roleFormState" :label-col="labelCol" :wrapper-col="wrapperCol">
        <a-form-item label="角色名称">
          <a-input :disabled="state.onlyRead" v-model:value="roleFormState.title"/>
        </a-form-item>
        <a-form-item label="角色描述">
          <a-input :disabled="state.onlyRead" v-model:value="roleFormState.remark"/>
        </a-form-item>
        <a-form-item label="超级管理员">
          <a-radio-group :disabled="state.onlyRead" v-model:value="roleFormState.isAdmin" button-style="solid">
            <a-radio-button :value="1">否</a-radio-button>
            <a-radio-button :value="0">是</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="默认角色" v-if="roleFormState.isAdmin===1">
          <a-radio-group :disabled="state.onlyRead" v-model:value="roleFormState.isDefault" button-style="solid">
            <a-radio-button :value="1">否</a-radio-button>
            <a-radio-button :value="0">是</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="角色权限" v-if="roleFormState.isAdmin===1">
          <div class="w-full layout-side" style="height: 400px">
            <div class="w-full h-full  layout-left-top" style="background: rgba(226, 242, 255, 0.85)">
              <div class="w-full h-full overflow-auto " >
                <a-tree
                        checkable
                        defaultExpandAll
                        :show-line="true"
                        v-if="state.treeData.length>0 && state.treeData"
                        v-model:checkedKeys="menuList"
                        :tree-data="state.treeData"
                        @check="checkTree"
                        @select="selectTree"
                >
                </a-tree>
              </div>
              <div>
                <a-checkbox-group class="w-full" @change="changeSelect" v-model:value="permissionsActive">
                  <a-row class="w-full">
                    <a-col v-for="item in permissionsList" :key="item" style="margin:10px 0" :span="12">
                      <a-checkbox style="color:#000" :value="item.key">{{item.title}}
                      </a-checkbox>
                    </a-col>
                  </a-row>
                </a-checkbox-group>
              </div>
            </div>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script>
  export default {
    name:"Role"
  }
</script>
<script setup>
  import {
    SmileOutlined,
    DownOutlined,
    DeleteOutlined,
    FormOutlined,
    FileTextOutlined,
    UserAddOutlined,
    LoginOutlined,
    StopOutlined,
    MinusSquareOutlined,
    PlusSquareOutlined,
    FileOutlined,
    CheckCircleOutlined, createFromIconfontCN,
  } from '@ant-design/icons-vue';
  import {defineComponent, reactive, toRefs} from 'vue';
  import useRoleList from "./js/useRoleList.js";
  import useRoleForm from './js/useRoleForm.js'
  import useTable from "../../../../../common/mixin/useTable.js";

  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  const {
    isTableStriped,
    tableSize,
    tableLoading,
    page,
    changeTableSize
  } = useTable();
  const {columns, roleData, getRoleList} = useRoleList(tableLoading)
  const {
    state,
    labelCol,
    wrapperCol,
    roleFormState,
    showModal,
    handleCancel,
    handleOk,
    expandedKeys,
    selectedKeys,
    checkedKeys,
    checkedMenus,
    selectedRoleKeys,
    selectedRolePerKeys,
    checkTree,
    selectTree,
    changeSelect,
    permissionsList,
    permissionsActive,
    menuList
  } = useRoleForm(tableLoading, getRoleList);
</script>

<style scoped>
  .level-1 {
    padding-left: 6px;
    position: relative;
  }
  .level-1:before {
    content: '';
    width: 1px;
    background-color: #35445e;
    position: absolute;
    bottom: 0;
    top: 40px;
    left: 12px;
  }
  .level-2 {
    padding-left: 20px;
  }
  .checkBox {
    display: flex;
    height: 32px;
    padding: 8px 0 4px;
    align-items: center;
  }
  .checkBox .ico {
    color: #999;
    font-size: 14px;
    margin-right: 6px;
    position: relative;
    top: 1px;
  }
  .authBoxs {
    background-color: #354971;
    padding: 6px 8px;
    margin-left: 36px;
    margin-right: 8px;
    font-size: 12px;
    color: #aec2d2;
  }
  .authBoxs >>> .ant-checkbox-wrapper {
    margin-left: 0;
    margin-right: 8px;
    height: 22px;
  }
</style>