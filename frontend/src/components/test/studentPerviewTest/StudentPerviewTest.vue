<template>
  <div class="" style="padding: 20px 10px 20px 40px; height: 100%" v-if="paperData" :style="{ height: height ? height : '660px' }">
    <div class="w-full" style="font-size: 22px; font-weight: 800; text-align: center">{{ paperData.name ? paperData.name : '暂无试卷名称' }}</div>
    <div class="w-full" style="font-size: 14px; text-align: center; color: #7c8fad">
      <span>总分：{{ paperData.total }}分</span>
    </div>
    <div style="overflow-y: auto; margin-top: 20px" :style="{ height: height ? 'calc(100% - 38px - 40px)' : 'calc(100% - 38px)' }">
      <div v-for="i in bankList">
        <div v-if="paperData && paperData[i.key].length !== 0" style="font-size: 18px; font-weight: 600; display: flex; padding-right: 10px; align-items: center; margin-bottom: 10px">
          {{ i.name }}
          <span class="gardColor" style="flex: 1; display: inline-block; border-bottom: 1px dashed #5d76a0; margin: 5px 10px"></span>
          <span class="gardColor">( 共{{ paperData[i.key].length }}小题，共{{ calculateScore(paperData[i.key]) }}分 )</span>
        </div>
        <div v-for="(j, index) in paperData[i.key]" class="layout-left-top" style="padding: 10px 0 10px 30px; position: relative">
          <IconFont v-if="j.correctAnswer && j.answer.toString().trim() == j.correctAnswer.toString().trim() && isGarde" type="icon-gou1" style="position: absolute; left: 0; color: #78e775; font-size: 20px"></IconFont>
          <IconFont v-else-if="isGarde && !(j.correctAnswer && j.answer.toString() == j.correctAnswer.toString())" type="icon-cha" style="position: absolute; left: 0; color: #d81e06; font-size: 20px"></IconFont>
          <div style="width: 20px">
            <span>{{ index + 1 }}、</span>
          </div>
          <div style="width: calc(100% - 20px); display: flex; padding-right: 10px">
            <StudentPreviewTheTopic style="flex: 1" :params="j" :bool="true" :noFirst="paperData.noFirst" :isGarde="isGarde" :clearAnswer="clearAnswer" :isAnswer="isAnswer" />
            <div class="gardColor">( {{ j.score }} 分 )</div>
          </div>
        </div>
      </div>
      <div class="w-full h-full layout-center" v-if="paperData && paperData.singleChoice.length + paperData.multipleChoice.length + paperData.judge.length + paperData.completion.length === 0">
        <nomore />
      </div>
    </div>
  </div>
</template>

<script>
import { defineComponent, ref, toRefs, watch } from 'vue'
import useStudentQues from '../studentQues/js/useStudentQues'
import StudentPreviewTheTopic from '../StudentPreviewTheTopic/StudentPreviewTheTopic.vue'
import { createFromIconfontCN } from '@ant-design/icons-vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
export default defineComponent({
  name: 'PreviewTest',
  props: {
    paperData: Object,
    height: String,
    teacherStart: {
      type: Boolean,
      default: false
    },
    isAnswer: {
      type: Boolean,
      default: true
    },
    clearAnswer: {
      //是否为答题，编写答案
      type: Boolean,
      default: false
    },
    isGarde: {
      //是否为评分，编写答案
      type: Boolean,
      default: false
    },
    time: {
      type: String,
      default: '--'
    }
  },
  components: {
    StudentPreviewTheTopic,
    IconFont
  },
  setup(props, context) {
    const { paperData } = toRefs(props)
    paperData.value.multipleChoice.forEach(item => {
      if (item.correctAnswer) {
        item.correctAnswer = item.correctAnswer.sort((a, b) => a - b)
      }
    })
    const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
    const bankList = ref([
      {
        name: '一、单选题',
        key: 'singleChoice'
      },
      {
        name: '二、多选题',
        key: 'multipleChoice'
      },
      {
        name: '三、判断题',
        key: 'judge'
      },
      {
        name: '四、填空题',
        key: 'completion'
      },
      {
        name: '五、简答题',
        key: 'shortAnswer'
      }
    ])
    const Earray = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K']
    const calculateScore = e => {
      let score = 0
      for (let j of e) {
        score = score + j.score
      }
      return score
    }
    return {
      calculateScore,
      bankList,
      Earray,
      userRole
    }
  }
})
</script>

<style lang="less" scoped>
  .HJ{
    .exam_test_paper {
      padding: 20px 10px 20px 40px;
      height: 100%;
      color: #fff;
      overflow: auto;
      .exam_test_paper_title {
        font-size: 22px;
        font-weight: 800;
        text-align: center;
      }
      .all_number {
        font-size: 14px;
        text-align: center;
        color: #7c8fad;
        span {
          margin: 0 10px;
        }
      }
      .title_list_box {
        display: flex;
        flex-direction: column;
        .title_type {
          width: 100%;
          .subject_title {
            font-size: 18px;
            font-weight: 600;
            display: flex;
            padding-right: 10px;
            align-items: center;
            margin-bottom: 10px;
            span:nth-child(2) {
              flex: 1;
              display: inline-block;
              border-bottom: 1px dashed #5d76a0;
              margin: 5px 10px;
            }
            span:nth-child(3) {
              color: rgb(110, 189, 255);
            }
          }
          .title_item {
            padding: 10px 0 10px 30px;
            position: relative;
            display: flex;
            flex-direction: row;
            justify-content: space-between;
            .icon {
              position: absolute;
              left: 0;
              color: #78e775;
              font-size: 20px;
            }
            .sort {
              width: 20px;
              font-size: 15px;
            }
          }
        }
      }
    }
    .gardColor{
      color: rgb(110, 189, 255)
    }
  }
  .HJJ{
    .exam_test_paper {
      padding: 20px 10px 20px 40px;
      height: 100%;
      color: #fff;
      overflow: auto;
      .exam_test_paper_title {
        font-size: 22px;
        font-weight: 800;
        text-align: center;
      }
      .all_number {
        font-size: 14px;
        text-align: center;
        color: #7c8fad;
        span {
          margin: 0 10px;
        }
      }
      .title_list_box {
        display: flex;
        flex-direction: column;
        .title_type {
          width: 100%;
          .subject_title {
            font-size: 18px;
            font-weight: 600;
            display: flex;
            padding-right: 10px;
            align-items: center;
            margin-bottom: 10px;
            span:nth-child(2) {
              flex: 1;
              display: inline-block;
              border-bottom: 1px dashed #5d76a0;
              margin: 5px 10px;
            }
            span:nth-child(3) {
              color: #ff6922;
            }
          }
          .title_item {
            padding: 10px 0 10px 30px;
            position: relative;
            display: flex;
            flex-direction: row;
            justify-content: space-between;
            .icon {
              position: absolute;
              left: 0;
              color: #78e775;
              font-size: 20px;
            }
            .sort {
              width: 20px;
              font-size: 15px;
            }
          }
        }
      }
    }
    .gardColor{
      color: #ff6922
    }
  }
  .LJ{
    .exam_test_paper {
      padding: 20px 10px 20px 40px;
      height: 100%;
      color: #fff;
      overflow: auto;
      .exam_test_paper_title {
        font-size: 22px;
        font-weight: 800;
        text-align: center;
      }
      .all_number {
        font-size: 14px;
        text-align: center;
        color: #a9abaa;
        span {
          margin: 0 10px;
        }
      }
      .title_list_box {
        display: flex;
        flex-direction: column;
        .title_type {
          width: 100%;
          .subject_title {
            font-size: 18px;
            font-weight: 600;
            display: flex;
            padding-right: 10px;
            align-items: center;
            margin-bottom: 10px;
            span:nth-child(2) {
              flex: 1;
              display: inline-block;
              border-bottom: 1px dashed #384a42;
              margin: 5px 10px;
            }
            span:nth-child(3) {
              color: rgb(233, 222, 178);
            }
          }
          .title_item {
            padding: 10px 0 10px 30px;
            position: relative;
            display: flex;
            flex-direction: row;
            justify-content: space-between;
            .icon {
              position: absolute;
              left: 0;
              color: #78e775;
              font-size: 20px;
            }
            .sort {
              width: 20px;
              font-size: 15px;
            }
          }
        }
      }
    }
    .gardColor{
      color: rgb(52, 179, 76)
    }
  }
</style>
