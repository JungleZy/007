<template>
  <div class="w-full h-full">
    <div class="telegrapHead">
      <div>页码：【{{ props.curr }}/{{ props.all }}】<span style="padding-left: 20px" v-if="header">报头：{{header}}</span></div>
      <div class="title_name">{{ details?.name }}</div>
      <div class="page" v-if="props.all > 1">
        <div :class="{ pag: true, disabled: props.curr == 1 }" @click="pageTurn('prev')">上一页</div>
        <div :class="{ pag: true, disabled: props.curr == props.all }" @click="pageTurn('next')">下一页</div>
      </div>
    </div>
    <div class="w-full" style="height: calc(100% - 40px)">
      <div class="patTelegraph" style="height: 100%">
        <div class="telegraph">
          <div class="rowHead">
            <div class="key">1</div>
            <div class="key">2</div>
            <div class="key">3</div>
            <div class="key">4</div>
            <div class="key">5</div>
            <div class="key">6</div>
            <div class="key">7</div>
            <div class="key">8</div>
            <div class="key">9</div>
            <div class="key">10</div>
          </div>
          <div class="keyBox" v-if="props.result.pageVos">
            <template v-for="(key, index) in props.result.pageVos" :key="index">
              <div class="key">
                <div class="title">{{ key.key }}</div>
                <div class="val">
                  <div class="w-full nobr" v-if="props.result.value[index]" :title="props.result.value[index]">
                    <strong v-for="(v, i) in props.result.value[index].split('')" :key="i" :style="{ color: key.key.split('')[i] != v ? '#d11d1d' : '#fff' }">{{ v }}</strong>
                  </div>
                  <div class="w-full" v-else>&#45;&#45;</div>
                </div>
              </div>
            </template>
          </div>
        </div>
        <div class="serial">
          <div class="ser head"></div>
          <div class="ser">10</div>
          <div class="ser">20</div>
          <div class="ser">30</div>
          <div class="ser">40</div>
          <div class="ser">50</div>
          <div class="ser">60</div>
          <div class="ser">70</div>
          <div class="ser">80</div>
          <div class="ser">90</div>
          <div class="ser">100</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'TrainResult'
  }
</script>
<script setup>
  import {onMounted,ref} from 'vue'
  import { defineEmits, watch, defineProps } from 'vue'
  import {findHeader} from "../../../../common/api/ReceiveApi.js";
  import {useRoute} from "vue-router"
  const route = useRoute();
  const props = defineProps({
    result: {
      type: Object,
      default: {}
    },
    curr: {
      type: Number,
      default: 1
    },
    all: {
      type: Number,
      default: 1
    },
    details: {
      type: Object
    }
  })
  const emit = defineEmits(['switchPage'])
  const header = ref('')
  onMounted(()=>{
    findHeader(route.query.id).then(res=>{
      if(res.data!==null){
        const arr = res.data.content.split(' ')
        arr[3] = arr[3].replaceAll("T",'0')
        arr[4] = arr[4].replaceAll("T",'0')
        arr.forEach(item=>{
          header.value +=" "+item
        })
      }
    })
  })
  watch(props, () => {
    if (props.result.pageVos && props.result.pageVos.length < 100) {
      for (let i = 0; i < 100; i++) {
        if(props.result.pageVos[i]){

        }else {
          props.result.pageVos.push({ key: '--' })
        }
      }
    }
  })

  const pageTurn = type => {
    if ((type == 'next' && props.curr == props.all) || (type == 'prev' && props.curr == 1)) return false
    emit('switchPage', type)
  }
</script>

<style lang="less" scoped>
  .HJ,.HJJ{
    .telegrapHead {
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 36px 10px 16px;
      .page {
        height: 30px;
        display: flex;
        align-items: center;
        flex-shrink: 0;
        .pag {
          height: 26px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #70a3b8;
          font-size: 13px;
          position: relative;
          padding: 0 20px;
          border: 1px solid #70a3b8;
          cursor: pointer;
          &:hover {
            background-color: #4c7595;
            color: #fff;
          }
          &.disabled {
            opacity: 0.3;
            cursor: no-drop;
            &:hover {
              background-color: transparent;
              color: #4c7595;
            }
          }
          & + .pag {
            margin-left: 10px;
          }
        }
      }
    }
    .patTelegraph {
      width: 100%;
      display: flex;
      position: relative;
    }
    .patTelegraph .serial {
      width: 30px;
      margin-left: 5px;
      display: flex;
      flex-direction: column;
      color: #8eafca;
      align-content: stretch;
    }
    .patTelegraph .serial .ser {
      height: calc((100% - 30px) / 10);
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .serial .ser.head {
      height: 30px;
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .telegraph {
      width: 100%;
      height: 100%;
      border: 1px solid #171e27;
      background-color: #213141;
      display: flex;
      flex-direction: column;
    }
    .patTelegraph .telegraph .rowHead {
      width: 100%;
      height: 30px;
      display: flex;
      flex-shrink: 0;
    }
    .patTelegraph .telegraph .keyBox {
      display: flex;
      flex-wrap: wrap;
      height: 100%;
      min-height: 480px;
    }
    .patTelegraph .telegraph .keyBox .key {
      min-height: 48px;
    }
    .patTelegraph .telegraph .key {
      color: #fff;
      width: 10%;
      height: 10%;
      text-align: center;
      border-right: 1px solid #171e27;
    }
    .patTelegraph .telegraph .key:nth-of-type(10n) {
      border-right: none;
    }
    .patTelegraph .telegraph .key:nth-of-type(n + 11) {
      border-top: 1px solid #171e27;
    }
    .patTelegraph .telegraph .rowHead .key {
      color: #161e29;
      height: 100%;
      font-weight: bold;
      font-size: 18px;
      background-color: #4c7595;
      border: none;
    }
    .patTelegraph .telegraph .key .title {
      font-size: 14px;
      height: 50%;
      background-color: #463e2f;
      color: #fff;
      font-weight: normal;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .patTelegraph .telegraph .key .val {
      font-size: 15px;
      height: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 3px;
    }
    .patTelegraph .telegraph .key .error {
      color: #d11d1d;
      font-weight: bold;
    }
    .patTelegraph .telegraph .key .omission {
      color: #4c7595;
      font-weight: bold;
    }
  }
  .LJ{
    .telegrapHead {
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 36px 10px 16px;
      .page {
        height: 30px;
        display: flex;
        align-items: center;
        flex-shrink: 0;
        .pag {
          height: 26px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #70a3b8;
          font-size: 13px;
          position: relative;
          padding: 0 20px;
          border: 1px solid #70a3b8;
          cursor: pointer;
          &:hover {
            background-color: #4c7595;
            color: #fff;
          }
          &.disabled {
            opacity: 0.3;
            cursor: no-drop;
            &:hover {
              background-color: transparent;
              color: #4c7595;
            }
          }
          & + .pag {
            margin-left: 10px;
          }
        }
      }
    }
    .patTelegraph {
      width: 100%;
      display: flex;
      position: relative;
    }
    .patTelegraph .serial {
      width: 30px;
      margin-left: 5px;
      display: flex;
      flex-direction: column;
      color: #a9abaa;
      align-content: stretch;
    }
    .patTelegraph .serial .ser {
      height: calc((100% - 30px) / 10);
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .serial .ser.head {
      height: 30px;
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .telegraph {
      width: 100%;
      height: 100%;
      border: 1px solid #171e27;
      background-color: rgba(21,39,39,0.5);
      display: flex;
      flex-direction: column;
    }
    .patTelegraph .telegraph .rowHead {
      width: 100%;
      height: 30px;
      display: flex;
      flex-shrink: 0;
    }
    .patTelegraph .telegraph .keyBox {
      display: flex;
      flex-wrap: wrap;
      height: 100%;
      min-height: 480px;
    }
    .patTelegraph .telegraph .keyBox .key {
      min-height: 48px;
    }
    .patTelegraph .telegraph .key {
      color: #fff;
      width: 10%;
      height: 10%;
      text-align: center;
      border-right: 1px solid #171e27;
    }
    .patTelegraph .telegraph .key:nth-of-type(10n) {
      border-right: none;
    }
    .patTelegraph .telegraph .key:nth-of-type(n + 11) {
      border-top: 1px solid #171e27;
    }
    .patTelegraph .telegraph .rowHead .key {
      color: #a9abaa;
      height: 100%;
      font-weight: bold;
      font-size: 18px;
      background-color: #38403d;
      border: none;
    }
    .patTelegraph .telegraph .key .title {
      font-size: 14px;
      height: 50%;
      background-color: #463e2f;
      color: #fff;
      font-weight: normal;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .patTelegraph .telegraph .key .val {
      font-size: 15px;
      height: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 3px;
    }
    .patTelegraph .telegraph .key .error {
      color: #d11d1d;
      font-weight: bold;
    }
    .patTelegraph .telegraph .key .omission {
      color: #4c7595;
      font-weight: bold;
    }
  }
  .KJ{
    .telegrapHead {
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 36px 10px 16px;
      .page {
        height: 30px;
        display: flex;
        align-items: center;
        flex-shrink: 0;
        .pag {
          height: 26px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #70a3b8;
          font-size: 13px;
          position: relative;
          padding: 0 20px;
          border: 1px solid #70a3b8;
          cursor: pointer;
          &:hover {
            background-color: #4c7595;
            color: #fff;
          }
          &.disabled {
            opacity: 0.3;
            cursor: no-drop;
            &:hover {
              background-color: transparent;
              color: #4c7595;
            }
          }
          & + .pag {
            margin-left: 10px;
          }
        }
      }
    }
    .patTelegraph {
      width: 100%;
      display: flex;
      position: relative;
    }
    .patTelegraph .serial {
      width: 30px;
      margin-left: 5px;
      display: flex;
      flex-direction: column;
      color: #a9abaa;
      align-content: stretch;
    }
    .patTelegraph .serial .ser {
      height: calc((100% - 30px) / 10);
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .serial .ser.head {
      height: 30px;
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .telegraph {
      width: 100%;
      height: 100%;
      border: 1px solid #171e27;
      background-color: rgba(31, 67, 99, 0.3);
      display: flex;
      flex-direction: column;
    }
    .patTelegraph .telegraph .rowHead {
      width: 100%;
      height: 30px;
      display: flex;
      flex-shrink: 0;
    }
    .patTelegraph .telegraph .keyBox {
      display: flex;
      flex-wrap: wrap;
      height: 100%;
      min-height: 480px;
      background: rgba(31, 67, 99, 0.6);
    }
    .patTelegraph .telegraph .keyBox .key {
      min-height: 48px;
    }
    .patTelegraph .telegraph .key {
      color: #fff;
      width: 10%;
      height: 10%;
      text-align: center;
      border-right: 1px solid #171e27;
    }
    .patTelegraph .telegraph .key:nth-of-type(10n) {
      border-right: none;
    }
    .patTelegraph .telegraph .key:nth-of-type(n + 11) {
      border-top: 1px solid #171e27;
    }
    .patTelegraph .telegraph .rowHead .key {
      color: #a9abaa;
      height: 100%;
      font-weight: bold;
      font-size: 18px;
      background-color: rgba(80,141,230,0.6);
      border: none;
    }
    .patTelegraph .telegraph .key .title {
      font-size: 14px;
      height: 50%;
      background-color: #463e2f;
      color: #fff;
      font-weight: normal;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .patTelegraph .telegraph .key .val {
      font-size: 15px;
      height: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 3px;
    }
    .patTelegraph .telegraph .key .error {
      color: #d11d1d;
      font-weight: bold;
    }
    .patTelegraph .telegraph .key .omission {
      color: #4c7595;
      font-weight: bold;
    }
  }
</style>
