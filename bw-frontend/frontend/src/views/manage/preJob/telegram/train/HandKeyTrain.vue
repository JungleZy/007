<template>
  <div class="w-full h-full content-mask-bg overflow-hidden relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..."/>
    </div>
    <div class="w-full h-full trainBoxs">
      <div class="trainLeft">
        <div class="statisticsBox statisticalBox">
          <div class="lineBox">
            <div class="box"><img :src="countLab" alt="" /> <span>总数量</span></div>
            <div class="box">{{ trainData.totalNumber }}个</div>
          </div>
          <div class="lineBox">
            <div class="box"><img :src="errorLab" alt="" /> <span>错误数</span></div>
            <div class="box">{{ trainData.errorNumber }}个</div>
          </div>
          <div class="lineBox">
            <div class="box"><img :src="successLab" alt="" /> <span>正确率</span></div>
            <div class="box">{{ parseFloat(trainData.accuracy) }}%</div>
          </div>
          <div class="lineBox">
            <div class="box"><img :src="speedLab" alt="" /> <span>速度</span></div>
            <div class="box">{{ trainData.speed }}{{ wpmTOmm ? '码/分' : 'WPM' }}</div>
          </div>
          <div class="linebtns">
            <div @click="goBack" class="layout-center btn"><IconFont type="icon-rollback" style="margin-right: 5px"></IconFont> 退出</div>
          </div>
        </div>
        <div class="h-full overflow-auto deployBoxs" v-if="trainData.status<3"
             style="padding: 0 12px;height: calc(100% - 280px);margin-top: 8px">
          <div class="deployGroup">
            <div class="title">
              <span class="leftLine"></span>
              <span class="dot"></span>
              <span class="text">字码风格</span>
              <span class="dot"></span>
              <span class="rightLine"></span>
            </div>
            <div class="cont codeCont">
              <div v-for="(item, i) in codeTypeArr"
                   :class="{codeStyle: true, on: item.type===codeType}"
                   @click="codeType=item.type">
                <img :src="fileUrl+item.type+'/A.png'" alt="">
                <div class="name" :style="{fontSize: (fs * 1 + 12) + 'px'}">{{ item.name }}</div>
              </div>
            </div>
          </div>
          <div class="deployGroup">
            <div class="title">
              <span class="leftLine"></span>
              <span class="dot"></span>
              <span class="text">音量设置</span>
              <span class="dot"></span>
              <span class="rightLine"></span>
            </div>
            <div class="cont">
              <div class="volumeBox">
                <div class="title">
                  <img v-if="audioVolume===0" :src="volumeNone" alt="">
                  <img v-else-if="audioVolume<50" :src="volumeMini" alt="">
                  <img v-else-if="audioVolume===100" :src="volumeBig" alt="">
                  <img v-else :src="volumeSmall" alt="">
                  <span>{{ audioVolume }}%</span>
                </div>
                <div class="volumeSlider">
                  <a-slider v-model:value="audioVolume" :min="0" :max="100" :step="5"
                            :tooltipVisible="false"></a-slider>
                </div>
              </div>
            </div>
          </div>
          <div class="deployGroup">
            <div class="title">
              <span class="leftLine"></span>
              <span class="dot"></span>
              <span class="text">拍发配置</span>
              <span class="dot"></span>
              <span class="rightLine"></span>
            </div>
            <div class="cont">
              <div class="depRow">
                <div class="lab" :style="{width: (fs>0?46:36)+'px'}"></div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">最小值</div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">最大值</div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">比例</div>
              </div>
              <div class="depRow">
                <div class="lab fs_dispose_1" :style="{width: (fs>0?46:36)+'px'}">点区间</div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.dot.min" disabled
                                  :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.dot.max" :min="patStandard.dot.min+1"
                                  @change="handlePatDeployData" :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number :value="1" disabled :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
              </div>
              <div class="depRow">
                <div class="lab fs_dispose_1" :style="{width: (fs>0?46:36)+'px'}">划区间</div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.line.min" disabled :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.line.max" disabled :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="proportion.line" :min="2" @change="handlePatDeployData"
                                  :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
              </div>
              <div class="depRow">
                <div class="lab fs_dispose_1" :style="{width: (fs>0?46:36)+'px'}">词间隔</div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.interval.min" disabled
                                  :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.interval.max" disabled
                                  :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="proportion.interval" :min="2" @change="handlePatDeployData"
                                  :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
              </div>
              <div class="depRow" v-if="trainData.type > 10">
                <div class="lab fs_dispose_1" :style="{width: (fs>0?46:36)+'px'}">组间隔</div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.gap.min" disabled :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="patStandard.gap.max" disabled :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
                <div class="item fs_dispose_1" :style="{width: (fs>0?60:64)+'px'}">
                  <a-input-number v-model:value="proportion.gap" :min="3" @change="handlePatDeployData"
                                  :style="{width: (fs>0?60:64)+'px'}"></a-input-number>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="trainCenter" style="width: calc(100% - 580px);">
        <div class="w-full h-full overflow-auto handKeyBoardBoxs">
          <div class="flex" ref="handKeyBoardBoxRef" v-show="!isTrainFocusMode">
            <template v-if="trainData.type<10">
              <div v-for="(bw, w) in currBaoWen.baoWenList"
                   :class="{keyItem: true,
                          active: currBaoWenIndex===w&&currBaoDiIndex===editBaoDiIndex&&trainData.status===1,
                          success: bw.val.length>0&&bw.val.join('')===morseCode[numberCodeType][bw.key].value,
                          error: !(currBaoWenIndex===w&&currBaoDiIndex===editBaoDiIndex)&&bw.val.length>0&&bw.val.join('')!=morseCode[numberCodeType][bw.key].value}"
                   :style="{width: handKeyWidth+'px'}"><!--@click="editHandKeyInfo(bw,w)"-->
                <div class="text">
                  <img :src="fileUrl+codeType+'/'+bw.key+'.png'" alt="">
                </div>
                <div class="value" :title="bw.time.map(t => ((t[1]-t[0])+'ms')).join(',')" style="cursor: default">
                  <div v-if="bw.val.length > 0" class="vals">
                    <span v-for="(val, v) in bw.val" class="val" :data="val"></span>
                  </div>
                  <div class="time" v-if="trainData.status===3 && bw.time.length > 0">
                    {{ sum(bw.time.map(t => (t[1] - t[0]))) }} ms
                  </div>
                </div>
              </div>
            </template>
            <template v-else><!--930px-->
              <div v-for="(bw, w) in currBaoWen.baoWenList"
                   :class="{plraseItem: true,
                            active: currBaoWenIndex===w&&currBaoDiIndex===editBaoDiIndex&&trainData.status===1,
                            success: bw.val.every((v,x) => v.join('')===morseCode['mix'][bw.key[x]].value),
                            error: !bw.val.every((v,x) => v.join('')===morseCode['mix'][bw.key[x]].value)&&!bw.val.every((v,x) => v.length===0)&&currBaoWenIndex!=w}">
                <div class="imgBox">
                  <template v-for="(c,k) in bw.key">
                    <img :src="fileUrl+codeType+'/'+c+'.png'" class="img">
                  </template>
                </div>
                <div class="value">
                  <div class="vals" v-for="(val, v) in bw.val">
                    <span v-for="(v, c) in val" class="val" :data="v"></span>
                  </div>
                </div>
              </div>
            </template>
          </div>
          <div class="w-full h-full focusModeBox" v-show="isTrainFocusMode">
            <template v-if="trainData.type<10">
              <div class="w-full layout-center" style="padding: 35px 0 8px;">
                <div class="bigKey">
                  <div class="tag" v-if="trainData.type === 1">{{trainData.shortCode?'短码':'长码'}}</div>
                  <div class="img">
                    <img v-if="currBaoWen.baoWenList[currBaoWenIndex]"
                         :src="fileUrl+'big/'+codeType+'/'+currBaoWen.baoWenList[currBaoWenIndex].key+'.png'">
                    <img v-else-if="currBaoWen.baoWenList[currBaoWen.baoWenList.length-1]"
                         :src="fileUrl+'big/'+codeType+'/'+currBaoWen.baoWenList[currBaoWen.baoWenList.length-1].key+'.png'">
                  </div>
                  <div class="value">
                    <template
                      v-if="currBaoWen.baoWenList[currBaoWenIndex] && currBaoWen.baoWenList[currBaoWenIndex].val.length > 0">
                      <span v-for="(v, j) in currBaoWen.baoWenList[currBaoWenIndex].val" class="val" :data="v"
                            :style="{background: 'url('+fileUrl+'big/'+codeType+'/'+(v===1?'line':'dot')+'.png) no-repeat center'}"></span>
                    </template>
                    <template v-else-if="currBaoWen.baoWenList[currBaoWen.baoWenList.length-1]">
                      <span v-for="(v, j) in currBaoWen.baoWenList[currBaoWen.baoWenList.length-1].val" class="val"
                            :data="v"
                            :style="{background: 'url('+fileUrl+'big/'+codeType+'/'+(v===1?'line':'dot')+'.png) no-repeat center'}"></span>
                    </template>
                  </div>
                </div>
              </div>
              <div class="w-full thumb overflow-auto" ref="focusTrainThumbRef">
                <div v-for="(bw, w) in currBaoWen.baoWenList"
                     :class="{item: true,
                            active: currBaoWenIndex===w&&currBaoDiIndex===editBaoDiIndex&&trainData.status===1,
                            success: bw.val.length>0&&bw.val.join('')===morseCode[numberCodeType][bw.key].value,
                            error: !(currBaoWenIndex===w&&currBaoDiIndex===editBaoDiIndex)&&bw.val.length>0&&bw.val.join('')!=morseCode[numberCodeType][bw.key].value}">
                  <img :src="fileUrl+codeType+'/'+bw.key+'.png'" alt="">
                  <div class="vals">
                    <div class="v" v-for="(va,v) in bw.val" :data="va"></div>
                  </div>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="w-full layout-center" style="padding: 35px 0 8px;">
                <div class="bigKey plraseBigKey">
                  <div class="img">
                    <template v-if="currBaoWen.baoWenList[currBaoWenIndex]">
                      <img v-for="(c,k) in currBaoWen.baoWenList[currBaoWenIndex].key"
                           :src="fileUrl+'big/'+codeType+'/'+c+'.png'">
                    </template>
                    <template v-else-if="currBaoWen.baoWenList[currBaoWen.baoWenList.length-1]">
                      <img v-for="(c,k) in currBaoWen.baoWenList[currBaoWen.baoWenList.length-1].key"
                           :src="fileUrl+'big/'+codeType+'/'+c+'.png'">
                    </template>
                  </div>
                  <div class="value">
                    <template v-if="currBaoWen.baoWenList[currBaoWenIndex]">
                      <div class="vals" v-for="(vs, i) in currBaoWen.baoWenList[currBaoWenIndex].val">
                        <span v-for="(v, j) in vs" class="val" :data="v"
                              :style="{background: 'url('+fileUrl+'big/'+codeType+'/'+(v===1?'line':'dot')+'.png) no-repeat center'}"></span>
                      </div>
                    </template>
                    <template v-else-if="currBaoWen.baoWenList[currBaoWen.baoWenList.length-1]">
                      <div class="vals" v-for="(vs, i) in currBaoWen.baoWenList[currBaoWen.baoWenList.length-1].val">
                        <span v-for="(v, j) in vs" class="val" :data="v"
                              :style="{background: 'url('+fileUrl+'big/'+codeType+'/'+(v===1?'line':'dot')+'.png) no-repeat center'}"></span>
                      </div>
                    </template>
                  </div>
                </div>
              </div>
              <div class="w-full thumb overflow-auto" ref="focusTrainThumbRef">
                <div class="items" v-for="(bw, w) in currBaoWen.baoWenList">
                  <div v-for="(code, k) in bw.key"
                       :data="bw.val[k]"
                       :class="{item: true,
                            active: currBaoWenIndex===w&&currBaoDiIndex===editBaoDiIndex&&trainData.status===1,
                            success: bw.val[k]&&bw.val[k].join('')===morseCode['mix'][code].value,
                            error: w<currBaoWenIndex&&bw.val[k]&&bw.val[k].join('')!=morseCode['mix'][code].value}">
                    <img :src="fileUrl+codeType+'/'+code+'.png'" alt="">
                    <div class="vals">
                      <div class="v" v-for="(va,v) in bw.val[k]" :data="va"></div>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
        <div class="flex trainCenterBot">
          <div class="handKeyImg">
            <img :src="handKeyBg" style="margin: 0 auto">
            <div :class="{handKey: true, logs: handKeyLogs.length>0}">
              <div :class="{handShank: true, up: handKeyDown&&trainData.status===1}"></div>
            </div>
            <div :class="{'handKeyLogs overflow-auto': true, logs: handKeyLogs.length>0}" ref="logsContainerRef">
              <template v-for="(log, l) in handKeyLogs">
                <div :class="{log: true, gap: log.val === 2}" v-if="log.time.length > 0"
                     :style="{fontSize: (fs * 1 + 12) + 'px'}">
                  <span class="lab" :data="log.val"></span>
                  {{ log.time[1] - log.time[0] }} ms
                </div>
              </template>
            </div>
          </div>
        </div>

      </div>
      <div class="trainRight">
        <CutDown :nowTime="nowTime"></CutDown>
        <div class="paging">
          <div class="item fs_dispose_1">
            <span class="ico"></span>当前：
            <strong class="deepen">{{ trainData.baoDiList.findIndex(item => item.id === currBaoWen.id) + 1 }}</strong>
          </div>
          <div class="item fs_dispose_1">
            <span class="ico"></span>总数：
            <strong class="deepen">{{ trainData.baoDiList.length }}</strong>
          </div>
        </div>
        <div class="h-full list overflow-auto">
          <div v-if="baoWenLoading" class="baoWenLoading">
            <a-spin size="small" style="margin-right: 8px;"/>
            报文数据正在加载中...
          </div>
          <div class="flex w-full" ref="trainBaoDiBoxRef">
            <template v-if="trainData.status===3">
              <div v-for="(bd, i) in trainData.baoDiList" :key="i"
                   :class="{item: true,
                          success: bd.errNumber<=0&&bd.baoWenList.every(bw => (bw.val.length>0&&bd.type<10)||(bd.type>=10&&bw.val.every(w => w.length>0)))&&(i<=lastBaoDiIndex||lastBaoDiIndex===-1),
                          warning: bd.errNumber>0&&bd.errNumber<=4&&(i<=lastBaoDiIndex||lastBaoDiIndex===-1),
                          error: bd.errNumber>4&&(i<=lastBaoDiIndex||lastBaoDiIndex===-1),
                          disabled: bd.disabled,
                          currEdit: bd.currEdit,
                          active: bd.id===currBaoWen.id}"
                   @click="selectedBaoDiInfo(bd, i)"
                   :style="{fontSize: (fs * 1 + 13) + 'px'}">
                <span class="labNum" :style="{fontSize: (fs * 1 + 12) + 'px'}">{{ i + 1 }}</span>
                <div class="itemTag"></div>
                <div>类型：{{ trainTypeArr[bd.type.toString()] }}</div>
                <div>数量：{{ bd.baoWenList.length===0?'--':bd.baoWenList.length }}</div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <div class="achievementMasking" v-show="showResultModal && trainData.status === 3">
      <div class="achievement">
        <div class="resTitle">
          <img v-if="parseFloat(trainData.accuracy)>80" :src="resText1">
          <img v-else-if="parseFloat(trainData.accuracy)>60" :src="resText2">
          <img v-else :src="resText3">
        </div>
        <div class="cont">
          <div class="dataBox">
            <div class="resLeft w-full">
              <div class="top">
                <img v-if="parseFloat(trainData.accuracy)>80" :src="tagScrapSuccess">
                <img v-else-if="parseFloat(trainData.accuracy)>60" :src="tagScrapWarning"
                     alt="">
                <img v-else :src="tagScrapError" alt="">
                <div class="desc" style="color: #7b90af;">本次训练用时</div>
                <div class="time">{{ resSustainTime }}</div>
              </div>
              <div class="bottom">
                <div class="resGroup">
                  <img :src="resAccuracy" alt="">
                  <div class="cont">
                    <div class="desc">正确率</div>
                    <div class="num">{{ trainData.accuracy }}%</div>
                  </div>
                </div>
                <div class="resGroup">
                  <img :src="resSpeed" alt="">
                  <div class="cont">
                    <div class="desc">速度</div>
                    <div class="num">{{ trainData.speed }}<span
                      style="font-size: 20px;">{{ wpmTOmm ? '码/分' : 'WPM' }}</span></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="splitLine"></div>
            <div class="resRight w-full">
              <div class="resTextItem">
                <div class="desc">报底数量</div>
                <div class="num">{{ trainData.baoDiList.length }}个</div>
              </div>
              <div class="resTextItem">
                <div class="desc">报文总数</div>
                <div class="num">{{ trainData.totalNumber }}个</div>
              </div>
              <div class="resTextItem">
                <div class="desc">错误总数</div>
                <div class="num">{{ trainData.errorNumber }}个</div>
              </div>
            </div>
          </div>
          <div class="chartBox">
            <div class="chartTitle" style="height: 58px;">
              <div class="tabs">
                <span :class="{tab: true, active: currChart===-2}" @click="seeTrainChart(-2)">拍发态势</span>
                <span :class="{tab: true, active: currChart===-1}" @click="seeTrainChart(-1)">拍发总览</span>
                <span :class="{tab: true, active: currChart===0}" @click="seeTrainChart(0)">点耗时</span>
                <span :class="{tab: true, active: currChart===1}" @click="seeTrainChart(1)">划耗时</span>
                <span :class="{tab: true, active: currChart===2}" @click="seeTrainChart(2)">间隔耗时</span>
              </div>
              <div class="legend" v-show="currChart===-2">
                <div class="leg dot">点</div>
                <div class="leg line">划</div>
                <div class="leg gap">间隔</div>
                <div class="leg omission" v-if="trainData.type > 10">漏拍</div>
                <div class="leg nimiety">多拍</div>
                <div class="leg abnormal">异常</div>
              </div>
              <div class="flex" v-show="currChart>-2">
                <div class="item">
                  <div class="lab">
                    平均{{ currChart === 0 ? '点' : currChart === 1 ? '线' : currChart === 2 ? '间隔' : '点/线/间隔' }}
                  </div>
                  <strong class="num">
                    {{
                    currChart === 0 ? (consumTime.dot.av + 'ms') :
                    currChart === 1 ? (consumTime.line.av + 'ms') :
                    currChart === 2 ? (consumTime.gap.av + 'ms') :
                    (consumTime.dot.av + '/' + consumTime.line.av + '/' + consumTime.gap.av + ' ms')
                    }}
                  </strong>
                </div>
                <div class="item">
                  <div class="lab">
                    最短{{ currChart === 0 ? '点' : currChart === 1 ? '线' : currChart === 2 ? '间隔' : '点/线/间隔' }}
                  </div>
                  <strong class="num">
                    {{
                    currChart === 0 ? (consumTime.dot.min + 'ms') :
                    currChart === 1 ? (consumTime.line.min + 'ms') :
                    currChart === 2 ? (consumTime.gap.min + 'ms') :
                    (consumTime.dot.min + '/' + consumTime.line.min + '/' + consumTime.gap.min + ' ms')
                    }}
                  </strong>
                </div>
                <div class="item">
                  <div class="lab">
                    最长{{ currChart === 0 ? '点' : currChart === 1 ? '线' : currChart === 2 ? '间隔' : '点/线/间隔' }}
                  </div>
                  <strong class="num">
                    {{
                    currChart === 0 ? (consumTime.dot.max + 'ms') :
                    currChart === 1 ? (consumTime.line.max + 'ms') :
                    currChart === 2 ? (consumTime.gap.max + 'ms') :
                    (consumTime.dot.max + '/' + consumTime.line.max + '/' + consumTime.gap.max + ' ms')
                    }}
                  </strong>
                </div>
              </div>
            </div>
            <div v-if="chartData.length === 0" class="layout-center"
                 style="height: 175px;flex-direction: column;color: #7b90af;">
              <img :src="dataEmpty" style="margin-bottom: 8px;">
              无拍发成绩！
            </div>
            <div class="patHairTrend" v-show="currChart===-2&&chartData.length>0" ref="patHairTrendBoxRef">
             <template v-if="showResultModal && trainData.status === 3">
               <template v-for="(group, g) in trendLogData" >
                 <template v-if="trainData.type > 10 && group.list.length > 0">
                   <div class="groupLog">
                     <div class="codeContLog">
                       <template v-for="(word, w) in group.list">
                         <div :class="{key: true, omiss: (group.key.length - group.list.length) > 0}">
                           <div class="times">
                             <template v-for="(code, c) in word">
                               <div :class="{time: true,
                                        dot: code.key===0 || code.key===10,
                                        line: code.key===1 || code.key===11,
                                        gap: code.key===2 || code.key===12}"
                                    :style="{width: parseInt(code.value/2)+'px',minWidth: '15px'}"
                                    v-if="c < (word.length - 1)">
                                 <div class="num">{{ code.value }}</div>
                                 <div class="nimi"
                                      v-if="c < (word.length - morseCode[numberCodeType][group.key[w]].len*2)"></div>
                                 <div class="abno" v-if="code.key===12||code.key===11||code.key===10"></div>
                               </div>
                             </template>
                           </div>
                           <div class="keyName">{{ group.key[w] }}</div>
                         </div>
                         <div class="sep" v-if="w < (group.list.length - 1)">
                           <div class="times">
                             <div class="time gap"
                                  :style="{maxWidth: parseInt((patStandard.interval.max+patStandard.dot.max)/2)+'px',
                                      width:parseInt(word[word.length-1].value/2)+'px'}">
                               <div class="num">{{ word[word.length - 1].value }}</div>
                               <div class="abno" v-if="word[word.length-1].key===12"></div>
                             </div>
                           </div>
                           <div class="keyGap"></div>
                         </div>
                       </template>
                       <template v-if="(group.key.length - group.list.length) > 0">
                         <template v-for="(omi, o) in group.key">
                           <div class="key omission" v-if="o > (group.list.length - 1)">
                             <div class="omis"></div>
                             <div class="keyName">{{ omi }}</div>
                           </div>
                         </template>
                       </template>
                     </div>
                     <div class="name">{{ group.key.join('') }}</div>
                   </div>
                   <div class="sep" v-if="g < (trendLogData.length - 1)">
                     <div class="times" v-if="group.list.length > 0">
                       <div class="time gap"
                            :style="{maxWidth: parseInt((patStandard.gap.max+patStandard.dot.max)/2)+'px',
                                width:parseInt(group.list[group.list.length-1][group.list[group.list.length-1].length-1].value/2)+'px'}">
                         <div class="num">
                           {{ group.list[group.list.length - 1][group.list[group.list.length - 1].length - 1].value }}
                         </div>
                         <div class="abno"
                              v-if="group.list[group.list.length-1][group.list[group.list.length-1].length-1].key===12"></div>
                       </div>
                     </div>
                   </div>
                 </template>
                 <template v-else>
                   <div class="codeContLog" v-if="group.list.length > 0&&g<editBaoDiIndex*100">
                     <div class="key">
                       <div class="times">
                         <template v-for="(code, c) in group.list">
                           <div :class="{time: true,
                                    dot: code.key===0 || code.key===10,
                                    line: code.key===1 || code.key===11,
                                    gap: code.key===2 || code.key===12}"
                                :style="{width: parseInt(code.value/2)+'px',minWidth: '15px'}"
                                v-if="c < (group.list.length - 1)">
                             <div class="num">{{ code.value }}</div>
                             <div class="nimi"
                                  v-if="c < (group.list.length - morseCode[numberCodeType][group.key].len*2)"></div>
                             <div class="abno" v-if="code.key===12||code.key===11||code.key===10"></div>
                           </div>
                         </template>
                       </div>
                       <div class="keyName">{{ group.key }}</div>
                     </div>
                     <div class="sep" v-if="g < (trendLogData.length - 1)">
                       <div class="times" v-if="group.list.length > 0">
                         <div class="time gap"
                              :style="{maxWidth: parseInt((patStandard.interval.max+patStandard.dot.max)/2)+'px',
                                    width:parseInt(group.list[group.list.length-1].value/2)+'px'}">
                           <div class="num">{{ group.list[group.list.length - 1].value }}</div>
                           <div class="abno" v-if="group.list[group.list.length-1].key===12"></div>
                         </div>
                       </div>
                       <div class="keyGap"></div>
                     </div>
                   </div>
                 </template>
               </template>
             </template>
            </div>
            <div id="handKeyChart" class="handKeyChart" v-show="currChart>-2&&chartData.length>0"></div>
          </div>
        </div>
        <div class="resClose">
          <div class="closeInfo">
            <div class="close" @click="showResultModal=false"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'ReceivePre'
  }
</script>
<script setup>
  import { ref, onMounted, onUnmounted, watch, inject } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { message } from 'ant-design-vue'
  import { createFromIconfontCN } from '@ant-design/icons-vue'
  import CountDown from '../../../../../components/common/CountDown.vue'
  import Number from '../../../../../components/number/Number.vue'
  import useControl from './js/useControl.js'
  import useDetails from './js/useDetails.js'
  import wordCode from './js/wordCode.js'
  import { getTelegramTrainById, getFloorContentByFloorId, getFloorContentByFloorIdAsync,getFloorContentByFloor } from '../../../../../common/api/TelegramApi.js'
  import { partTimeFormatInfo, sum } from '../../../../../common/utils/Utils.js'
  import { PubSub } from '../../../../../common/utils/PubSub.js'
  import { wsCode } from '../../../../../common/ws/Ws.js'
  import volumeNone from '../../../../../assets/HJ/train/volume-none.png'
  import volumeMini from '../../../../../assets/HJ/train/volume-mini.png'
  import volumeBig from '../../../../../assets/HJ/train/volume-big.png'
  import volumeSmall from '../../../../../assets/HJ/train/volume-small.png'
  import labNum from '../../../../../assets/HJ/train/lab-num.png'
  import labErr from '../../../../../assets/HJ/train/lab-err.png'
  import labAccuracy from '../../../../../assets/HJ/train/lab-accuracy.png'
  import labSpeed from '../../../../../assets/HJ/train/lab-speed.png'
  import startExercise from '../../../../../assets/HJ/postTrain/start-exercise.png'
  import endExercise from '../../../../../assets/HJ/postTrain/end-exercise.png'
  import detailExercise from '../../../../../assets/HJ/train/detail-exercise.png'
  import resText1 from '../../../../../assets/HJ/train/res-text-1.png'
  import resText2 from '../../../../../assets/HJ/train/res-text-2.png'
  import resText3 from '../../../../../assets/HJ/train/res-text-3.png'
  import dataEmpty from '../../../../../assets/HJ/train/dataEmpty.png'
  import tagScrapSuccess from '../../../../../assets/HJ/train/tag-scrap-success.png'
  import tagScrapWarning from '../../../../../assets/HJ/train/tag-scrap-warning.png'
  import tagScrapError from '../../../../../assets/HJ/train/tag-scrap-error.png'
  import resAccuracy from '../../../../../assets/HJ/train/res-accuracy.png'
  import resSpeed from '../../../../../assets/HJ/train/res-speed.png'
  import countLab from '../../../../../assets/HJJ/telexTrain/count.png'
  import errorLab from '../../../../../assets/HJJ/telexTrain/error.png'
  import successLab from '../../../../../assets/HJJ/telexTrain/success.png'
  import speedLab from '../../../../../assets/HJJ/telexTrain/speed.png'
  import handKeyBgHJ from '../../../../../assets/HJ/train/hand-key-bg.png'
  import handKeyBgHJJ from '../../../../../assets/HJJ/train/hand-key-bg.png'
  import handKeyBgLJ from '../../../../../assets/LJ/train/hand-key-bg.png'
  import handKeyBgKJ from '../../../../../assets/KJ/train/hand-key-bg.png'
  import CutDown from '../../../../../components/cutDown/CutDown.vue'
  const interfaceStyle = window.interfaceStyle
  let handKeyBg
  if (interfaceStyle==='HJ'){
    handKeyBg = handKeyBgHJ
  }else if(interfaceStyle==='HJJ'){
    handKeyBg = handKeyBgHJJ
  }else if(interfaceStyle==='KJ'){
    handKeyBg = handKeyBgKJ
  }else {
    handKeyBg = handKeyBgLJ
  }
  const fs = ref(JSON.parse(localStorage.getItem('fs')));
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl
  })
  const wpmTOmm = inject('wpmTOmm')
  const route = useRoute()
  const router = useRouter()
  const title = ref('请点击下方[开始练习]按钮开启训练')
  const loading = ref(true)
  const baoWenLoading = ref(true)
  const patHairTrendBoxRef = ref(null)
  const trainDeploy = ref(0)
  const handKeyWidth = ref(112)
  const minSpeed = ref(60)
  const volume = ref(50)
  const labs = ref(['status', 'totalNumber', 'errorNumber', 'accuracy', 'totalKnockNumber', 'speed', 'nowFloorId', 'type'])
  /** gradient:渐变; metal:金属; chapped:皲裂; white:纯白;  */
  const codeTypeArr = ref([
    { type: 'gradient', name: '渐变' },
    { type: 'metal', name: '金属' },
    { type: 'chapped', name: '皲裂' },
    { type: 'white', name: '纯白' }
  ])
  /** gradient:渐变; metal:金属; chapped:皲裂; white:纯白;  */
  const trainTypeArr = ref({
    0: '字码报',
    1: '数码报',
    2: '混合报',
    11: '点报',
    12: '划报',
    13: '点划报',
    14: '点划连接报'
  })
  const codeType = ref('gradient')
  const trainData = ref({
    time: {
      startTime: 0,
      pauseTime: 0,
      endTime: 0,
      sustainTime: 0
    },
    trainId: '',
    shortCode: false,
    status: -1,
    totalNumber: 0,
    errorNumber: 0,
    successNumber: 0,
    accuracy: 100,
    totalKnockNumber: 0,
    speed: 0,
    nowFloorId: null,
    baoDiList: []
  })
  const showResultModal = ref(false)
  const fileUrl = ref(window.fileUrl + '/006/code/')

  /**
   * 计算报文按键宽度自适应
   */
  const handleBaoWenKeyInfo = () => {
    if (!handKeyBoardBoxRef.value) return false
    let boxWidth = handKeyBoardBoxRef.value.clientWidth - 8
    handKeyWidth.value = (boxWidth % 120) / parseInt(boxWidth / 120) + 112
  }

  const { handKeyDown, patStandard, handKeyValue, diffTime, gapTime, wsOnline, devOnline, audioVolume, init } = useControl(trainData)
  //查询最后一页报底的时候执行
  const findLastPage = ()=>{
    trainData.value.baoDiList.forEach(d => {
      d.baoWenList.forEach(w => {
        trendLogData.value.push({
          key: w.key,
          list: []
        })
      })
    })
    handleKeyTrendData()
  }

  const {
    handKeyBoardBoxRef,
    trainBaoDiBoxRef,
    focusTrainThumbRef,
    lastBaoDiIndex,
    nowTime,
    trainTimeRef,
    currBaoWen,
    currBaoDiIndex,
    editBaoDiIndex,
    currBaoWenIndex,
    morseCode,
    numberCodeType,
    resSustainTime,
    timeAreaShow,
    pauseExerciseInfo,
    initTrainTimeInfo,
    initMorseCodeInfo,
    editHandKeyInfo,
    beginExerciseInfo,
    endExerciseInfo,
    selectedBaoDiInfo,
    handKeyLogs,
    logsContainerRef,
    modifyBaoDiIds,
    isTrainFocusMode,
    switchTrainMode,
    getTrainSendRecordLog,
    handleAchievementChart,
    handleChartData,
    lineChart,
    chartData,
    proportion,
    consumTime,
    currChart,
    handlePatDeployData,
    seeTrainChart,
    trendLogData,
    handleKeyTrendData,
    getFloorContentInfo
  } = useDetails(handKeyValue, diffTime, trainData, patStandard, loading, title, handleBaoWenKeyInfo, handKeyWidth, wsOnline, devOnline, wpmTOmm,findLastPage)

  const { initWordCodeInfo } = wordCode(trainData, currBaoWen, currBaoWenIndex, handKeyBoardBoxRef)

  onMounted(() => {
    window.addEventListener('beforeunload', e => {
      pauseExerciseInfo()
    })

    patHairTrendBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patHairTrendBoxRef.value.scrollLeft += 100
      } else {
        patHairTrendBoxRef.value.scrollLeft -= 100
      }
    })

    if (route.query.id && route.query.id !== '') {
      trainData.value.trainId = route.query.id
      currBaoDiIndex.value = -1
      getTelegramTrainById({
        trainId: trainData.value.trainId
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          patStandard.value.dot.min = res.data.train.rateDotMinMs
          patStandard.value.dot.max = res.data.train.rateDotMaxMs
          patStandard.value.line.min = res.data.train.rateLineMinMs
          patStandard.value.line.max = res.data.train.rateLineMaxMs
          patStandard.value.interval.min = res.data.train.rateIntervalMinMs
          patStandard.value.interval.max = res.data.train.rateIntervalMaxMs
          patStandard.value.gap.min = res.data.train.bigIntervalMinMs
          patStandard.value.gap.max = res.data.train.bigIntervalMaxMs
          proportion.value.line = parseInt(res.data.train.rateLineMaxMs / res.data.train.rateDotMaxMs)
          proportion.value.interval = parseInt(res.data.train.rateIntervalMaxMs / res.data.train.rateDotMaxMs - 1)
          proportion.value.gap = parseInt(res.data.train.bigIntervalMaxMs / res.data.train.rateDotMaxMs - 1)
          for (let key in trainData.value.time) {
            if (res.data.train[key] && res.data.train[key] !== '') {
              trainData.value.time[key] = parseInt(res.data.train[key])
            } else {
              trainData.value.time[key] = 0
            }
          }
          for (let lab of labs.value) {
            trainData.value[lab] = res.data.train[lab]
          }
          trainData.value.baoDiList = []
          trainData.value.shortCode = false
          baoWenLoading.value = false
          trainData.value.nowFloorId = res.data.trainFloors[0].floor.id
          res.data.trainFloors.map((bd, index) => {
            if (bd.floorContents.length > 0 && res.data.train.status !== 3) {
              currBaoDiIndex.value = index
              editBaoDiIndex.value = index
              lastBaoDiIndex.value = index
              numberCodeType.value = bd.floor.numberType === 1 ? 'short' : 'mix'
              if (bd.floorContents[bd.floorContents.length - 1].moresValue !== '[]' && bd.floorContents[bd.floorContents.length - 1].moresValue !== '[[],[],[],[]]') {
                currBaoDiIndex.value++
                editBaoDiIndex.value++
                lastBaoDiIndex.value++
              }
            }
            if (editBaoDiIndex.value === index) {
              modifyBaoDiIds.value.push(bd.floor.id)
              trainData.value.nowFloorId = bd.floor.id
            }
            trainData.value.shortCode = bd.floor.numberType === 1
            trainData.value.baoDiList.push({
              id: bd.floor.id,
              type: bd.floor.type,
              numberType: bd.floor.numberType,
              errNumber: -1,
              disabled: currBaoDiIndex.value !== -1 && index > currBaoDiIndex.value && res.data.train.status !== 3,
              currEdit: currBaoDiIndex.value === index,
              baoWenList: []
            })
          })
          if (res.data.train.status === 1) {
            title.value = '本次练习正在进行，当前总耗时'
            initTrainTimeInfo()
          }
          if (res.data.train.status === 2) {
            title.value = '本次练习正在进行，当前总耗时'
            beginExerciseInfo()
          }
          if (res.data.train.status === 3) {
            title.value = '本次练习已结束,总用时'
            isTrainFocusMode.value = false
            editBaoDiIndex.value = -1
            lastBaoDiIndex.value = -1
            currBaoWenIndex.value = -1
            timeAreaShow(trainData.value.time.sustainTime)
            resSustainTime.value = partTimeFormatInfo(trainData.value.time.sustainTime, 'number')
            getTrainSendRecordLog()
          }
          getFloorContentInfo(editBaoDiIndex.value)
          init().then()
        } else {
          message.error(res.message)
        }
      })
    }

    window.onresize = () => {
      handleBaoWenKeyInfo()
    }
  })

  watch(handKeyValue, () => {
    if (handKeyValue.value !== null) {
      if (trainData.value.status === 0) {
        initMorseCodeInfo(handKeyValue.value, diffTime.value, gapTime.value)
        beginExerciseInfo()
      } else if (trainData.value.status === 1) {
        initMorseCodeInfo(handKeyValue.value, diffTime.value, gapTime.value)
      }
    }
  })
  let isFirst = true
  /**
   * 接收报底的所有报文数据的订阅消息
   */
  PubSub.subscribe(wsCode.FLOOR_CONTENT_DATA, data => {
    let num = 0,
        bwArr = [],
        bd = null
    if (trainData.value.type > 10) {
      initWordCodeInfo(data)
    } else {
      for (let key in data) {
        for (let d = 0; d < trainData.value.baoDiList.length; d++) {
          if (trainData.value.baoDiList[d].id === key) {
            bd = trainData.value.baoDiList[d]
            bd.baoWenList = []
            num = 0
            bwArr = []
            data[key].map((bw, w) => {
              if (typeof bw.moresValue === 'string') {
                bw.moresValue = JSON.parse(bw.moresValue)
              }
              if (typeof bw.moresTime === 'string') {
                bw.moresTime = JSON.parse(bw.moresTime)
              }
              if (bw.moresValue.join('') !== '' && bw.moresValue.join('') !== morseCode[numberCodeType.value][bw.moresKey].value) {
                num++
              }
              if (trainData.value.nowFloorId === key && bw.moresValue.length === 0 && currBaoWenIndex.value < 0) {
                currBaoWenIndex.value = w
              }
              bwArr.push({ id: bw.id, key: bw.moresKey, val: bw.moresValue, time: bw.moresTime })
            })
            bd.baoWenList = bwArr
            bd.errNumber = num > 0 ? num : -1
            if ((!trainData.value.nowFloorId && d === 0) || trainData.value.nowFloorId === key) {

              if(isFirst){
                isFirst = false
                currBaoWen.value = bd
              }
              setTimeout(() => {
                handleBaoWenKeyInfo()
                if (currBaoWenIndex.value > -1) {
                  handKeyBoardBoxRef.value.children[currBaoWenIndex.value].scrollIntoView(false)
                  focusTrainThumbRef.value.children[currBaoWenIndex.value].scrollIntoView(false)
                }
              }, 500)
            }
            break
          }
        }
      }
    }
  })

  /**
   * 报底的所有报文数据接收完毕的订阅消息
   */
  PubSub.subscribe(wsCode.FLOOR_CONTENT_DATA_OVER, () => {
    // baoWenLoading.value = false
    // trainData.value.baoDiList.forEach(d => {
    //   d.baoWenList.forEach(w => {
    //     trendLogData.value.push({
    //       key: w.key,
    //       list: []
    //     })
    //   })
    // })
    // handleKeyTrendData()
  })

  /**
   * 销毁订阅消息
   */
  onUnmounted(() => {
    PubSub.unsubscribe('message')
    PubSub.unsubscribe(wsCode.FLOOR_CONTENT_DATA)
    PubSub.unsubscribe(wsCode.FLOOR_CONTENT_DATA_OVER)
    if (lineChart) {
      if (lineChart.value) {
        lineChart.value = null
      }
    }
  })



  const goBack = () => {
    /*if (trainData.value.status === 1) {
      endExerciseInfo()
    } else {*/
    router.go(-1)
    /*}*/
  }
</script>

<style scoped lang="less">
  @import "./css/HandKeyTrain";
  @import "../../css/code";
</style>
