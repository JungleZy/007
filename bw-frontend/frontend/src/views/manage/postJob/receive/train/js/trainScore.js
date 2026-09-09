import {ref, onMounted, onUnmounted, watch,nextTick} from "vue";
import {useRoute} from "vue-router"
import {message} from "ant-design-vue";
import {partTimeFormatInfo} from "../../../../../../common/utils/Utils.js";
import {getReceiveTrainDetails,apiPostTickerTapeTrainFindPage,findHeader} from "../../../../../../common/api/ReceiveApi.js";

export default function telegramList() {
  const loading = ref(true);
  const route = useRoute();
  const scoreData = ref({});
  const currTelegram = ref(1);
  const thumbImageRef = ref(null);
  const content = ref({});
  const thumbNumber = ref(0);
  const header = ref('')

  onMounted(() => {
    nextTick(() => {
      thumbImageRef.value.addEventListener('mousewheel', e => {
        if (e.deltaY > 0) {
          thumbImageRef.value.scrollLeft += 100;
        } else {
          thumbImageRef.value.scrollLeft -= 100;
        }
      });
    });

    if (route.query.id && route.query.id !== '') {
      findHeader(route.query.id).then(res=>{
        if(res.data!==null){
          const arr = res.data.content.split(' ')
          arr[3] = arr[3].replaceAll("T",'0')
          arr[4] = arr[4].replaceAll("T",'0')
          arr.forEach(item=>{
            header.value +=" "+item
          })
        }
      })
      getReceiveTrainDetails({
        id: route.query.id
      }).then(res => {
        loading.value = false;
        if (res.code === 200) {
          res.data.validTime = partTimeFormatInfo(Number(res.data.validTime)*1000, 'number');
          res.data.validTime = res.data.validTime.replace(/：/g, ':');
          res.data['totalPage'] = Math.ceil(res.data.totalNumber / 100);
          scoreData.value = res.data;
          if (res.data.images.length > res.data['totalPage']) {
            thumbNumber.value = res.data.images.length
          } else {
            thumbNumber.value = res.data['totalPage']
          }
          getPageList();
        } else {
          message.error(res.message);
        }
      })
    }
  });

  /**
   * 获取当前页的报文和抄收结果
   */
  const getPageList = ()=>{
    apiPostTickerTapeTrainFindPage({
      pageNumber: currTelegram.value,
      trainId:route.query.id
    }).then((res)=>{
      if(res.code!=200)return
      let arr=[]
      for(let i=0;i<100;i++){
        arr.push({
          key:res.data.messageBody[i]?.key ?? '',
          value:res.data.value[i] ?? ''
        })
      }
      content.value[currTelegram.value+''] = arr
    })
  }

  /**
   * 切换电报纸
   * @param num
   */
  const switchTelegram = (num) => {
    currTelegram.value = num
    if (!content.value[currTelegram.value+'']) {
      getPageList()
    }
  };

  return {
    scoreData,loading,currTelegram,thumbImageRef,switchTelegram,content,thumbNumber,header
  }
}







