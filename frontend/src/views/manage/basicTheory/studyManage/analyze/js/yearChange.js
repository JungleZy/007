import { ref } from 'vue'
import moment from 'moment'

export default function yearChange(getChartDataSource2, currentYear1, currentYear2, pointGetChartDataSource) {
  let today = moment(new Date()).format('YYYY-MM-DD')
  today = Number(today.split('-')[0])
  currentYear1.value = today
  currentYear2.value = today
  let selectYearOptions = ref([])
  for (let i = 0; i < 3; i++) {
    let obj = {
      value: today - i,
      label: today - i
    }
    selectYearOptions.value.push(obj)
  }
  let handleYearChange1 = value => {
    getChartDataSource2()
  }
  let handleYearChange2 = value => {
    pointGetChartDataSource()
  }

  return {
    handleYearChange1,
    handleYearChange2,
    selectYearOptions
  }
}
