<template>
  <div class="w-full layout-side" style="height: 720px; margin-top: 20px; color: #bbcdef; position: relative; z-index: 9; max-height: calc(100vh - 160px)">
    <div class="h-full border-r pr-1 borderColor" style=" width: 200px">
      <div class="item_group btn big mb-2" @click="addGradingRuleInfo" style="justify-content: center"><PlusOutlined /> 新增评分规则</div>
      <div class="w-full overflow-auto" style="height: calc(100% - 40px)">
        <div class="w-full layout-left-center" v-for="(r, index) in ruleList" :key="index">
          <div class="w-full h-full cursor-pointer p-1 ruleItem layout-side" :class="[pickRuleIndex === index ? 'rule-active' : 'rule-un-active']" @click="pickGradingRuleInfo(index)">
            <div>{{ r.title }}</div>
            <a-popconfirm
                title="是否删除该评分规则?"
                ok-text="是"
                cancel-text="否"
                @confirm="deleteGard(r,index)"
            >
              <DeleteOutlined style="color: red;" title="删除" />
            </a-popconfirm>
          </div>
        </div>
      </div>
    </div>
    <div class="h-full grading-list overflow-auto" style="width: calc(100% - 200px)">
      <div class="w-full pl-1 pr-1 layout-left-center" v-if="pickRuleIndex > -1">
        <a-row class="w-full">
          <a-col :span="3" class="layout-right-center pr-1">规则名称</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-input v-model:value="ruleList[pickRuleIndex].title" />
          </a-col>
          <a-col :span="3" class="layout-right-center pr-1">初始分数</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-input-number v-model:value="ruleList[pickRuleIndex].score" :min="1" :max="1000" :step="1" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" :precision="0" />
          </a-col>

          <a-col :span="3" class="layout-right-center pr-1">默认规则</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-switch checked-children="是" un-checked-children="否" v-model:checked="ruleList[pickRuleIndex].isDefault" style="margin-left: 6px" />
          </a-col>
          <!--<a-col :span="3" class="layout-right-center pr-1">码率单位</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-switch checked-children="WPM" un-checked-children="码/分" :disabled="wpmTOmm" v-model:checked="ruleList[pickRuleIndex].content.wpm.type" @change="changeSpeedUnit"/>
          </a-col>-->
        </a-row>
        <a-row class="w-full">
          <a-col :span="3" class="layout-right-center pr-1">设定速度</a-col>
          <a-col :span="4" class="layout-left-center">
            <a-input-number
              :min="1"
              :max="ruleList[pickRuleIndex].content.wpm.type ? 50 : 150"
              :step="1"
              style="width: 100%"
              :precision="0"
              :formatter="value => `${value}` + (ruleList[pickRuleIndex].content.wpm.type ? 'WPM' : '码/分')"
              :parser="value => value.replace(ruleList[pickRuleIndex].content.wpm.type ? 'WPM' : '码/分', '')"
              v-model:value="ruleList[pickRuleIndex].content.wpm.base"
            />
          </a-col>
          <a-col :span="1" class="layout-right-center cursor-pointer-def">
            <a-tooltip class="layout-right-center" color="orange">
              <template #title>
                WPM：每分钟包含的无间隔点的个数除以50<br />
                公式：<br />
                (1000÷X)×60÷50=Y<br />
                X：点长度，单位为毫秒<br />
                Y：发报速度，单位WPM
              </template>
              <QuestionCircleOutlined style="color: orange" />
            </a-tooltip>
          </a-col>
          <a-col :span="3" class="layout-right-center pr-1">低一扣</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-input-number :min="0" :max="100" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.wpm.l" />
          </a-col>
          <a-col :span="3" class="layout-right-center pr-1">高一加</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-input-number :min="0" :max="100" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.wpm.r" />
          </a-col>
        </a-row>
        <a-row class="w-full">
          <a-col :span="3" class="layout-right-center pr-1 cursor-pointer-def">
            <a-tooltip class="layout-left-center" color="orange">
              <template #title>基于设定速度产生的标准值<br />在系统进行识别时的上下偏移值</template>
              识别偏移&nbsp;<QuestionCircleOutlined style="color: orange" />
            </a-tooltip>
          </a-col>
          <a-col :span="18" class="grading-list pl-2 pr-4">
            <a-slider :marks="marks" v-model:value="ruleList[pickRuleIndex].content.skew" />
          </a-col>
          <a-col :span="3" class="layout-left-center">
            <a-input-number :min="0" :max="100" :step="1" :precision="0" :formatter="value => `${value}%`" :parser="value => value.replace('%', '')" v-model:value="ruleList[pickRuleIndex].content.skew" />
          </a-col>
        </a-row>
        <a-row class="w-full border-r border-l border-t borderColor">
          <a-col :span="3" class="layout-center">分类</a-col>
          <a-col :span="21">
            <a-row class="w-full">
              <a-col :span="3" class="layout-center">类型</a-col>
              <a-col :span="4" class="layout-center" v-if="ruleList[pickRuleIndex].content.wpm.type">标准(ms)</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">临界值(ms)</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">＜零界值扣分</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">＞零界值扣分</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">单项最大扣分</a-col>
            </a-row>
          </a-col>
        </a-row>
        <a-row class="w-full border borderColor">
          <a-col :span="3" class="layout-center">码</a-col>
          <a-col :span="21">
            <a-row class="w-full">
              <a-col :span="3" class="layout-left-center cursor-pointer-def">
                <a-tooltip class="layout-left-center" color="orange">
                  <template #title>小于临界值为点虚<br />大于临界值为点粗</template>
                  点长度&nbsp;<QuestionCircleOutlined style="color: orange" />
                </a-tooltip>
              </a-col>
              <a-col :span="4" class="layout-center" v-if="ruleList[pickRuleIndex].content.wpm.type">{{ js(0) }}</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="1" :max="1000" :step="1" :precision="0" :formatter="value => `${value}ms`" :parser="value => value.replace('ms', '')" v-model:value="ruleList[pickRuleIndex].content.code.dot.base" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.code.dot.l" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.code.dot.r" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.code.dot.max" />
              </a-col>
            </a-row>
            <a-row class="w-full">
              <a-col :span="3" class="layout-left-center cursor-pointer-def">
                <a-tooltip class="layout-left-center" color="orange">
                  <template #title>小于临界值为划短<br />大于临界值为划长</template>
                  划长度&nbsp;<QuestionCircleOutlined style="color: orange" />
                </a-tooltip>
              </a-col>
              <a-col :span="4" class="layout-center" v-if="ruleList[pickRuleIndex].content.wpm.type">{{ js(1) }}</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="1" :max="1000" :step="1" :precision="0" :formatter="value => `${value}ms`" :parser="value => value.replace('ms', '')" v-model:value="ruleList[pickRuleIndex].content.code.dash.base" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.code.dash.l" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.code.dash.r" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.code.dash.max" />
              </a-col>
            </a-row>
          </a-col>
        </a-row>
        <a-row class="w-full border-l border-b border-r borderColor" >
          <a-col :span="3" class="layout-center">间隔</a-col>
          <a-col :span="21">
            <a-row class="w-full">
              <a-col :span="3" class="layout-left-center cursor-pointer-def">
                <a-tooltip class="layout-left-center" color="orange">
                  <template #title>小于临界值为码间隔过小<br />大于临界值为码间隔过大</template>
                  码间隔&nbsp;<QuestionCircleOutlined style="color: orange" />
                </a-tooltip>
              </a-col>
              <a-col :span="4" class="layout-center" v-if="ruleList[pickRuleIndex].content.wpm.type">{{ js(0) }}</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="1" :max="1000" :step="1" :precision="0" :formatter="value => `${value}ms`" :parser="value => value.replace('ms', '')" v-model:value="ruleList[pickRuleIndex].content.gap.little.base" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.little.l" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.little.r" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.little.max" />
              </a-col>
            </a-row>
            <a-row class="w-full">
              <a-col :span="3" class="layout-left-center cursor-pointer-def">
                <a-tooltip class="layout-left-center" color="orange">
                  <template #title>小于临界值为字间隔过小<br />大于临界值为字间隔过大</template>
                  字间隔&nbsp;<QuestionCircleOutlined style="color: orange" />
                </a-tooltip>
              </a-col>
              <a-col :span="4" class="layout-center" v-if="ruleList[pickRuleIndex].content.wpm.type">{{ js(2) }}</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="1" :max="1000" :step="1" :precision="0" :formatter="value => `${value}ms`" :parser="value => value.replace('ms', '')" v-model:value="ruleList[pickRuleIndex].content.gap.middle.base" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.middle.l" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.middle.r" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.middle.max" />
              </a-col>
            </a-row>
            <a-row class="w-full">
              <a-col :span="3" class="layout-left-center cursor-pointer-def">
                <a-tooltip class="layout-left-center" color="orange">
                  <template #title>小于临界值为组间隔过小<br />大于临界值为组间隔过大</template>
                  组间隔&nbsp;<QuestionCircleOutlined style="color: orange" />
                </a-tooltip>
              </a-col>
              <a-col :span="4" class="layout-center" v-if="ruleList[pickRuleIndex].content.wpm.type">{{ js(3) }}</a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="1" :max="1000" :step="1" :precision="0" :formatter="value => `${value}ms`" :parser="value => value.replace('ms', '')" v-model:value="ruleList[pickRuleIndex].content.gap.large.base" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.large.l" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.large.r" />
              </a-col>
              <a-col :span="ruleList[pickRuleIndex].content.wpm.type ? 4 : 5" class="layout-center">
                <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.gap.large.max" />
              </a-col>
            </a-row>
          </a-col>
        </a-row>
        <a-row class="w-full" style="padding: 0">
          <a-col :span="12">
            <a-row class="w-full border-r border-l border-b borderColor">
              <a-col :span="6" class="layout-center">分类</a-col>
              <a-col :span="18">
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center">类型</a-col>
                  <a-col :span="9" class="layout-center">单个扣分</a-col>
                  <a-col :span="9" class="layout-center">单项最大扣分</a-col>
                </a-row>
              </a-col>
            </a-row>
            <a-row class="w-full border-l border-b border-r borderColor" >
              <a-col :span="6" class="layout-center">其他</a-col>
              <a-col :span="18">
                <!--<a-row class="w-full" v-if="ruleList[pickRuleIndex].content.other.alterError">
                  <a-col :span="6" class="layout-left-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发改错</template>
                      改错字&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.alterError.l" />
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.alterError.max" />
                  </a-col>
                </a-row>-->
                <a-row class="w-full">
                  <a-col :span="6" class="layout-left-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发错误</template>
                      错误码&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.errorCode.l" />
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.errorCode.max" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-left-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发与报底比较，多拍、少拍总组数</template>
                      多少码&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.quantoCode.l" />
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.quantoCode.max" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-left-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发与报底比较，多拍、少拍组总数（四字为一组）</template>
                      多少组&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.quantoGroup.l" />
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.quantoGroup.max" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-left-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发与报底比较，重拍、漏拍、未拍的行数量</template>
                      多少行&nbsp;<QuestionCircleOutlined style="color: orange"/>
                    </a-tooltip>
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分','')" v-model:value="ruleList[pickRuleIndex].content.other.quantoRow.l"/>
                  </a-col>
                  <a-col :span="9" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分','')" v-model:value="ruleList[pickRuleIndex].content.other.quantoRow.max"/>
                  </a-col>
                </a-row>
              </a-col>
            </a-row>
          </a-col>
          <a-col :span="12">
            <a-row class="w-full border-r border-b borderColor">
              <a-col :span="5" class="layout-center">分类</a-col>
              <a-col :span="19">
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center">类型</a-col>
                  <a-col :span="18" class="layout-center">比例</a-col>
                </a-row>
              </a-col>
            </a-row>
            <div>
              <a-row class="w-full border-l border-b border-r borderColor" style="padding: 0">
                <a-col :span="6" class="layout-center">其他</a-col>
                <a-col :span="18">
                  <a-row class="w-full" v-if="ruleList[pickRuleIndex].content.other.alterError">
                    <a-col :span="5" class="layout-left-center cursor-pointer-def">
                      <a-tooltip class="layout-left-center" color="orange">
                        <template #title>拍发错误通过改错正确的组数</template>
                        改错&nbsp;<QuestionCircleOutlined style="color: orange"/>
                      </a-tooltip>
                    </a-col>
                    <a-col :span="9" class="layout-left-center">
                      <a-input-number :min="0" :max="1000" :step="1"
                                      :precision="0"
                                      :formatter="value => `${value}分`"
                                      :parser="value => value.replace('分','')"
                                      v-model:value="ruleList[pickRuleIndex].content.other.alterError.l"/>
                    </a-col>
                    <a-col :span="9" class="layout-left-center">
                      <a-input-number :min="0" :max="1000" :step="1"
                                      :precision="0"
                                      :formatter="value => `${value}分`"
                                      :parser="value => value.replace('分','')"
                                      v-model:value="ruleList[pickRuleIndex].content.other.alterError.max"/>
                    </a-col>
                  </a-row>
                  <a-row class="w-full">
                    <a-col :span="5" class="layout-left-center cursor-pointer-def">
                      <a-tooltip class="layout-left-center" color="orange">
                        <template #title>拍发报文串组到上一行或下一行的组数</template>
                        串组&nbsp;<QuestionCircleOutlined style="color: orange"/>
                      </a-tooltip>
                    </a-col>
                    <a-col :span="9" class="layout-left-center">
                      <a-input-number :min="0" :max="1000" :step="1"
                                      :precision="0"
                                      :formatter="value => `${value}分`"
                                      :parser="value => value.replace('分','')"
                                      v-model:value="ruleList[pickRuleIndex].content.other.bunchGroup.l"/>
                    </a-col>
                    <a-col :span="9" class="layout-left-center">
                      <a-input-number :min="0" :max="1000" :step="1"
                                      :precision="0"
                                      :formatter="value => `${value}分`"
                                      :parser="value => value.replace('分','')"
                                      v-model:value="ruleList[pickRuleIndex].content.other.bunchGroup.max"/>
                    </a-col>
                  </a-row>
                </a-col>
              </a-row>
              <a-row class="w-full border-b border-r borderColor" >
                <a-col :span="6" class="layout-center">拍发<br />比例</a-col>
                <a-col :span="18">
                  <a-row class="w-full">
                    <a-col :span="5" class="layout-left-center cursor-pointer-def">
                      <a-tooltip class="layout-left-center" color="orange">
                        <template #title>点与划比例<br />默认3划等于1个点</template>
                        点划&nbsp;<QuestionCircleOutlined style="color: orange" />
                      </a-tooltip>
                    </a-col>
                    <a-col :span="19" class="layout-left-center">
                      <a-input-number :min="1" :max="10" :step="1" :precision="0" style="width: 50px" :disabled="true" v-model:value="ruleList[pickRuleIndex].content.scale.dot" />&nbsp;&nbsp;:&nbsp;&nbsp;
                      <a-input-number :min="1" :max="10" :step="1" :precision="0" style="width: 50px" v-model:value="ruleList[pickRuleIndex].content.scale.dash" />
                    </a-col>
                  </a-row>
                  <a-row class="w-full">
                    <a-col :span="5" class="layout-left-center cursor-pointer-def">
                      <a-tooltip class="layout-left-center" color="orange">
                        <template #title>
                          码间隔、字间隔、组间隔<br />
                          码间隔默认为1个无间隔点的长度<br />
                          字间隔默认为3个无间隔点的长度<br />
                          组间隔默认为5个无间隔点的长度<br />
                        </template>
                        间隔&nbsp;<QuestionCircleOutlined style="color: orange" />
                      </a-tooltip>
                    </a-col>
                    <a-col :span="19" class="layout-left-center">
                      <a-input-number :min="1" :max="10" :step="1" :precision="0" style="width: 50px" :disabled="true" v-model:value="ruleList[pickRuleIndex].content.scale.little" />&nbsp;&nbsp;:&nbsp;&nbsp;
                      <a-input-number :min="1" :max="10" :step="1" :precision="0" style="width: 50px" v-model:value="ruleList[pickRuleIndex].content.scale.middle" />&nbsp;&nbsp;:&nbsp;&nbsp;
                      <a-input-number :min="1" :max="10" :step="1" :precision="0" style="width: 50px" v-model:value="ruleList[pickRuleIndex].content.scale.large" />
                    </a-col>
                  </a-row>
                </a-col>
              </a-row>
            </div>
          </a-col>
        </a-row>
        <div class="w-full pt-1 layout-center pr-2">

          <div class="layout-right-center">
            <a-button @click="handleCancel" class="layout-center" danger>
              <DeleteOutlined />
              取消编辑
            </a-button>
            <a-button @click="handleSubmit" class="layout-center">
              <CheckOutlined />
              提交规则
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'HandKeyGradingRule'
}
</script>
<script setup>
import { ref, onMounted, inject } from 'vue'
import * as gr from '../../common/api/GradingRuleApi.js'
import { PlusOutlined, QuestionCircleOutlined, StopOutlined, CheckOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { deepClone } from '../../common/utils/Utils.js'
const wpmTOmm = inject('wpmTOmm')
const loading = ref(false)
const ruleList = ref([])
const pickRuleIndex = ref(-1)
const marks = ref({
  0: '无偏移',
  20: '低偏移',
  50: '中偏移',
  80: '高偏移',
  100: '全偏移'
})

onMounted(() => {
  getGradingRule()
})
const getGradingRule = () => {
  gr.getGradingRuleListByType({ type: 0 }).then(res => {
    if (res.code === 200) {
      res.data.forEach(d => {
        d.isDefault = d.isDefault === 0
        d.content = JSON.parse(d.content)
      })
      ruleList.value = res.data
      if (ruleList.value.length > 0) {
        pickRuleIndex.value = 0
      }
    } else {
      message.error(res.message)
    }
  })
}
const addGradingRuleInfo = () => {
  ruleList.value.push({
    type: 0,
    title: '评分规则' + (ruleList.value.length + 1),
    score: 100,
    status: 0,
    isDefault: false,
    content: {
      wpm: {
        type: false,
        base: 60,
        r: 0,
        l: 1
      },
      skew: 50,
      code: {
        dot: {
          base: 10,
          l: 1,
          r: 5,
          max: 10
        },
        dash: {
          base: 10,
          l: 1,
          r: 1,
          max: 10
        }
      },
      gap: {
        little: {
          base: 10,
          l: 1,
          r: 5,
          max: 10
        },
        middle: {
          base: 10,
          l: 1,
          r: 5,
          max: 10
        },
        large: {
          base: 10,
          l: 1,
          r: 5,
          max: 10
        }
      },
      other: {
        alterError: {
          l: 1,
          max: 0
        },
        errorCode: {
          l: 1,
          max: 0
        },
        quantoCode: {
          l: 1,
          max: 10
        },
        quantoGroup: {
          l: 1,
          max: 10
        },
        quantoRow: {
          l: 1,
          max: 10
        },
        bunchGroup: {
          l: 1,
          max: 10
        }
      },
      scale: {
        dot: 1,
        dash: 3,
        little: 1,
        middle: 3,
        large: 5
      }
    }
  })
  pickRuleIndex.value = ruleList.value.length - 1
}
const pickGradingRuleInfo = index => {
  loading.value = true
  pickRuleIndex.value = index
  gr.getGradingRuleById({ id: ruleList.value[index].id }).then(res => {
    res.data.isDefault = res.data.isDefault === 0
    res.data.content = JSON.parse(res.data.content)
    if (res.code === 200) {
      ruleList.value[index] = res.data
      loading.value = false
    }
  })
}
const changeSpeedUnit = () => {
  ruleList.value[pickRuleIndex.value].content.wpm.base = ruleList.value[pickRuleIndex.value].content.wpm.type ? 10 : 60
}
const js = type => {
  let content = ruleList.value[pickRuleIndex.value].content
  if (type === 0) {
    return '0~' + ((1200 / content.wpm.base) * (content.scale.dot + content.skew / 100)).toFixed(0)
  }
  if (type === 1) {
    return ((1200 / content.wpm.base) * (1 + content.skew / 100) + 1).toFixed(0) + '~' + ((1200 / content.wpm.base) * content.scale.dash * (1 + content.skew / 100)).toFixed(0)
  }
  if (type === 2) {
    return ((1200 / content.wpm.base) * (1 + content.skew / 100) + 1).toFixed(0) + '~' + ((1200 / content.wpm.base) * content.scale.middle * (1 + content.skew / 100)).toFixed(0)
  }
  if (type === 3) {
    return ((1200 / content.wpm.base) * content.scale.middle * (1 + content.skew / 100) + 1).toFixed(0) + '~' + ((1200 / content.wpm.base) * content.scale.large * (1 + content.skew / 100)).toFixed(0)
  }
}
const handleCancel = () => {
  if (ruleList.value[pickRuleIndex.value].id === undefined) {
    const pi = pickRuleIndex.value
    pickRuleIndex.value = -1
    ruleList.value.splice(pi, 1)
  } else {
    getGradingRule()
  }
}
const handleSubmit = () => {
  let data = deepClone(ruleList.value[pickRuleIndex.value])
  data.isDefault = data.isDefault ? 0 : 1
  data.content = JSON.stringify(data.content)
  gr.saveGradingRule(data).then(res => {
    if (res.code === 200) {
      message.success('评分规则更新成功')
      getGradingRule()
    } else {
      message.error(res.message)
    }
  })
}
const deleteGard = (r,index)=>{
  if(r.isDefault){
    message.error('默认规则不能删除！')
    return
  }
  if(r.id){
    gr.deleteGradingRule({id: r.id}).then(res=>{
      if (res.code === 200) {
        message.success('删除成功！')
        getGradingRule()
      }else {
        message.error(res.message)
      }
    })
  }else {
    message.success('删除成功！')
    getGradingRule()
  }
}
</script>

<style lang="less">
  .HJ {
    .borderColor{border-color: #364e7b;}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem:hover,
    .rule-active {
      background: #24578c !important;
      color: #ffffff;
      border-left: 3px solid #70c9ff;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #bbcdef !important;
    }
  }
  .HJJ{
    .borderColor{border-color: rgb(54, 78, 123);}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem:hover,
    .rule-active {
      background: #4c7595 !important;
      color: #ffffff;
      border-left: 3px solid #70c9ff;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #bbcdef !important;
    }
  }
  .LJ{
    .borderColor{border-color:rgb(95, 101, 100);}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem{
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .ruleItem:hover,
    .rule-active {
      color: #ffffff;
      background-color: #533e1a;
      border-left: 3px solid #939797;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #a9abaa !important;
    }
  }
  .KJ{
    .borderColor{border-color:rgba(80,141,230,0.6);}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem{
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .ruleItem:hover,
    .rule-active {
      color: #ffffff;
      background-color: rgba(80,141,230,0.6);
      border-left: 3px solid #939797;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #a9abaa !important;
    }
  }

</style>
