<template>
  <div class="w-full mb-1 test" v-if="params">
    <div>
      <span style="font-size: 15px; color: rgb(226, 242, 255)" v-if="params.type != 4">( {{ typeList[params.type - 1] }} ){{ params.topic }}{{ params.type == '4' ? '' : '' }}</span>
      <div v-if="params.type == 4 && !clearAnswer" style="font-size: 15px; padding-bottom: 10px">
        <span v-for="(m, k) in params.dom" :key="k">
          <span v-if="m.text !== ''">{{ m.text }}</span>
          <span class="answerTK" :style="m.answer == '' ? 'padding:0 30px' : ''" v-if="m.answer">{{ m.answer }}</span>
        </span>
      </div>
      <div v-if="params.type == 4 && clearAnswer" style="font-size: 15px; padding-bottom: 10px">
        <span v-for="(m, k) in params.dom" :key="k">
          <div class="answerTK" contenteditable="true" @keyup="changeAnswer($event, k)" style="min-width: 50px; display: inline-block" v-if="m.text == '' && k == 0">
            {{ noFirst ? m.answer : '' }}
          </div>
          <span v-if="m.text !== ''">{{ m.text }}</span>
          <div class="answerTK" contenteditable="true" @keyup="changeAnswer($event, k)" style="min-width: 50px; display: inline-block" v-if="m.text && m.answer !== undefined">
            {{ noFirst ? m.answer : '' }}
          </div>
        </span>
      </div>
    </div>
    <div v-if="params.type == 1" style="padding: 10px 0">
      <a-row class="w-full" v-if="isGarde">
        <a-col style="margin: 5px 0" :span="12" v-for="item in params.options" :key="item.value">
          <div style="color: #e2f2ff; position: relative; margin-right: 10px">
            <div :class="[params.answer == item.value && params.correctAnswer ? 'studentAnsCon' : '']" style="color: #8fa3be">
              <IconFont type="icon-gou1" class="studentAns" v-if="params.answer == item.value && params.correctAnswer"></IconFont>
              <span style="padding-left: 30px"> {{ Earray[item.value] + '、' + item.label }}</span>
            </div>
          </div>
        </a-col>
      </a-row>
      <a-radio-group v-else @change="changeSelect" class="w-full" v-model:value="params.answer" :disabled="!clearAnswer">
        <a-row class="w-full">
          <a-col style="margin: 5px 0" :span="12" v-for="item in params.options">
            <a-radio style="color: #e2f2ff" :value="item.value">{{ Earray[item.value] + '、' + item.label }} </a-radio>
          </a-col>
        </a-row>
      </a-radio-group>
    </div>
    <div v-if="params.type == 2" style="padding: 10px 0">
      <a-row class="w-full" v-if="isGarde">
        <a-col style="margin: 5px 0" :span="12" v-for="item in params.options" :key="item.value">
          <div style="color: #e2f2ff; position: relative; margin-right: 10px">
            <div :class="[params.answer.some(p => p == item.value) && params.correctAnswer ? 'studentAnsCon' : '']" style="color: #8fa3be">
              <IconFont type="icon-gou1" class="studentAns" v-if="params.answer.some(p => p == item.value) && params.correctAnswer"></IconFont>
              <span style="padding-left: 30px; color: #8fa3be"> {{ Earray[item.value] + '、' + item.label }}</span>
            </div>
          </div>
        </a-col>
      </a-row>
      <a-checkbox-group v-else @change="changeSelect" class="w-full" v-model:value="params.answer" :disabled="!clearAnswer">
        <a-row class="w-full">
          <a-col v-for="item in params.options" style="margin: 5px 0" :span="12">
            <a-checkbox style="color: #8fa3be" :value="item.value">{{ Earray[item.value] + '、' + item.label }} </a-checkbox>
          </a-col>
        </a-row>
      </a-checkbox-group>
    </div>
    <div v-if="params.type == 3" style="padding: 10px 0">
      <a-row class="w-full" v-if="isGarde">
        <a-col style="margin: 5px 0" :span="12" v-for="(item, index) in params.options" :key="index">
          <div style="color: #e2f2ff; position: relative">
            <div :class="[params.answer == index + 1 && params.correctAnswer ? 'studentAnsCon' : '']" style="color: #8fa3be">
              <IconFont type="icon-gou1" class="studentAns" v-if="params.answer == index + 1 && params.correctAnswer"></IconFont>
              <span style="padding-left: 30px; color: #8fa3be"> {{ item.name }}</span>
            </div>
          </div>
        </a-col>
      </a-row>
      <a-radio-group v-else @change="changeSelect" v-model:value="params.answer" :disabled="!clearAnswer">
        <a-row class="w-full">
          <a-col style="margin: 10px 0" :span="12" v-for="item in params.options">
            <a-radio style="color: #e2f2ff" :value="item.id">{{ item.name }}</a-radio>
          </a-col>
        </a-row>
      </a-radio-group>
    </div>
    <div v-if="params.type == 4"></div>
    <div v-if="params.type == 5">
      <a-textarea v-model:value="params.answer" @change="changeSelect" :disabled="isGarde"></a-textarea>
    </div>
    <div v-if="!isAnswer" style="color: #78e775">
      正解：
      <span v-if="params.type == '1'">{{ Earray[params.answer] }}</span>
      <template v-for="(ite, index) in params.answer" :key="index">
        <span v-if="params.type == '2'"> {{ Earray[ite] }}{{ index == params.answer.length - 1 ? '' : '、' }} </span>
      </template>
      <template v-for="(ite, index) in params.answer" :key="index">
        <span v-if="params.type == '3'">{{ ite == '1' ? '对' : '错' }}</span>
      </template>
      <template v-for="(ite, index) in params.answer" :key="index">
        <span v-if="params.type == '4'"> {{ ite }}{{ index == params.answer.length - 1 ? '' : '、' }} </span>
      </template>
      <template v-for="(ite, index) in params.answer" :key="index">
        <span v-if="params.type == '5'">{{ ite }}</span>
      </template>
    </div>
    <div class="layout-left-top" style="color: #78e775; padding-bottom: 10px; justify-content: space-between" v-if="isGarde">
      <div class="layout-left-top">
        正确答案：
        <div v-if="(params.type == 2 || params.type == 1) && params.correctAnswer && isGarde" style="padding-right: 20px">
          <template v-if="params.correctAnswer?.length">
            <span v-for="v in params.correctAnswer" :key="v">{{ Earray[v] }}</span>
          </template>
          <span v-else>{{ Earray[params.correctAnswer] }}</span>
        </div>
        <div v-else-if="(params.type == 2 || params.type == 1) && !params.correctAnswer && isGarde" style="padding-right: 20px">
          <span v-for="v in params.correctAnswer" :key="v">{{ Earray[v] }}</span>
        </div>
        <div v-if="params.type == 3 && params.correctAnswer && isGarde" style="padding-right: 20px">
          <span>{{ params.correctAnswer == '1' ? '对' : '错' }}</span>
        </div>
        <div v-else-if="params.type == 3 && !params.correctAnswer && isGarde" style="padding-right: 20px">
          <span>{{ params.correctAnswer == '1' ? '对' : '错' }}</span>
        </div>
        <div v-if="params.type == 4 && params.correctAnswer && isGarde" style="padding-right: 20px">
          <span style="padding-right: 5px" v-for="v in params.correctAnswer" :key="v">{{ v }}</span>
        </div>
        <div v-else-if="params.type == 4 && !params.correctAnswer && isGarde" style="padding-right: 20px">
          <span style="padding-right: 5px" v-for="v in params.correctAnswer" :key="v">{{ v }}</span>
        </div>
        <div v-if="params.type == 5 && params.correctAnswer && isGarde" style="padding-right: 20px">
          <span style="padding-right: 5px">{{ params.correctAnswer }}</span>
        </div>
        <div v-else-if="params.type == 5 && !params.correctAnswer && isGarde" style="padding-right: 20px">
          <span style="padding-right: 5px">{{ params.correctAnswer }}</span>
        </div>
      </div>
      <div v-if="params.type == 5 && params.correctAnswer && isGarde" style="color: white">该题得分：{{ params.teacherScore }}</div>
    </div>
    <div v-if="!isAnswer || (examState && examState == 4)" style="color: #fd8029">解析：{{ params.analysis ? params.analysis : '暂无解析' }}</div>
  </div>
</template>

<script>
import { defineComponent, toRefs, ref, watch, inject, onMounted } from 'vue'
import { createFromIconfontCN } from '@ant-design/icons-vue'
// import useRoomTest from './js/usePreviewTheTopic.js'
import { useRouter, useRoute } from 'vue-router'
import { deepClone } from '../../../common/utils/Utils.js'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
export default defineComponent({
  name: 'PreviewTheTopic',
  components: {
    IconFont
  },
  props: {
    params: Object,
    bool: Boolean,
    isAnswer: Boolean,
    clearAnswer: Boolean,
    isGarde: Boolean,
    noFirst: Boolean //学员是否为第一次进入考试
  },

  setup(props, context) {
    const typeList = ref(['单选', '多选', '判断', '填空', '简答'])
    const fun = inject('realTimeAnwser')
    const Earray = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K']
    const route = useRoute()
    const examState = ref(route.query.state)
    const router = useRouter()
    const { params, clearAnswer, noFirst, isGarde } = toRefs(props)
    if (clearAnswer.value && !noFirst.value) {
      params.value.correctAnswer = deepClone(params.value.answer)
      if (params.value.type == 1) {
        params.value.answer = ' '
      }
      if (params.value.type == 2) {
        if (clearAnswer.value) {
          for (let i in params.value.answer) {
            params.value.answer[i] = ''
          }
        }
      }
      if (params.value.type == 3) {
        params.value.answer = ' '
      }
      if (params.value.type == 5) {
        params.value.answer = ' '
      }
    }
    onMounted(() => {
      analyzeTheTopic()
    })
    const analyzeTheTopic = () => {
      if (params.value.type == 4) {
        let arr
        if (params.value.topic.indexOf('________') !== -1) {
          arr = params.value.topic.split('________')
        } else if (params.value.topic.indexOf('(___)') !== -1) {
          arr = params.value.topic.split('(___)')
        } else if (params.value.topic.indexOf('$_$') !== -1) {
          arr = params.value.topic.split('$_$')
        }
        let dom = []
        for (let index in arr) {
          //考试时清空答案
          if (clearAnswer.value) {
            dom.push({
              text: arr[index],
              answer: params.value.answer[index]
            })
          } else {
            if (params.value.correctAnswer) {
              dom.push({
                text: arr[index],
                answer: params.value.answer[index]
              })
            } else if (isGarde.value) {
              dom.push({
                text: arr[index],
                answer: params.value.answer[index] ? ' ' : params.value.answer[index]
              })
            } else {
              dom.push({
                text: arr[index],
                answer: params.value.answer[index]
              })
            }
          }
        }
        params.value.dom = dom
        if (clearAnswer.value && !noFirst.value) {
          for (let i in params.value.answer) {
            params.value.answer[i] = ''
          }
        }
      }
    }
    const changeAnswer = (e, k) => {
      params.value.answer[k] = e.target.innerText
      if (e.target.innerText.trim() !== '') {
        params.value.isAnswer = true
      } else {
        params.value.isAnswer = false
      }
      fun.realTimeAnwser()
    }
    const changeSelect = () => {
      params.value.isAnswer = true
      if (params.value.type == 2) {
        params.value.answer = params.value.answer.sort((x, y) => x - y)
      }
      if (`${params.value.answer}` == '') {
        params.value.isAnswer = false
      }
      if (params.value.type == 2 && params.value.answer.every(item => item == '')) {
        params.value.isAnswer = false
      }
      fun.realTimeAnwser()
    }
    return {
      params,
      typeList,
      Earray,
      changeAnswer,
      clearAnswer,
      changeSelect,
      examState,
      noFirst,
      analyzeTheTopic
    }
  }
})
</script>

<style lang="less">
.HJ{
  .studentAns {
    position: absolute;
    left: 5px;
    color: rgb(110, 189, 255);
    font-size: 20px;
  }
  .studentAnsCon {
    width: max-content;
    background: rgba(110, 189, 255, 0.1);
    max-width: 100%;
    padding: 3px 5px 3px 5px;
    border: 1px dashed rgba(110, 189, 255, 1);
    color: rgba(110, 189, 255, 1) !important;
  }
  .test {
    .ant-input-number .ant-input {
      background-color: #172b47 !important;
    }
    .ant-radio-wrapper {
      color: #8fa3be !important;
    }
    .ant-checkbox + span {
      color: #8fa3be;
    }
    .ant-radio-disabled + span {
      color: #8fa3be;
    }
  }
  .answerTK {
    padding: 2px 10px;
    border-bottom: 1px solid #354971;
    margin: 0 3px;
    color: #6ebdff;
  }
}
  .HJJ{
    .studentAns {
      position: absolute;
      left: 5px;
      color: rgb(110, 189, 255);
      font-size: 20px;
    }
    .studentAnsCon {
      width: max-content;
      background: rgba(110, 189, 255, 0.1);
      max-width: 100%;
      padding: 3px 5px 3px 5px;
      border: 1px dashed rgba(110, 189, 255, 1);
      color: rgba(110, 189, 255, 1) !important;
    }
    .test {
      .ant-input-number .ant-input {
        background-color: #172b47 !important;
      }
      .ant-radio-wrapper {
        color: #8fa3be !important;
      }
      .ant-checkbox + span {
        color: #8fa3be;
      }
      .ant-radio-disabled + span {
        color: #8fa3be;
      }
    }
    .answerTK {
      padding: 2px 10px;
      border-bottom: 1px solid #354971;
      margin: 0 3px;
      color: #6ebdff;
    }
  }
  .LJ{
    .c-53a165{
      background:#00910d ;
      color:#ffffff ;
      width: max-content;
      padding: 0 10px;
      margin: 10px 0;
    }
    .c-8b5f2f{
      background: #d7551e;
      color: #ffffff;
      width: max-content;
      padding: 0 10px;
      margin: 10px 0;
    }
    .studentAns{
      position: absolute;left: 5px;color: #fff;font-size: 20px;
    }
    .studentAnsCon{
      width: max-content;background:rgb(58,57,54);
      max-width: 100%;
      padding: 3px 5px 3px 5px;
      border: 1px dashed #afa48a;
      color: #fff !important;
    }
    .test {
      .ant-input-number .ant-input {
        background-color: #172b47 !important;
      }
      .ant-radio-wrapper{
        color: #ffffff!important;
      }
      .ant-checkbox + span{
        color: #ffffff;
      }
      /*.ant-checkbox {*/
      /*    border: 1px solid #f1f4f5;*/
      /*}*/
      .ant-radio-disabled + span{
        color: #ffffff;
      }
    }
    .answerTK{
      padding: 2px 10px;
      border-bottom: 1px solid #ffffff;
      margin: 0 3px;
      color: #00f516;
    }
  }
</style>
