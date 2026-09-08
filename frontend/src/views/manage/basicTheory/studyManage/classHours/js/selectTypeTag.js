import {reactive, ref} from 'vue'

let selectTypeTagsFunc = (currentTypeTag,getChartDataSource) => {
  let typeTags = ref([
    {
      name: "基础理论",
      isActive: true
    },

  ])
  const interfaceStyle = window.interfaceStyle
  if(interfaceStyle==='HJ'){
    typeTags.value.push({
          name: "值掌装备",
          isActive: false
        },
        {
          name: "值勤业务",
          isActive: false
        })
  }


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

