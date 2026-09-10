import {reactive, ref} from 'vue'

let selectTypeTagsFunc = (currentTypeTag,getChartDataSource) => {
  let typeTags = ref([
    {
      name: "成绩分布",
      isActive: true
    },
    {
      name: "训练次数",
      isActive: false
    },
    {
      name: "分数统计",
      isActive: false
    }
  ])



  let selectTag = item => {
    currentTypeTag.value = item.name
    for (let qq of typeTags.value) {
      qq.isActive = qq.name === item.name
    }
    getChartDataSource()
  }

  return {
    typeTags,
    selectTag
  }
}

export {selectTypeTagsFunc}

