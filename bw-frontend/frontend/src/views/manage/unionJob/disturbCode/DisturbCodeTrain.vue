<template>
  <div class="w-full h-full content-mask-bg">
    <div v-if="!WSConnect || recoveryError || submittingResult" role="status" style="position: fixed; bottom: 16px; left: 50%; transform: translateX(-50%); z-index: 1100; padding: 10px 18px; color: #fff; background: #26394b; border-radius: 4px">
      <span v-if="!WSConnect">连接已断开，正在重连。</span>
      <span v-if="submittingResult">正在提交结果，请勿重复操作。</span>
      <span v-if="recoveryError">{{ recoveryError }}</span>
      <a-button v-if="recoveryError" size="small" @click="recoverResults">重新读取</a-button>
    </div>
    <template v-if="trainData">
      <div class="w-full h-full trainBoxs" v-if="trainData.creatUser || (trainData.status == 2 && userConfirmResult)">
        <TrainLeft @startTest="openTrainInfo" @endTest="closeTrainInfo" :trainData="trainData">
          <template v-slot:top>
            <div class="desc">
              {{ trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '训练正在进行，当前总耗时' : '本次练习已结束,总用时' }}
            </div>
            <count-down class="width-100-per layout-center" color="#70c9ff" ref="trainTimeRef" style="height: 55px" />
          </template>
          <template v-slot:bottom>
            <div class="userListBox">
              <div class="title">参训人员列表</div>
              <div class="userList overflow-auto">
                <template v-for="(user, u) in joinTrainUser" :key="u">
                  <div class="user" v-if="trainData.creatUser || user.id == userInfo.id">
                    <div class="layout-left-center">
                      <img :src="fileUrl + user.userImg" class="avaImg" />
                      <span class="nobr ml-1">{{ user.userName }}</span>
                    </div>
                    <div class="layout-left-center">
                      <a-tooltip title="查看抄收结果" v-if="user.userStatus == 1 && trainData.creatUser">
                        <FileTextOutlined class="ico" @click="seeTrainResult(user)" />
                      </a-tooltip>
                      <div class="tag" v-if="user.channel">
                        {{ user.channel == '1' ? '第一路' : user.channel == '2' ? '第二路' :user.channel == '3' ? '第三路':'' }}
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </TrainLeft>
        <div class="trainCenter w-full overflow-auto">
          <template v-if="trainData.creatUser">
            <div class="patTelegraphBox">
              <div class="telegrapHead">
                <div style="width: 172px; flex-shrink: 0">页码：【{{ trainData.currPag }}/{{ trainData.pag }}】</div>
                <strong style="font-size: 20px">{{ trainData.name }}</strong>
                <div class="page">
                  <div :class="{ pag: true, disabled: trainData.currPag == 1 }" @click="pageTurn('prev')">上一页</div>
                  <div :class="{ pag: true, disabled: trainData.currPag == trainData.pag }" @click="pageTurn('next')">下一页</div>
                </div>
              </div>
              <div class="patTelegraph" style="height: calc(100% - 40px)">
                <div class="telegraph">
                  <div class="rowHead">
                    <div class="key">1</div>
                    <div class="key">2</div>
                    <div class="key">3</div>
                    <div class="key">4</div>
                    <div class="key">5</div>
                    <div class="key">6</div>
                    <div class="key">7</div>
                    <div class="key">8</div>
                    <div class="key">9</div>
                    <div class="key">10</div>
                  </div>
                  <div class="keyBox">
                    <template v-for="(item, index) in trainData.content" :key="index">
                      <div class="key">{{ item.key }}</div>
                    </template>
                    <template v-if="trainData.content && trainData.content.length % 100 > 0">
                      <div class="key" v-for="(key, index) in 100 - (trainData.content.length % 100)" :key="index"></div>
                    </template>
                  </div>
                </div>
                <div class="serial">
                  <div class="ser head"></div>
                  <div class="ser">10</div>
                  <div class="ser">20</div>
                  <div class="ser">30</div>
                  <div class="ser">40</div>
                  <div class="ser">50</div>
                  <div class="ser">60</div>
                  <div class="ser">70</div>
                  <div class="ser">80</div>
                  <div class="ser">90</div>
                  <div class="ser">100</div>
                </div>
              </div>
            </div>
            <div class="disposeBox">
              <div class="disposeItem">
                <div class="groupBoxs" style="height: 154px">
                  <div class="groupTitle">主信号</div>
                  <div class="rowItem zhu">
                    <div class="lab"></div>
                    <div class="item">速度</div>
                    <div class="item" :style="{ width: 'calc((100% - ' + (trainData.status < 2 ? 108 : 60) + 'px) * 0.4)' }">音量</div>
                    <div class="item">音调</div>
                    <div class="oper" v-if="trainData.status < 2">操作</div>
                  </div>
                  <div class="listBox">
                    <template v-for="(zhu, z) in trainData.mainSignal" :key="z">
                      <div :class="{ 'rowItem zhu': true, animateBg: zhu.status == 1 && trainData.status < 2 }" v-if="zhu.checked">
                        <div class="lab">{{ zhu.type == 1 ? '第一路' : zhu.type == 2 ? '第二路' : zhu.type == 3 ? '第三路' : '' }}</div>
                        <div class="item">
                          <a-input-number v-model:value="zhu.rate" :min="40" :step="2" size="small" placeholder="码率" @change="changeMainSignalItem(zhu, 'rate')" :disabled="trainData.status == 2" style="width: 100%; max-width: 120px"></a-input-number>
                        </div>
                        <div class="item" :style="{ width: 'calc((100% - ' + (trainData.status < 2 ? 108 : 60) + 'px) * 0.4)' }">
                          <a-slider v-model:value="zhu.volume" :min="0" :max="100" @change="changeMainSignalItem(zhu, 'volume')" :disabled="trainData.status == 2" style="margin: 8px 6px 4px"></a-slider>
                        </div>
                        <div class="item">
                          <a-input-number v-model:value="zhu.fre" :min="100" :step="100" size="small" placeholder="音调" @change="changeMainSignalItem(zhu, 'fre')" :disabled="trainData.status == 2" style="width: 100%; max-width: 120px"></a-input-number>
                        </div>
                        <div class="oper" v-if="trainData.status < 2">
                          <a-tooltip title="开始" v-if="zhu.status == 0">
                            <PlayCircleFilled class="ico" @click="changeMainSignalStatus(zhu, 1)" />
                          </a-tooltip>
                          <a-tooltip title="暂停" v-if="zhu.status == 1">
                            <PauseCircleFilled class="ico" @click="changeMainSignalStatus(zhu, 2)" />
                          </a-tooltip>
                          <a-tooltip title="继续" v-if="zhu.status == 2">
                            <PlayCircleFilled class="ico" @click="changeMainSignalStatus(zhu, 1)" />
                          </a-tooltip>
                          <a-tooltip title="结束" v-if="zhu.status == 1 || zhu.status == 2">
                            <StopFilled class="ico" style="color: red" @click="changeMainSignalStatus(zhu, 3)" />
                          </a-tooltip>
                        </div>
                      </div>
                    </template>
                  </div>
                </div>
                <div class="groupBoxs">
                  <div class="groupTitle">噪音干扰</div>
                  <div class="listBox" style="height: 100%">
                    <div class="disturbBox">
                      <div class="disturbItem" v-for="item of disturbList" :key="item">
                        <a-checkbox v-model:checked="item.checked" @change="changeDisturbItem(item, '')" :disabled="trainData.status == 2" style="flex-shrink: 0; width: 84px">{{ item.name }}</a-checkbox>
                        <a-slider v-model:value="item.volume" :min="0" :max="100" @change="changeDisturbItem(item, 'volume')" :disabled="trainData.status == 2" style="margin: 0 6px; width: 100%; max-width: 160px"></a-slider>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="disposeItem">
                <div class="groupBoxs" style="height: calc(100% - 16px)">
                  <div class="groupTitle">电码干扰</div>
                  <div class="rowItem code">
                    <div class="lab"></div>
                    <div class="item">速度</div>
                    <div class="item" :style="{ width: 'calc((100% - ' + (trainData.status < 2 ? 126 : 96) + 'px) * 0.4)' }">强度</div>
                    <div class="item">音调</div>
                    <div class="oper" v-if="trainData.status < 2">操作</div>
                  </div>
                  <div class="listBox code">
                    <div class="rowItem code" v-for="(cod, z) in trainData.interferenceSignal" :key="z">
                      <div class="lab">
                        {{ cod.type == 1 ? '第一路(数码)' : cod.type == 2 ? '第二路(数码)' : cod.type == 3 ? '第三路(数码)' : cod.type == 4 ? '第四路(长码)' : cod.type == 5 ? '第五路(字码)' : cod.type == 6 ? '第六路(混合码)' : '' }}
                      </div>
                      <div class="item">
                        <a-input-number v-model:value="cod.rate" :min="40" :step="2" size="small" placeholder="码率" @change="changeCodeDisturbItem(cod, 'rate')" :disabled="trainData.status == 2" style="width: 100%; max-width: 120px"></a-input-number>
                      </div>
                      <div class="item" :style="{ width: 'calc((100% - ' + (trainData.status < 2 ? 126 : 96) + 'px) * 0.4)' }">
                        <a-slider v-model:value="cod.volume" :min="0" :max="100" @change="changeCodeDisturbItem(cod, 'volume')" :disabled="trainData.status == 2" style="margin: 8px 6px 4px"></a-slider>
                      </div>
                      <div class="item">
                        <a-input-number v-model:value="cod.fre" :min="40" :step="100" size="small" placeholder="音调" @change="changeCodeDisturbItem(cod, 'fre')" :disabled="trainData.status == 2" style="width: 100%; max-width: 120px"></a-input-number>
                      </div>
                      <div class="oper" v-if="trainData.status < 2">
                        <a-tooltip title="开始" v-if="cod.status == 0">
                          <PlayCircleFilled class="ico" @click="changeCodeDisturbStatus(cod, 1)" />
                        </a-tooltip>
                        <a-tooltip title="停止" v-if="cod.status == 1">
                          <StopFilled class="ico" style="color: red" @click="changeCodeDisturbStatus(cod, 0)" />
                        </a-tooltip>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="disposeItemTwo" v-if="ban"></div>
            </div>
          </template>
          <template v-else>
            <div class="layout-center" style="font-size: 22px; font-weight: bolder">{{ trainData.name }}</div>
            <TrainResult :result="allBaoWen[trainData.currPag + '']" :curr="trainData.currPag" :all="trainData.pag" @switchPage="pageTurn" style="height: calc(100% - 40px)"></TrainResult>
          </template>
        </div>
      </div>
      <div class="w-full h-full trainBoxs student layout-center" v-else style="flex-direction: column">
        <div class="tipHead" v-if="examinerStatus == 'offline'" style="top: 0">
          <strong style="color: red; font-size: 20px">考官暂时离开</strong>
        </div>
        <div class="tipHead">
          <strong class="layout-center" style="font-size: 26px">{{ trainData.name }}</strong>
        </div>
        <div class="tipHead" v-if="trainData.status == 1 && student.road > 0" style="top: 116px">
          <div class="tit">
            播报路报：<strong>{{ student.road == '1' ? '第一路报' : student.road == '2' ? '第二路报' : student.road == '3' ? '第三路报' : '' }}</strong>
          </div>
          <div class="descs" v-if="student.msg">
            <div class="dis">
              码率：<strong>{{ student.msg.rate }}</strong>
              <div class="unit">码/分</div>
            </div>
            <div class="dis">
              音量：<strong>{{ student.msg.volume }}</strong>
              <div class="unit">%</div>
            </div>
            <div class="dis">
              音调：<strong>{{ student.msg.fre }}</strong>
              <div class="unit">Hz</div>
            </div>
            <div class="dis" v-if="student.msg.status == 2">状态：<strong style="color: red; font-size: 20px">已暂停</strong></div>
          </div>
        </div>
        <div class="tipCard" v-if="trainData.status == 0 || student.road == '0'">
          <div class="title" v-if="student.road == '0'">请选择你要抄收的第几路报？</div>
          <div class="title" v-else>
            当前选择路报：<strong>【{{ student.road == '1' ? '第一路报' : student.road == '2' ? '第二路报' : '第三路报' }}】</strong>
          </div>
          <div class="riadBox" v-if="WSConnect">
            <template v-for="(item, i) in trainData.mainSignal" :key="i">
              <div class="roadItem" @click="selectRoadInfo(item)" v-if="item.checked">
                <div :class="{ roadBtn: true, on: student.road == item.type }">
                  {{ item.type == 1 ? '第一路报' : item.type == 2 ? '第二路报' : item.type == 3 ? '第三路报' : '第三路报' }}
                </div>
                <div :class="{ desc: true, on: student.road == item.type }">
                  <div class="num">{{ item.rate }}</div>
                  码/分
                </div>
              </div>
            </template>
          </div>
        </div>
        <div class="tipCard" v-if="trainData.status == 2 && !fillInResult">
          <div class="title" style="padding: 60px 40px 20px">训练已结束</div>
          <div class="descs">
            <div class="dis">
              用时：<strong>{{ partTimeFormatInfo(parseInt(trainData.totalTime * 1000), 'number') }}</strong>
            </div>
          </div>
          <div class="roadItem" style="padding-top: 60px">
            <div class="roadBtn" @click="fillInResult = true">填报抄收结果</div>
          </div>
        </div>
        <div class="tipText" v-if="!fillInResult">
          <div class="textRow">
            <img :src="text1" alt="" />
            <img :src="text2" alt="" />
            <img :src="text3" alt="" />
            <img :src="text4" alt="" />
          </div>
        </div>
        <FillInResult v-else :submitting="submittingResult" @result="fillInTrainResult"></FillInResult>
        <div class="playTipsBox" v-if="playTips.visible">
          <div class="tipCard" style="top: calc(50% - 200px)">
            <div class="title" style="padding: 60px 40px 20px">欢迎回来【{{ userInfo.userName }}】</div>
            <div class="descs">
              <div class="dis" v-if="storage.totalTime > 0">
                您已抄收：<strong>{{ partTimeFormatInfo(parseInt(storage.totalTime * 1000), 'chinese') }}</strong>
              </div>
            </div>
            <div class="roadItem" style="padding-top: 60px">
              <div class="roadBtn" @click="againPlayCode(0, 1)">重新抄收</div>
              <div class="roadBtn ml-5" @click="againPlayCode(storage.playCodeIndex, storage.playPage)" v-if="storage.playPage > 1 || storage.playCodeIndex > 0">继续抄收</div>
            </div>
          </div>
        </div>
      </div>
    </template>
    <div class="loading" v-else>
      <a-spin size="large" tip="正在努力加载..." />
    </div>

    <a-modal :destroyOnClose="true" :width="1000" class="init_modal_style footer-border-none" v-model:visible="trainResult.visible" @cancel="trainResult.visible = false">
      <template #title>
        <strong v-if="trainResult.user">查看【{{ trainResult.user.userName }}】训练结果</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center"></div>
      </template>
      <div class="resBox overflow-auto relative" v-if="trainData" style="max-height: calc(100vh - 280px)">
        <TrainResult :result="trainResult.res[trainResult.curr + '']" :curr="trainResult.curr" :all="trainResult.existPage" @switchPage="modelPageTurn"></TrainResult>
      </div>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'DisturbCodeTrain'
}
</script>
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { PauseCircleFilled, PlayCircleFilled, StopFilled, CheckCircleFilled, FileTextOutlined } from '@ant-design/icons-vue'
import trainJS from './js/train.js'
import { partTimeFormatInfo } from '../../../../common/utils/Utils'
import FillInResult from './FillInResult.vue'
import TrainResult from './TrainResult.vue'
import text1 from '../../../../assets/HJ/union/text-1.png'
import text2 from '../../../../assets/HJ/union/text-2.png'
import text3 from '../../../../assets/HJ/union/text-3.png'
import text4 from '../../../../assets/HJ/union/text-4.png'

const fileUrl = ref(window.fileUrl)
const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
const {
  trainTimeRef,
  WSConnect,
  recoveryError,
  submittingResult,
  recoverResults,
  trainData,
  disturbList,
  student,
  joinTrainUser,
  userConfirmResult,
  fillInResult,
  trainResult,
  playTips,
  storage,
  examinerStatus,
  ban,
  cacheDispose,
  openTrainInfo,
  closeTrainInfo,
  pageTurn,
  selectRoadInfo,
  changeMainSignalStatus,
  changeCodeDisturbStatus,
  changeMainSignalItem,
  changeDisturbItem,
  changeCodeDisturbItem,
  fillInTrainResult,
  seeTrainResult,
  againPlayCode,
  allBaoWen,
  modelPageTurn
} = trainJS()
</script>

<style lang="less" scoped>
/deep/.ant-checkbox-disabled + span {
  color: #909db0;
}
@import "./css/DisturbCodeTrain";
</style>
