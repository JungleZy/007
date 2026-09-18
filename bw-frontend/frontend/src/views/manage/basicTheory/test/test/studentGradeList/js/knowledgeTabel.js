import {ref} from 'vue'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import {listPageSelfTesting} from '../../../../../../../common/api/TestApi'
export default function knowledgeTabel() {
  const listData = ref([])
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  const testPaper = () => {
    listPageSelfTesting({}).then(res => {
      if (res.code === 200) {
        listData.value = res.data.sort((a, b) => moment(b.start_time).valueOf() - moment(a.start_time).valueOf())
      }
    })
  }
  return {
    listData,
    userRole,
    testPaper
  }
}
