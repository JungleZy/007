import { message, Modal } from 'ant-design-vue'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
export default function useRoomTest(params, selectedKnowledgeSwfs, index) {
  const Earray = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K']
  const typeCheckList = ref([
    { name: '单选', id: '1' },
    { name: '多选', id: '2' },
    { name: '判断', id: '3' },
    { name: '填空', id: '4' },
    { name: '简答', id: '5' }
  ])
  //,{name:'简答',id:'5'}  //暂时不加入选择
  // const Type=ref(1)
  const question = ref({ type: '1', topic: '', options: [], answer: '', analysis: '' })
  if (params != undefined) {
    question.value = params.value
  }
  const spacing = ref('$_$')
  const spacingLength = ref(0)
  const changeType = e => {
    let value = e.target.value
    if (value == 1) {
      question.value.options = []
      question.value.answer = ''
    } else if (value == 2) {
      question.value.options = []
      question.value.answer = []
    } else if (value == 3) {
      question.value.options = [
        { name: '对', id: '1' },
        { name: '错', id: '2' }
      ]
      question.value.answer = ''
    } else if (value == 4) {
      question.value.options = []
      question.value.answer = []
      setTimeout(() => {
        changeTitle(question.value.topic)
      }, 5)
    } else if (value == 5) {
      question.value.options = ''
      question.value.answer = ''
    }
  }
  const addSpacing = () => {
    let elTextarea = document.getElementById('titleTextarea' + index.value)
    let startPos = elTextarea.selectionStart
    let endPos = elTextarea.selectionEnd
    if (startPos === undefined || endPos === undefined) {
    }
    let text = question.value.topic
    question.value.topic = text.substring(0, startPos) + spacing.value + text.substring(endPos)
    changeTitle(question.value.topic)
    elTextarea.focus()
  }
  const deleteClass = i => {
    question.value.options.splice(i, 1)
    if (question.value.options.length == 0) {
      if (question.value.type == 1) {
        question.value.answer = ''
      } else if (question.value.type == 2) {
        question.value.answer = []
      }
    }
  }
  const changeTitle = e => {
    question.value.topic = e.replace(/\s*/g, '')
    if (question.value.type == 4) {
      if (question.value.topic.match(/\$_\$/g)) {
        spacingLength.value = question.value.topic.match(/\$_\$/g).length
        for (let i = question.value.answer.length; i < spacingLength.value; i++) {
          question.value.answer.push('')
        }
        if (question.value.answer.length > spacingLength.value) {
          let deleteVal = question.value.answer.length - spacingLength.value
          question.value.answer.splice(question.value.answer.length - (deleteVal + 1), deleteVal)
        }
      } else {
        if (question.value.type == 4) {
          question.value.answer = []
        }
      }
    }
  }
  const addSelect = () => {
    let length = question.value.options.length
    if (length > 9) return
    question.value.options.push({
      value: length.toString(),
      label: ''
    })
  }
  return {
    question,
    typeCheckList,
    Earray,
    addSelect,
    changeType,
    spacing,
    spacingLength,
    addSpacing,
    changeTitle,
    deleteClass
  }
}
