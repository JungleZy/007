<template>
  <div class="h-full w-full overflow-hidden">
    <div class="h-full w-full p-2 overflow-auto table_list_box">
      <div class="w-full layout-side p-1 mb-1" style="height: 48px">
        <div class="h-full layout-left-center">
          <div class="item_group btn" @click="showModal(0)"><span class="ico add"></span>新增权限</div>
        </div>
        <div class="h-full layout-right-center">
          <!--<a-button type="primary"
                    class="layout-left-center"
                    @click="showModal(0)">
            <UserAddOutlined/>
            新增权限
          </a-button>-->
          <a-divider type="vertical" class="ml-2"/>
          <a-tooltip>
            <template #title>表格斑马纹</template>
            <a-switch checked-children="开"
                      un-checked-children="关"
                      v-model:checked="isTableStriped"/>
          </a-tooltip>
          <a-tooltip>
            <template #title>刷新</template>
            <icon-font :style="{fontSize: '18px'}"
                       @click="getMenusList()"
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
               :defaultExpandAllRows="true"
               :size="tableSize"
               :loading="tableLoading"
               class="ant-table-striped"
               :pagination="page"
               :rowClassName="(record, index) => (isTableStriped?index % 2 === 1 ? 'table-striped' : null:null)"
               :data-source="menusData">
        <template #icon="{ text }">
          <icon-font :style="{fontSize: '20px'}"
                     :type="text"/>
        </template>
        <template #component="{text}">
          {{ text !== '-1' ? text : "公共根页面" }}
        </template>
        <template #permissions="{text}">
          {{ text.length }}
        </template>
        <template #action="{ record }">
          <div class="flex layout-center">
            <div class="table_action_btn">
              <div class="table_btn" title="权限详情" @click="showModal(1,record)">
                <FileTextOutlined/>
              </div>
              <div class="table_btn" title="编辑权限" @click="showModal(2,record)">
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
        :width="800"
        class="init_modal_style"
        v-model:visible="state.visible">
      <template #footer>
        <div class="w-full layout-right-center">
          <div class="item_group btn" style="margin-right: 10px"  @click="handleCancel">取消</div>
          <div class="item_group btn"  v-if="!state.onlyRead" :loading="state.confirmLoading"
               @click="handleOk">提交
          </div>
<!--          <a-button key="back" @click="handleCancel">取消</a-button>-->
<!--          <a-button v-if="!state.onlyRead" key="submit" type="primary" :loading="state.confirmLoading"-->
<!--                    @click="handleOk">提交-->
<!--          </a-button>-->
        </div>
      </template>
      <div class="w-full layout-side" style="height: 535px;padding-top: 10px">
        <div class="h-full layout-side" style="width: 450px;">
          <div class="w-full pl-2 fontColor" style="height: 30px">
            基础信息
          </div>
          <div class="w-full overflow-auto" style="height: calc(100% - 30px)">
            <a-form :model="menuFormState" :label-col="labelCol" :wrapper-col="wrapperCol">
              <a-form-item label="上级菜单">
                <a-input :disabled="state.onlyRead" v-model:value="menuFormState.parentId"/>
              </a-form-item>
              <a-form-item label="路由文件">
                <a-tree-select :treeData="vueFileTree"
                               style="width: 100%"
                               @select="selectNode($event)"
                               :dropdown-style="{maxHeight:'400px',overflow:'auto'}"
                               v-model:value="menuFormState.component"/>
              </a-form-item>
              <a-form-item label="唯一值">
                <a-input :disabled="state.onlyRead" v-model:value="menuFormState.key"/>
              </a-form-item>
              <a-form-item label="路由路径">
                <a-input :disabled="state.onlyRead" v-model:value="menuFormState.path"/>
              </a-form-item>
              <a-form-item label="命名">
                <a-input :disabled="state.onlyRead" v-model:value="menuFormState.name"/>
              </a-form-item>
              <a-form-item label="图标">
                <a-input :disabled="state.onlyRead" v-model:value="menuFormState.icon"/>
              </a-form-item>
              <a-form-item label="标题">
                <a-input :disabled="state.onlyRead" v-model:value="menuFormState.title"/>
              </a-form-item>
              <a-form-item label="排序">
                <a-input :disabled="state.onlyRead" v-model:value="menuFormState.sort"/>
              </a-form-item>
              <a-form-item label="展示菜单">
                <a-select v-model:value="menuFormState.isMenu" class="input_user">
                  <a-select-option :value="0">是</a-select-option>
                  <a-select-option :value="1">否</a-select-option>
                </a-select>
              </a-form-item>
            </a-form>
          </div>
        </div>
        <div class="h-full layout-side" style="width: calc(100% - 450px)">
          <div class="w-full pl-2 fontColor" style="height: 30px">
            权限信息
          </div>
          <div class="w-full pl-1 pr-1 overflow-auto" style="height: calc(100% - 30px)">
            <div class="w-full">
              <a-row v-for="(p,index) of permissionsState"  style="height: 40px;border-bottom: 1px solid #354971;padding: 5px 0;box-sizing: content-box">
                <a-col :span="3">
                  <div class="w-full h-full layout-right-center fontColor">名称:</div>
                </a-col>
                <a-col :span="7">
                  <div class="w-full h-full layout-left-center pl-2 truncate">
                    <a-input v-model:value="p.title" :disabled="!(selectPermissionIndex===index)"/>
                  </div>
                </a-col>
                <a-col :span="3">
                  <div class="w-full h-full layout-right-center fontColor">编码:</div>
                </a-col>
                <a-col :span="!state.onlyRead?7:11">
                  <div class="w-full h-full layout-left-center pl-2 truncate">
                    <a-input v-model:value="p.key" :disabled="!(selectPermissionIndex===index)"/>
                  </div>
                </a-col>
                <a-col :span="4" v-if="!state.onlyRead">
                  <div class="w-full h-full pl-2 layout-side" v-if="!(selectPermissionIndex===index)">
                    <FormOutlined class="cursor-pointer-def primary-color"
                                  @click="handlePermission(0,index)"
                                  style="font-size: 17px"/>
                    <DeleteOutlined class="cursor-pointer-def danger-color"
                                    @click="handlePermission(1,index)"
                                    style="font-size: 17px"/>
                  </div>
                  <div class="w-full h-full pl-2 layout-side" v-else>
                    <CheckOutlined class="cursor-pointer-def primary-color"
                                   @click="handlePermission(3,index)"
                                   style="font-size: 17px"/>
                    <CloseOutlined class="cursor-pointer-def danger-color"
                                   @click="handlePermission(4,index)"
                                   style="font-size: 17px"/>
                  </div>
                </a-col>
              </a-row>
              <div class="w-full mt-1" v-show=" !state.onlyRead && selectPermissionIndex===-1 ">
                <a-button class="layout-center" block @click="handlePermission(2,permissionsState.length+1)">
                  新增权限
                </a-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>
<script>
export default {
  name: "Menu"
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
  CheckOutlined,
  CloseOutlined,
  StopOutlined, LoadingOutlined, PlusOutlined,
  CheckCircleOutlined, createFromIconfontCN,
} from '@ant-design/icons-vue';
import useTable from "../../../../../common/mixin/useTable.js";
import useMenuList from "./js/useMenuList.js";
import useMenuForm from "./js/useMenuForm.js";
import IconFontList from "../../../../../components/common/IconFontList.vue"

const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl,
});
const selectNode = (data)=>{
console.log(data)
}
const {
  isTableStriped,
  tableSize,
  tableLoading,
  page,
  changeTableSize
} = useTable();
const {
  columns,
  menusData,
  getMenusList
} = useMenuList(tableLoading);
const {
  state,
  labelCol,
  wrapperCol,
  menuFormState,
  permissionsState,
  selectPermission,
  selectPermissionIndex,
  showModal,
  handleCancel,
  handleOk,
  vueFileTree,
  handlePermission
} = useMenuForm(tableLoading, getMenusList);
</script>

<style scoped>
 .fontColor{
   color: rgba(226, 242, 255, 0.5);
 }
</style>