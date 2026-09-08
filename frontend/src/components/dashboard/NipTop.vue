<template>
  <div class="w-full dashboard-page-top">
    <div class="w-full h-full overflow-hidden layout-side pl-2 pr-2">
      <div class="h-full top-border relative content--bg"
           style="width: 356px;">
        <div class="h-full w-1/2 layout-center">
          <div style="width: 130px;height: 164px;" :style="{background:'url('+avatarBg+')'}">
            <div class="w-full" style="height: 100px;padding: 0 25px">
              <img v-real-img="fileUrl+userInfo.userImg"
                   :src="avatarDef">
            </div>
            <div class="w-full pt-2"
                 style="height: calc(100% - 100px);color: #7b90af;font-size: 15px">
              <div class="w-full layout-center" style=";font-size: 14px">士字第</div>
              <div class="w-full layout-center">{{ userInfo.wkno }}</div>
            </div>
          </div>
        </div>
        <div class="h-full w-1/2"></div>
        <NipBorderMask/>
      </div>
      <div class="h-full layout-side" style="width: calc(100% - 804px)">
        <div class="h-full w-full top-border content--bg relative">
          <div class="h-full w-full layout-side" style="padding: 0 10px 10px">
            <div id="container" class="w-full h-full handKeyChart"></div>
          </div>
          <NipBorderMask/>
        </div>
      </div>
      <div class="h-full top-border relative content--bg"
           style="width: 424px;">
        <div class="h-full w-full layout-side">
          <div class="h-full overflow-hidden relative"
               @mouseenter="handleMouseStatus(true)"
               @mouseleave="handleMouseStatus(false)"
               :style="{width: '76px',background: 'url('+rightBar+')'}">
            <div class="w-full h-full absolute"
                 style="top: -48px"
                 :class="[dateListFlag===null?'':dateListFlag?'date-list-down':'date-list-up']">
              <div class="w-full h-1/5 layout-left-center pl-2 cursor-pointer-def transition duration-200 ease-in-out"
                   v-for="(d,index) of dateList" @click="handleDataList(d,index)"
                   style="color: #bfe9fa;"
                   :class="['date-item-'+index]">
                {{ d.title }}
              </div>
            </div>
          </div>
          <div class="h-full dataListBox">
            <div class="w-full h-full" style="background: rgba(110,189,255,.04);">
              <div class="itemRow head">
                <div class="itemCol">训练名称</div>
                <div class="itemCol status">状态</div>
              </div>
              <div class="overflow-auto" style="height: calc(100% - 36px);">
                <div v-if="dateList[3].list.length == 0" class="layout-center py-1"
                     style="font-size: 12px;color: #7b90af;display: flex;flex-direction: column;padding-top: 20px;">
                  <img src="../../assets/HJ/train/dataEmpty.png" style="margin-bottom: 10px;">
                  暂无训练！
                </div>
                <div class="itemRow" v-for="(item, index) in dateList[3].list"
                     @click="startTrain(item.path, item.id)">
                  <div class="itemCol nobr">{{item.name}}</div>
                  <div class="itemCol status">
                    <span class="tag finish" v-if="item.status==3">已完成</span>
                    <span class="tag pause" v-else-if="item.status==2">已暂停</span>
                    <span class="tag oper" v-else-if="item.status==1">进行中</span>
                    <span class="tag" v-else>未开始</span>
                  </div>
                </div>
              </div>

            </div>
          </div>
        </div>
        <NipBorderMask/>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "NipTop"
}
</script>
<script setup>
import avatarBg from '../../assets/HJ/main/avatar-bg.png';
import avatarDef from '../../assets/HJ/main/avatar-def.png';
import rightBar from '../../assets/HJ/main/right-bar.png';
import NipBorderMask from "./NipBorderMask.vue";
import {onMounted, ref, watch} from "vue";
import {useRouter} from "vue-router";
import {Line} from '@antv/g2plot';
import {getAllTelegramTrain} from "../../common/api/TelegramApi.js";
import {sum} from "../../common/utils/Utils.js";

const pageWidth = ref(document.body.clientWidth);
const userInfo = ref(JSON.parse(window.localStorage.getItem("userInfo")));
const fileUrl = ref(window.fileUrl);
const dateList = ref([
  {title: dayjs().subtract(3, 'day').format('MM-DD'),list: []},
  {title: dayjs().subtract(2, 'day').format('MM-DD'),list: []},
  {title: dayjs().subtract(1, 'day').format('MM-DD'),list: []},
  {title: dayjs().format('MM-DD'),list: []},
  {title: dayjs().add(1, 'day').format('MM-DD'),list: []},
  {title: dayjs().add(2, 'day').format('MM-DD'),list: []},
  {title: dayjs().add(3, 'day').format('MM-DD'),list: []}
]);
const resDataList = ref({});
const dateListFlag = ref(null);
const router = useRouter();
const emit = defineEmits(['goHandKeyTrain']);
window.onresize = () => {

}
onMounted(() => {
  let _data = {},t_lab = '',time = '',arr = [], path = '';
  getAllTelegramTrain().then(res => {
    if (res.code === 200) {
      router.getRoutes().forEach(r => {
        if (r.name === 'HandKeyTrain') {
          path = r.path;
        }
      });
      for (let item of res.data) {
        time = ((new Date(parseInt(item.createTime)).getMonth()+1)<10?'0':'')+(new Date(parseInt(item.createTime)).getMonth()+1)+'-'+new Date(parseInt(item.createTime)).getDate();
        if (resDataList.value[time]) {
          resDataList.value[time].push({id: item.id, name: item.title, status: item.status, path: path});
        } else {
          resDataList.value[time] = [{id: item.id, name: item.title, status: item.status, path: path}];
        }

        t_lab = '';
        if (item.endTime && item.endTime !== '') {
          t_lab = (new Date(parseInt(item.endTime)).getMonth()+1)+'-'+new Date(parseInt(item.endTime)).getDate();
        }
        if ((t_lab !== '' && (new Date().getTime() - parseInt(item.endTime)) < 30*24*60*60*1000)) {
          if (_data[t_lab]) {
            arr.push(parseInt(item.accuracy));
            _data[t_lab] = {
              name: t_lab,
              min: parseInt(item.accuracy)<_data[t_lab].min?parseInt(item.accuracy):_data[t_lab].min,
              max: parseInt(item.accuracy)>_data[t_lab].max?parseInt(item.accuracy):_data[t_lab].max,
              average: parseInt(sum(arr)/arr.length)
            };
          } else {
            arr = [parseInt(item.accuracy)];
            _data[t_lab] = {
              name: t_lab,
              min: parseInt(item.accuracy),
              max: parseInt(item.accuracy),
              average: parseInt(item.accuracy)
            };
          }

        }
      }
      handleChartInfo(_data);
      handleDataListInfo();
    }
  })
});
const handleChartInfo = (data) => {
  let res = [],getTime = new Date().getTime(),key = '';
  for (let i=0; i<30;i++) {
    key = (new Date(parseInt(getTime - i*24*60*60*1000)).getMonth()+1)+'-'+new Date(parseInt(getTime - i*24*60*60*1000)).getDate();
    res.unshift({"level": '最高', "data": key, "value": data[key]?data[key].max:0});
    res.unshift({"level": '最低', "data": key, "value": data[key]?data[key].min:0});
    res.unshift({"level": '平均', "data": key, "value": data[key]?data[key].average:0});
  }
  const linePlot = new Line('container', {
    data: res,
    xField: 'data',
    yField: 'value',
    seriesField: 'level',
    legend: {
      position: 'top',
      offsetY: 8,
      itemName: {style: {fill: '#7b90af'}}
    },
    smooth: true,
    area: {
      style: {
        fillOpacity: 0.35
      }
    },
    tooltip: {
      crosshairs: {line:{style:{stroke: '#354971'}}},
    },
    xAxis: {
      label: {style: {fill: '#7b90af'}},
      tickLine: {style: {stroke: '#354971'}},
      line: {style: {stroke: '#354971'}}
    },
    yAxis: {
      label: {style: {fill: '#7b90af'}, formatter: (v) => `${v}`},
      grid: {line: {style: {stroke: '#213250', lineDash: [7, 3]}},}
    },
    animation: {
      appear: {
        animation: 'wave-in',
        duration: '300'
      }
    }
  });
  linePlot.render();
};
const handleDataListInfo = () => {
  dateList.value.map(item => {
    if (resDataList.value[item.title]) {
      item.list = resDataList.value[item.title]
    }
    return item;
  });
}
const handleMouseStatus = (e) => {
  if (e) {
    window.addEventListener('mousewheel', mw, false)
  } else {
    window.removeEventListener('mousewheel', mw, false)
  }
}
const mw = (e) => {
  if (e.deltaY < 0) {// 上
    handleDataList(dateList.value[1], 0)
  } else {// 下
    handleDataList(dateList.value[5], 6)
  }
}
const handleDataList = (e, index) => {
  if (dateListFlag.value === null) {
    if (index < 3) {
      dateListFlag.value = false;
      setTimeout(() => {
        const d = dayjs(e.title).subtract(index === 2 ? 3 : 2, 'day');
        dateList.value.unshift({title: d.format('MM-DD'),list: []});
        dateList.value = dateList.value.filter((ele, idx, arr) => arr.length - 1 !== idx);
        dateListFlag.value = null;
        handleDataListInfo();
      }, 250)
    } else if (index > 3) {
      dateListFlag.value = true;
      setTimeout(() => {
        const d = dayjs(e.title).add(index === 4 ? 3 : 2, 'day');
        dateList.value.push({title: d.format('MM-DD'),list: []});
        dateList.value.splice(0, 1);
        dateListFlag.value = null;
        handleDataListInfo();
      }, 250)
    }
  }
}
const startTrain = (path, id) => {
  emit("goHandKeyTrain", {path: path, id: id});
};
</script>

<style scoped>
.dashboard-page-top {
  height: 250px;
}

.dashboard-page-top .top-border {
  border-top: 1px solid rgb(23, 38, 66);
  border-bottom: 1px solid rgb(23, 38, 66);
}

.content--bg {
  background-color: rgba(2,12,27,.4);
}
.date-list-down {
  animation: dateListDown 0.25s linear;
}

.date-list-up {
  animation: dateListUp 0.25s linear;
}

.date-item-0 {
  font-size: 11px;
}

.date-item-1 {
  font-size: 14px;
}

.date-item-2 {
  font-size: 17px;
}

.date-item-3 {
  font-size: 20px;
}

.date-item-4 {
  font-size: 17px;
}

.date-item-5 {
  font-size: 14px;
}

.date-item-6 {
  font-size: 11px;
}

.dataListBox {
  width: calc(100% - 76px);
  padding: 12px 8px 12px 12px;
  position: relative;
}
.itemRow {
  height: 36px;
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #7b90af;
  border-bottom: 1px solid #041025;
  cursor: pointer;
}
.itemRow.head {
  font-size: 14px;
  color: #e2f2ff;
  background: rgba(110,189,255,.12);
  cursor: default;
}
.itemRow:nth-of-type(2n) {
  background: rgba(110,189,255,.08);
}
.itemRow:not(.head):hover {
  background: #112541;
}
.itemRow .itemCol {
  width: 100%;
  padding: 6px 12px;
  line-height: 24px;
}
.itemRow .itemCol.status {
  width: 80px;
  flex-shrink: 0;
  text-align: center;
  padding: 6px 8px;
}
.itemRow .itemCol .tag {
  font-size: 12px;
  padding: 0 6px;
  border-radius: 2px;
  border: 1px solid #6ebdff;
  color: #6ebdff;
}
.itemRow .itemCol .tag.finish {
  border-color: #354964;
  color: #354964;
}
.itemRow .itemCol .tag.pause {
  border-color: #f8cf6f;
  color: #f8cf6f;
}
.itemRow .itemCol .tag.oper {
  border-color: #78e775;
  color: #78e775;
}

@-webkit-keyframes dateListDown {
  0% {
    top: -48px;
  }
  100% {
    top: -96px;
  }
}

@-webkit-keyframes dateListUp {
  0% {
    top: -48px;
  }
  100% {
    top: 0px;
  }
}

</style>