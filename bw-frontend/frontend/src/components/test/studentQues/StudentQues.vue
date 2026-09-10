<template>
  <div class="w-full mb-1 studentQues">
    <div class="checkType w-full" style="position: relative">
      <span style="font-size: 20px; color: #e2f2ff" v-if="question.type != 4">{{ index + 1 }}、{{ question.topic }} ({{ question.type == 1 ? '单选' : question.type == 2 ? '多选' : question.type == 3 ? '判断' : '简答' }})</span>
      <IconFont :type="question.icon ? 'icon-gou1' : 'icon-cha'" style="margin-left: 10px; font-size: 20px" :style="question.icon ? 'color: #06b60b' : 'color: #d81e06'" v-if="question.icon !== undefined && score != 100 && question.type != 4"></IconFont>
      <div class="layout-left-bottom" v-if="question.type == 4 && score == 100">
        {{ index + 1 }}、
        <div v-for="(m, k) in dom" :key="k" class="layout-left-bottom">
          <span v-if="m.text !== ''">{{ m.text }}</span>
          <div class="answerTK" v-if="m.answer">{{ m.answer }}</div>
        </div>
        (填空)
      </div>
      <div class="layout-left-bottom" style="font-size: 20px" v-if="question.type == 4 && score != 100">
        {{ index + 1 }}、
        <div v-for="(m, k) in dom" :key="k" class="layout-left-bottom">
          <div class="answerTK" contenteditable="true" @keyup="changeAnswer($event, k)" style="min-width: 50px" v-if="m.text == '' && k == 0"></div>
          <span style="font-size: 20px" v-if="m.text !== ''">{{ m.text }}</span>
          <div class="answerTK" contenteditable="true" @keyup="changeAnswer($event, k)" style="min-width: 50px" v-if="m.text && m.answer !== undefined"></div>
        </div>
        (填空)
        <IconFont style="margin-left: 10px; font-size: 20px" :type="question.icon ? 'icon-gou1' : 'icon-cha'" :style="question.icon ? 'color: #06b60b' : 'color: #d81e06'" v-if="question.icon !== undefined"></IconFont>
      </div>
    </div>
    <div class="w-full ml-2" style="height: calc(100% - 23px); overflow-y: auto">
      <div class="quesTitle mt-2 layout-left-center" v-if="question.type != 4 && question.type != 5"></div>
      <div class="manyCheck layout-left-top">
        <a-row class="w-full" v-if="question.type == 4">
          <a-col :span="11" :offset="(index + 1) % 2 == 0 ? '2' : '0'" v-for="(item, index) in question.options" :key="index" class="p-1">
            <div class="layout-left-center w-full">
              <div style="width: 18px" class="layout-left-center">{{ Earray[index] }}</div>
              <div>{{ item.label }}</div>
            </div>
          </a-col>
        </a-row>
        <a-radio-group v-model:value="question.answer" name="radioGroup" v-if="question.type == 3" class="mt-1">
          <a-radio :value="item.id" v-for="(item, i) in question.options" :key="i" :disabled="score == 100">
            <span style="color: #e2f2ff">{{ item.name }}</span>
          </a-radio>
        </a-radio-group>

        <a-radio-group v-model:value="question.answer" name="radioGroup" v-if="question.type == 1" class="mt-1">
          <a-radio :value="item.value" v-for="(item, i) in question.options" :key="i" :disabled="score == 100">
            <span style="padding: 0 10px 0px 5px; color: #e2f2ff"> {{ Earray[i] }}</span
            ><span style="color: #e2f2ff">{{ item.label }}</span>
          </a-radio>
        </a-radio-group>

        <a-checkbox-group style="width: 100%" v-model:value="question.answer" v-if="question.type == 2">
          <a-row class="w-full">
            <a-col :span="12" v-for="(item, i) in question.options" :key="i">
              <a-checkbox :value="item.value" :disabled="score == 100">
                <span style="padding: 0 10px 0px 5px; color: #e2f2ff"> {{ Earray[i] }}</span
                ><span style="color: #e2f2ff">{{ item.label }}</span>
              </a-checkbox>
            </a-col>
          </a-row>
        </a-checkbox-group>
      </div>
      <!--      解析-->
      <div class="quesTitle mt-20px layout-left-center c-8b5f2f" v-if="score == 100">解析</div>
      <div class="greenText mt-4px mb-1" v-if="score == 100">
        <a-textarea v-model:value="question.analysis" disabled style="resize: none"></a-textarea>
      </div>
      <a-textarea v-model:value="question.answer" style="resize: none" v-if="question.type == 5" :disabled="score == 100"></a-textarea>
      <div class="greenInput mt-1"></div>
    </div>
  </div>
</template>
<script>
import { defineComponent, toRefs, ref, watch } from 'vue'
import useStudentQues from './js/useStudentQues.js'
import { PlusSquareOutlined, DeleteOutlined, createFromIconfontCN } from '@ant-design/icons-vue'
export default defineComponent({
  name: 'RoomTest',
  // props:{params:Object},
  components: {
    PlusSquareOutlined,
    DeleteOutlined,
    IconFont: createFromIconfontCN({
      scriptUrl: window.iconUrl
    })
  },
  props: {
    params: Object,
    index: Number,
    score: Number
  },
  setup(props, context) {
    const { params, index, score } = toRefs(props)
    const { typeCheckList, question, Earray } = useStudentQues(params, index)
    let arr
    if (question.value.topic.indexOf('________') !== -1) {
      arr = question.value.topic.split('________')
    } else if (question.value.topic.indexOf('(___)') !== -1) {
      arr = question.value.topic.split('(___)')
    }
    let dom = []
    for (let i in arr) {
      dom.push({
        text: arr[i],
        answer: question.value.answer[i]
      })
    }
    if (question.value.analysis == '') {
      question.value.analysis = '暂无解析！'
    }
    //双向绑定填写的答案
    const changeAnswer = (e, k) => {
      question.value.answer[k] = e.target.innerText
    }
    return {
      typeCheckList,
      question,
      Earray,
      dom,
      changeAnswer
    }
  }
})
</script>

<script setup></script>

<style lang="less">
.answerTK {
  padding: 2px 10px;
  border-bottom: 1px solid #354971;
  margin: 0 3px;
  color: #6ebdff;
}
.greenText {
  .ant-input {
    background-color: #1e3552;
    border-color: #5c5145;
    color: #fff;
    height: 114px;
  }
}
.greenInput {
  .ant-select:not(.ant-select-customize-input) .ant-select-selector {
    background-color: #1a344d;
    border-color: #346558;
    color: #ffffff;
  }
}
.quesTitle {
  color: #7b90af;
  font-size: 14px;
}
.studentQues {
  .ant-col-offset-0 {
    margin-left: -8px;
  }
  .ant-input,
  .ant-input-number {
    border-color: #346558;
    background-color: #1a344d;
  }
  .ant-input[disabled] {
    background: #161e29 !important;
    color: #fff;
    cursor: not-allowed;
    border-color: #2f485c !important;
  }
}
.c-53a165 {
  color: #53a165;
}
.c-8b5f2f {
  color: #8b5f2f;
}
</style>
