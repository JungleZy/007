<template>
  <div class="w-full pl-1" style="height: 320px">
    <div class="w-full overflow-auto" style="height: calc(100% - 40px)">
      <a-row>
        <a-col class="pr-1 pb-1" span="12" v-for="(c,index) in cableList" :key="'c_'+index">
          <div class="w-full p-2 relative cursor-pointer-def"
               :class="[formData.cableId === c.id?'border-select':'border-cable']" @click.stop="onSelectCable(c,index)">
            <div class="w-full layout-center">{{ c.title }}</div>
            <div class="w-full mt-1 layout-side">
              <div class="w-1/2 layout-center">{{
                c.codeType === 0 ? '数码报' : c.codeType === 1 ? '字码报' : '混合报'
                }}
              </div>
              <div class="w-1/2 layout-center">{{ c.codeType === 0 ? c.codeSort === 0 ? '短码' : '长码' : '--' }}</div>
            </div>
            <div class="w-full mt-1 layout-side">
              <div class="w-1/3  details" :title="c.floorCount">页：{{ c.floorCount }}页</div>
              <div class="w-1/3  details" :title="c.groupCount">组：{{ c.groupCount }}组</div>
              <div class="w-1/3  details" :title="c.codeCount">码：{{ c.codeCount }}个</div>
            </div>
            <div class="w-full layout-center pt-2">
              <div @click.stop="openPreviewModel(c)">预览报文</div>
            </div>
            <div class="absolute select-cable" v-show="formData.cableId === c.id">
              <CheckOutlined/>
            </div>
          </div>
        </a-col>
      </a-row>
    </div>
    <div class="w-full px-2 layout-left-center" style="height: 40px" v-if="cableList.length>0">
      起始页：
      <a-input-number v-model:value="formData.startPage" :max="cableList[activeIndex].floorCount"
                      :min="1"></a-input-number>
      &nbsp;&nbsp;
      总页数：
      <a-input-number v-model:value="totalPage" :max="maxPage" :min="1"></a-input-number>
    </div>
    <a-modal :destroyOnClose="true" :width="800" class="init_modal_style footer-border-none"
             v-model:visible="previewModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>报文信息</strong>
      </template>
      <template #footer>
        <div></div>
      </template>
      <PreviewMessage :pageData="pageData" :all-page="pageData.length"></PreviewMessage>
    </a-modal>
  </div>
</template>

<script>
  export default {
    name: "SelectCable"
  }
</script>
<script setup>
  import {ref, inject, onMounted, watch} from "vue";
  import {CheckOutlined, createFromIconfontCN} from '@ant-design/icons-vue'
  import {getCableFloorAllByID} from "../../common/api/CableApi";
  import iconImage from "../../views/manage/postJob/js/iconImage";
  import PreviewMessage from "../previewMessage/PreviewMessage.vue";
  import {watchDeep} from "@vueuse/core";

  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl
  })
  const selectCable = inject('selectCable')
  const cableList = inject('cableList')
  const formData = inject('formData')
  const {prev, next} = iconImage()
  const previewModal = ref(false)
  const activeIndex = ref(0)
  const pageData = ref([])
  const totalPage = ref(0)
  const maxPage = ref(0)
  // const page = ref({
  //   current: 1,
  //   pageNumber: 0
  // })
  watchDeep(formData, (o, n) => {
    if (selectCable.value.floorCount > 0) {
      maxPage.value = selectCable.value.floorCount - n.startPage + 1
      if (totalPage.value > maxPage.value) {
        totalPage.value = maxPage.value
      }
    }
  })
  watchDeep(selectCable, (o, n) => {
    maxPage.value = selectCable.value.floorCount
    totalPage.value = maxPage.value
  })
  watch(totalPage, (o, n) => {
    console.log(totalPage.value)
    if (hasKeyInRef(formData, 'totalNumber')) {
      formData.value.totalNumber = totalPage.value * 100
    } else if (hasKeyInRef(formData, 'messageNumber')) {
      formData.value.messageNumber = totalPage.value * 100
    } else if (hasKeyInRef(formData, 'bwCount')) {
      formData.value.bwCount = totalPage.value * 100
    } else if (hasKeyInRef(formData, 'count')) {
      formData.value.count = totalPage.value * 100
    }
  })
  const openPreviewModel = (data) => {
    getCableFloorAllByID({id: data.id}).then(res => {
      previewModal.value = true
      pageData.value = res.data
      // page.value.pageNumber = res.data.length
    })
  }

  /**
   * 检测 ref 对象中是否存在指定 key
   * @param {Ref} refObj - Vue3 的 ref 对象
   * @param {string} key - 要检测的 key
   * @returns {boolean} - key 是否存在
   */
  function hasKeyInRef(refObj, key) {
    // 确保 refObj 是 ref 对象且不为空
    if (!refObj || typeof refObj.value !== 'object' || refObj.value === null) {
      return false;
    }

    // 检测 key 是否存在
    return key in refObj.value;
  }

  const cancelTrainModal = () => {
    // page.value.current = 1
    previewModal.value = false
  }
  const onSelectCable = (e, index) => {
    activeIndex.value = index
    formData.value.startPage = 1
    formData.value.cableId = e.id
    selectCable.value = e
    formData.value.type = e.codeType
    formData.value.codeSort = e.codeSort
    if (formData.value.bwType) {
      formData.value.bwType = e.codeType === 0 ? 1 : e.codeType === 1 ? 3 : 4
    }
    //干扰报时处理长短码字段不一样问题
    if (formData.value.numberType !== undefined) {
      formData.value.numberType = e.codeSort === 1
    }
    //通播时处理长短码字段不一样问题
    if (formData.value.isShort !== undefined) {
      formData.value.isShort = e.codeSort === 1
    }
    //电子键组训时处理报文类型问题
    if (formData.value.messageType !== undefined) {
      formData.value.messageType = e.codeType
    }
  }
</script>

<style scoped lang="less">
  @import "../../common/styles/css/score";

  .patTelegraphBox .telegraph .key:nth-of-type(10n) {
    border-right: 2px solid rgba(0, 0, 0, 0) !important;
  }

  .details {
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
  }

  .LJ {
    .select-cable {
      top: -2px;
      right: 4px;
      color: #33B34B
    }

    .icon {
      font-size: 20px;
      color: #33B34B;
      position: absolute;
      top: 2px;
      right: 20px;
    }

    .border-cable {
      border: 1px solid #474c4b;
      border-radius: 6px;
    }

    .border-select {
      border: 1px solid #33B34B;
      border-radius: 6px;
    }
  }

  .HJ {
    .select-cable {
      top: -2px;
      right: 4px;
      color: #6ebdff
    }

    .icon {
      font-size: 20px;
      color: #6ebdff;
      position: absolute;
      top: 2px;
      right: 20px;
    }

    .border-cable {
      border: 1px solid #3d586f;
      border-radius: 6px;
    }

    .border-select {
      border: 1px solid #6ebdff;
      border-radius: 6px;
    }
  }

  .HJJ {
    .select-cable {
      top: -2px;
      right: 4px;
      color: #e2d6b0
    }

    .icon {
      font-size: 20px;
      color: #e2d6b0;
      position: absolute;
      top: 2px;
      right: 20px;
    }

    .border-cable {
      border: 1px solid #3d586f;
      border-radius: 6px;
    }

    .border-select {
      border: 1px solid #e2d6b0;
      border-radius: 6px;
    }
  }
  .KJ {
    .select-cable {
      top: -2px;
      right: 4px;
      color: #508de6
    }

    .icon {
      font-size: 20px;
      color: #508de6;
      position: absolute;
      top: 2px;
      right: 20px;
    }

    .border-cable {
      border: 1px solid #29527e;
      border-radius: 6px;
    }

    .border-select {
      border: 1px solid #508de6;
      border-radius: 6px;
    }
  }

</style>