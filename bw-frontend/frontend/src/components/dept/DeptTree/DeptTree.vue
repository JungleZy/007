<template>
  <div class="h-full w-full">
    <div class="w-full layout-side">
      <a-button type="primary" class="layout-center w-1/2" style="border-top-right-radius: 0; border-bottom-right-radius: 0" @click="showModal(0)">
        <PlusOutlined />
        新增
      </a-button>
      <a-button type="primary" danger style="border-top-left-radius: 0; border-bottom-left-radius: 0" class="layout-center w-1/2" @click="showModal(1)">
        <DeleteOutlined />
        删除
      </a-button>
    </div>
    <div style="height: calc(100% - 42px); overflow: auto">
      <a-tree class="draggable-tree" :tree-data="allUserTreeData" v-if="allUserTreeData.length" defaultExpandAll v-model:selectedKeys="selectKeys" @select="selectTree" />
    </div>
  </div>
</template>
<script>
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { getDepartmentAll } from '../../../common/api/DeptApi.js'
import { defineComponent, ref, reactive, toRaw, onMounted, toRefs } from 'vue'
import DeptTreeDo from './js/useDeptTree'

export default defineComponent({
  name: 'DeptTree',
  components: {
    PlusOutlined,
    DeleteOutlined
  },
  props: {
    data: {
      type: Array,
      default: []
    }
  },
  setup(props, context) {
    const onDragEnter = info => {}
    onMounted(() => {
      getTree()
    })
    const state = reactive({
      searchValue: ref(''),
      expandedKeys: ref(0),
      gData: ref([]),
      allUserTreeData: ref([]),
      defaultExpandAll: ref(true),
      selectKeys: ref(['0']),
      checkVal: ref('0')
    })
    const { searchTeam, dataTreeDg } = DeptTreeDo(state)
    const transUserArrayToUserTree = tree => {
      let newTree = []
      for (let r of tree) {
        if (r.parentId === '-1') {
          let ct = dataTreeDg(r, tree)
          if (ct.length !== 0) {
            newTree.push({
              title: r.departmentName,
              key: r.id,
              id: r.id,
              value: r.id,
              parentId: r.parentId,
              children: dataTreeDg(r, tree)
            })
          } else {
            newTree.push({
              title: r.departmentName,
              parentId: r.parentId,
              value: r.id,
              id: r.id,
              key: r.id
            })
          }
        }
      }
      state.allUserTreeData = newTree
      state.defaultExpandAll = true
    }
    const getTree = () => {
      let token = localStorage.getItem('token')
      getDepartmentAll({ token }).then(res => {
        transUserArrayToUserTree(res.data)
      })
    }

    const selectTree = (key, node) => {
      state.checkVal = key[0]
      context.emit('childVal', key[0])
    }
    const showModal = flag => {
      //0新增 1删除
      if (flag) {
        context.emit('deleteTeam', state.checkVal)
      } else {
        context.emit('showModal', true)
      }
    }
    return {
      ...toRefs(state),
      searchTeam,
      selectTree,
      showModal,
      getTree
      // onDragEnter,
      // onDrop,
    }
  }
})
</script>
