<template>
  <div class="w-full h-full content-mask-bg">
    <a-alert v-if="entryError" type="error" :message="entryError" show-icon />
    <a-button v-if="entryError" @click="loadEntry">重新读取训练</a-button>
    <div v-if="entryRole === 'teacher'" class="w-full h-full"><BroadTeacher /></div>
    <div v-else-if="entryRole === 'student'" class="w-full h-full"><BroadStudent /></div>
  </div>
</template>

<script>
export default {
  name: 'BroadcastTeachTrain'
}
</script>
<script setup>
import BroadTeacher from '../../../../components/BroadcastTeachTrain/BroadTeacher.vue'
import BroadStudent from '../../../../components/BroadcastTeachTrain/BroadStudent.vue'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getRoomDetail } from '../../../../common/api/broaddcastTeacheingApi'
const route = useRoute()
const entryRole = ref(null)
const entryError = ref('')
const loadEntry = async () => {
  entryError.value = ''
  try {
    const response = await getRoomDetail({ roomId: Number(route.query.id) })
    if (response.code !== 200 || !response.data) throw new Error(response.msg || '无权查看该训练')
    const room = response.data
    entryRole.value = room.teacher === true ? 'teacher' : 'student'
  } catch (error) {
    entryError.value = error.message || '读取训练身份失败'
  }
}
onMounted(loadEntry)
</script>

<style lang="less" scoped>
.trainBoxs {
  display: flex;
  .userListBox {
    height: calc(100% - 60px);
    margin-bottom: 10px;
    .title {
      display: flex;
      align-items: center;
      height: 40px;
      padding: 0 16px;
      font-weight: bolder;
      font-size: 16px;
      background: url('../../../../assets/HJ/train/militaryBg.png') no-repeat center;
      background-size: 100% 100%;
    }
    .userList {
      height: calc(100% - 40px);
      padding: 8px;
      .user {
        height: 36px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 8px;
        margin-bottom: 6px;
        &:hover {
          background-color: #243443;
        }
        .avaImg {
          width: 26px;
          height: 26px;
          border-radius: 50%;
          margin-right: 10px;
        }
        .tag {
          font-weight: bolder;
          color: #2fff00;
        }
        .Ntag {
          font-weight: bolder;
          color: #c70000;
        }
      }
    }
  }
  .trainCenter {
    padding: 10px;
    .patTelegraphBox {
      width: 100%;
      /*height: calc(100% - 360px);*/
      height: 100%;
      /*max-width: 960px;*/
      min-height: 420px;
      margin: 0 auto;
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
            color: #4c7595;
            font-size: 13px;
            position: relative;
            padding: 0 20px;
            border: 1px solid #4c7595;
            cursor: pointer;
            &:hover {
              background-color: #4c7595;
              color: #fff;
            }
            &.disabled {
              opacity: 0.4;
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
        .serial {
          width: 30px;
          margin-left: 6px;
          display: flex;
          flex-direction: column;
          color: #8eafca;
          align-content: stretch;
          .ser {
            height: calc((100% - 36px) / 10);
            flex-grow: 1;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            &.head {
              height: 36px;
              flex-grow: 1;
              flex-shrink: 0;
              display: flex;
              align-items: center;
            }
          }
        }

      }
    }
    .disposeBox {
      height: 360px;
      padding-top: 10px;
      display: flex;
      .groupBoxs {
        position: relative;
        border: 1px solid #3d586f;
        margin-top: 16px;
        padding-top: 10px;
        min-height: 120px;
        height: calc(50% - 16px);
        .groupTitle {
          font-size: 13px;
          color: #bbcdef;
          line-height: 20px;
          padding: 0 10px;
          background-color: #2e4559;
          position: absolute;
          left: 10px;
          top: -10px;
        }
        .rowItem {
          padding: 4px 0;
          display: flex;
          align-items: center;
          .lab {
            width: 78px;
            flex-shrink: 0;
            color: #7b90af;
            text-align: center;
          }
          .oper {
            width: 60px;
            flex-shrink: 0;
            color: #7b90af;
            display: flex;
            justify-content: center;
            .ico {
              font-size: 16px;
              cursor: pointer;
              & + .ico {
                margin-left: 6px;
              }
            }
          }
          .item {
            font-size: 12px;
            color: #7b90af;
            text-align: center;
            padding: 0 8px;
          }
          &.zhu {
            .item:nth-of-type(2),
            .item:nth-of-type(4) {
              width: calc((100% - 138px) * 0.3);
            }
            .item:nth-of-type(3) {
              width: calc((100% - 138px) * 0.4);
            }
          }
          &.code {
            .lab {
              width: 90px;
            }
            .item:nth-of-type(2),
            .item:nth-of-type(4) {
              width: calc((100% - 150px) * 0.3);
            }
            .item:nth-of-type(3) {
              width: calc((100% - 150px) * 0.4);
            }
          }
        }
      }
      .disposeItem {
        width: 50%;
        flex-shrink: 0;
        padding-right: 10px;
        height: 100%;
        & + .disposeItem {
          padding-left: 10px;
          padding-right: 0;
        }
      }
    }
  }
}

@keyframes glint {
  0% {
    -webkit-box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
  }
  25% {
    -webkit-box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
  }
  50% {
    -webkit-box-shadow: inset 0 0 16px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 16px rgba(233, 222, 178, 0.8);
  }
  75% {
    -webkit-box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
  }
  100% {
    -webkit-box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
  }
}
</style>
