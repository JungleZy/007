<template>
  <div class="w-full h-full">
    <div class="w-full h-full pr-2">
      <div class="w-full layout-side p-1 mb-1" style="height: 48px">
        <div class="h-full layout-left-center" style="font-size: 20px">成员列表</div>
        <div class="h-full layout-right-center">
          <a-button type="primary" class="layout-left-center" @click="showModal(0)">
            <UserAddOutlined />
            编辑成员
          </a-button>
          <a-divider type="vertical" class="ml-2" />
        </div>
      </div>
      <!--:row-key="Math.random()+''"-->

      <a-table :columns="columns" style="height: calc(100% - 48px); overflow: auto" :pagination="page" class="ant-table-striped" :loading="tableLoading" :row-key="record => record.userEntity.id" @change="tableChange" :data-source="userData">
        <template #phone="{ text }">
          {{ text ? text : '暂未设置电话' }}
        </template>
        <template #departmentPostEntities="{ text }">
          <span v-for="(item, index) in text">{{ item.title }}{{ text.length - 1 == index ? '' : ',' }}</span>
          <span v-if="text.length == 0">目前暂未设置职务！</span>
        </template>
        <template #action="{ record }">
          <span>
            <a-tooltip placement="topRight">
              <template #title>移除</template>
              <UserDeleteOutlined @click="showModal(1, record.userEntity)" class="cursor-pointer-def danger-color" style="font-size: 17px" />
            </a-tooltip>
          </span>
        </template>
      </a-table>
    </div>
    <a-modal :title="modalInfo.modelTitle" v-model:visible="modalInfo.visible" @ok="handleOK" class="" width="600px" height="600px" :bodyStyle="{ margin: 0 + '' + 'auto' }">
      <!--      <div class="info"><span class="required">人员姓名：</span>-->
      <!--        <a-select v-model:value="User.selectUser"-->
      <!--                  @search="handleSearch"-->
      <!--                  @focus="focusName"-->
      <!--                  @change="handleChange"-->
      <!--                  placeholder="选择人员"-->
      <!--                  class="w-full"-->
      <!--                  show-search-->
      <!--                  :filter-option="false" >-->
      <!--          <a-select-option v-for="item in options" :value="item.id" :label="item.userName" :key="item.id">-->
      <!--            {{item.userName}}-->
      <!--          </a-select-option>-->
      <!--        </a-select>-->
      <!--      </div>-->
      <!--      穿梭框-->
      <a-transfer
        style="width: 100%; height: 600px; margin: 0 auto"
        class="layout-left-center"
        :titles="['全部成员', '当前部门成员']"
        :listStyle="{ width: 256 + 'px', height: 100 + '%' }"
        :locale="{ itemUnit: '人', itemsUnit: '人' }"
        :dataSource="options"
        show-search
        :filter-option="filterOption"
        :render="item => item.userName"
        :targetKeys="target"
        @change="transferChange"
        @search="transferSearch"
      >
      </a-transfer>
    </a-modal>
  </div>
</template>
<script>
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import useMember from './js/useMember'
import { FormOutlined, DeleteOutlined, CaretRightFilled, ApartmentOutlined, PlusOutlined, DeleteFilled, SearchOutlined, FileTextOutlined, UserDeleteOutlined, UserAddOutlined } from '@ant-design/icons-vue'

export default {
  name: 'Member',
  emits: ['childThing'],
  components: {
    FormOutlined,
    DeleteOutlined,
    CaretRightFilled,
    ApartmentOutlined,
    PlusOutlined,
    DeleteFilled,
    SearchOutlined,
    FileTextOutlined,
    UserDeleteOutlined,
    UserAddOutlined
  },
  setup(props, context) {
    const state = reactive({
      userData: ref([]),
      modalInfo: {
        modelTitle: ref(''),
        visible: ref(false)
      },
      isTableStriped: ref(false),
      User: {
        //人员
        selectUser: ref(''), //选中的用户；
        number: ref(null),
        leader: ref(null)
      },
      target: ref([]),
      leaderOptions: ref([]),
      departmentId: ref(0),
      options: ref([]),
      p: { page: ref(1), row: ref(10) },
      page: ref({
        showQuickJumper: true,
        showSizeChanger: true
      }),
      tableLoading: ref(false)
    })
    const { getUserList, handleSearch, handleChange, focusName, handleOK, showModal, columns, tableChange, transferChange, transferSearch, filterOption } = useMember(state, context)
    onMounted(() => {
      getUserList()
    })
    return {
      tableChange,
      ...toRefs(state),
      getUserList,
      showModal,
      handleSearch,
      handleChange,
      handleOK,
      focusName,
      columns,
      transferChange,
      transferSearch,
      filterOption
    }
  }
}
</script>
<style lang="less" scoped>
.MemberTitle {
  display: flex;
  width: 100%;
  justify-content: center;
}

.info {
  display: flex;
  flex-wrap: nowrap;
  margin-top: 10px;
  align-items: center;

  span {
    display: inline-block;
    white-space: nowrap;
    width: 95px;
    text-align: right;
  }
}

.info:nth-of-type(1) {
  margin-top: 0;
}

.modalClass {
  width: 900px;
  height: 600px;
}
</style>
