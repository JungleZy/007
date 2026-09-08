<template>
  <div class="w-full" v-show="openTreeIds.includes(treeNodes.id)">
    <div class="w-full" v-for="c of treeNodes.children">
      <div class="w-full cursor-pointer-def layout-left-center"
           :style="{background:c.id===selectDeptData.id?'#455aff':'',
           paddingLeft:levelValue*12+'px',
           color:c.id===selectDeptData.id?'#fff':''}"
           style="height: 34px">
        <div class="h-full layout-center"
             @click="handleOpenTreeIds(c)"
             style="width: 26px">
          <CaretRightFilled
              :rotate="openTreeIds.includes(c.id)?90:0"
              v-show="!!c.children"/>
        </div>
        <div class="h-full layout-left-center"
             @click="handleSelectDeptData(c)"
             style="width: calc(100% - 76px)">
          <div class="h-full layout-left-center" style="width: 19px;">
            <ApartmentOutlined/>
          </div>
          <div class="h-full layout-left-center truncate" style="width: calc(100% - 24px);font-size: 13px">
            {{ c.title }}
            <template v-if="c.children">
              ({{c.children.length}})
            </template>
          </div>
        </div>
        <div class="h-full layout-side pl-1 pr-1" style="width: 50px">
          <PlusOutlined @click="addTeam(c)" v-show="c.id===selectDeptData.id"/>
          <DeleteFilled @click="deleteTeam(c)" v-show="c.id===selectDeptData.id"/>
        </div>
      </div>
      <nip-tree-node v-if="c.children"
                     :level="levelValue"
                     @deleteTeam="deleteTeam"
                     @addTeam="addTeam"
                     @handleSelectDeptData="handleSelectDeptData"
                     @handleOpenTreeIds="handleOpenTreeIds"
                     :selectDeptData="selectDeptData"
                     :open-tree-ids="openTreeIds"
                     :tree-nodes='c'/>
    </div>
  </div>
</template>

<script>
import {defineComponent, toRefs, ref,watch} from "vue"
import {
  FormOutlined,
  DeleteOutlined,
  CaretRightFilled,
  ApartmentOutlined,
  PlusOutlined,
  DeleteFilled
} from '@ant-design/icons-vue';

export default defineComponent({
  name: 'NipTreeNode',
  components: {
    FormOutlined,
    DeleteOutlined,
    CaretRightFilled,
    ApartmentOutlined,
    PlusOutlined,
    DeleteFilled,
  },
  props: {
    level: Number,
    openTreeIds: Array,
    treeNodes: Object,
    selectDeptData: Object,
  },
  setup(props, context) {
    const {level, openTreeIds, treeNodes, selectDeptData} = toRefs(props)
    watch(()=>props,(newValue,oldValue)=>{
      // console.log(newValue,oldValue)
    })
    const levelValue = ref(1);
    levelValue.value = level.value + levelValue.value;
    const handleSelectDeptData = (e) => {
      context.emit('handleSelectDeptData', e)
    };
    const handleOpenTreeIds = (e) => {
      context.emit('handleOpenTreeIds', e)
    }
    const deleteTeam = (e) => {
      context.emit('deleteTeam', e)
    }
    const addTeam = (e) => {
      context.emit('addTeam', e)
    }
    return {
      levelValue,
      treeNodes,
      openTreeIds,
      selectDeptData,
      handleSelectDeptData,
      handleOpenTreeIds,
      deleteTeam,
      addTeam
    }
  }
})


</script>

<style scoped>

</style>