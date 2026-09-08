import {reactive, ref} from 'vue'

let selectTimeTagsFunc = (currentTimeTag,getChartDataSource) => {
  let timeTags = ref([
    {
      name: "年",
      isActive: false
    },
    {
      name: "月",
      isActive: true
    }
  ])



  let selectTimeTag = item => {
    currentTimeTag.value = item.name
    for (let qq of timeTags.value) {
      qq.isActive = qq.name === item.name
    }
    getChartDataSource()
  }

  return {
    timeTags,
    selectTimeTag
  }
}

export {selectTimeTagsFunc}

