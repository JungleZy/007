<template>
  <div class="w-full h-full">
    <div class="w-full h-full pr-2">
      <div class="w-full layout-side p-1 mb-1" style="height: 48px">
        <div class="h-full layout-left-center" style="font-size: 20px">
          职务列表
        </div>
        <div class="h-full layout-right-center">
          <a-button type="primary"
                    class="layout-left-center"
                    @click="showModal(0)">
            <UserAddOutlined/>
            新增职务
          </a-button>
          <a-divider type="vertical" class="ml-2"/>
        </div>
      </div>
      <div class="w-full" style="height:calc( 100% - 48px );overflow: auto">
        <!--:row-key="Math.random()+''"-->

        <a-table :columns="columns"
                 :pagination="page"
                 class="ant-table-striped"
                 :loading="tableLoading"
                 :rowKey="record=>record.id"
                 @change="tableChange"
                 :data-source="userData">
          <template #userArray="{ record }">
            <span v-if="record.userArray.length==0">
              目前暂未设置对应人员！
            </span>
            <div v-else>
              {{ record.userArray.map(i=>i.userName).join(',') }}
            </div>
          </template>
          <template #Role="{ record }">
            <span v-if="record.Role.length==0">
              目前暂未设置对应角色！
            </span>
            <div v-else>
              {{ record.Role.map(i=>i.title).join(',') }}
            </div>
          </template>
          <template #action="{ record }">
             <span>
              <a-tooltip placement="top">
                <template #title>编辑对应人员</template>
                <FormOutlined @click="showModal(1,record)" class="cursor-pointer-def primary-color"
                              style="font-size: 17px"/>
              </a-tooltip>
            </span>
            <span>
              <a-tooltip placement="top">
                <template #title>删除</template>
                <DeleteOutlined @click="showModal(2,record)" class="cursor-pointer-def danger-color"
                                style="font-size: 17px"/>
              </a-tooltip>
            </span>
          </template>
        </a-table>
      </div>
    </div>
    <a-modal :title="modalInfo.modelTitle" v-model:visible="modalInfo.visible" @ok="handleOK">
      <div class="info"><span class="required">职务名称：</span>
        <a-input v-model:value="User.job" placeholder="请输入职务名称"></a-input>
      </div>
      <div class="info"><span class="required">对应角色：</span>
        <a-select v-model:value="selectRole" class="w-full" mode="multiple" :filter-option="false" :placeholder="'请选择对应角色'" showsearch>
          <a-select-option v-for="item in Role" :value="item.role.id" :label="item.role.title" :key="item.id">
            {{ item.role.title }}
          </a-select-option>
        </a-select>
      </div>
      <div class="info"><span>对应人员：</span>
        <a-select v-model:value="selectValue" class="w-full" mode="multiple" :filter-option="filterOption"
                  :placeholder="'请选择对应人员'">
          <a-select-option v-for="item in options" :value="item.id" :label="item.userName" :key="item.id">
            {{ item.userName }}
          </a-select-option>
        </a-select>
      </div>

    </a-modal>
  </div>
</template>
<script>
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide} from 'vue';
import {
  FormOutlined,
  DeleteOutlined,
  CaretRightFilled,
  ApartmentOutlined,
  PlusOutlined,
  DeleteFilled,
  SearchOutlined,
  UserAddOutlined
} from '@ant-design/icons-vue';
import useJob from "./js/useJob"

export default {
  name: "Job",
  emits:["childThing"],
  components: {
    FormOutlined,
    DeleteOutlined,
    CaretRightFilled,
    ApartmentOutlined,
    PlusOutlined,
    DeleteFilled,
    SearchOutlined,
    UserAddOutlined
  },
  setup(props,context) {

    const state = reactive({
      userData: ref([]),
      modalInfo: {
        modelTitle: ref(""),
        visible: ref(false),
        isEdit: ref(false)
      },
      isTableStriped: ref(false),
      User: {  //人员
        job: ref(""),
        jobId: ref('')//对应职务id
      },
      selectRole: ref(null),
      Role: ref([]),
      leaderOptions: ref([]),
      departmentId: ref(0),
      options: ref([]),
      // columns:ref([]),
      p: {page: ref(1), row: ref(10)},
      page: ref({
        showQuickJumper: true,
        showSizeChanger: true,
      }),
      selectValue: ref([]),
      tableLoading: ref(false)
    });
    const {
      getJobList,
      handleSearch,
      handleChange,
      showModal,
      columns,
      handleOK,
      filterOption,
      tableChange
    } = useJob(state,context)
    onMounted(() => {
      // getJobList()
    })
    return {
      ...toRefs(state),
      tableChange,
      getJobList,
      showModal,
      handleSearch,
      handleChange,
      handleOK,
      filterOption,
      columns
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
</style>
