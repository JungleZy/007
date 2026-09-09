<template>
  <div class="w-full h-full analyze boxBorderShadow innerBackgroundColor content-mask-bg" style="position: relative;">
    <!--背景边框S-->
    <div class="w-full h-full layout-side" style="position: relative;">
      <div class="h-full zuo" style="width: 256px;" v-if="interfaceStyle==='HJ'"></div>
      <div class="h-full you" style="width: 256px;" v-if="interfaceStyle==='HJ'"></div>
    </div>
    <!--背景边框E-->
    <div class="w-full h-full layout-side" style="position: absolute;left: 0;top: 0;" v-if="interfaceStyle==='HJ'">
      <!--大左S-->
      <div class="h-full leftBoxs" style="width: 400px;">
        <!--左上S-->
        <div class="w-full layout-side" style="height: 200px;padding-left: 50px;padding-top: 20px;">
          <div style="width: 130px;height: 100%;" class="layout-left-center">
            <div style="width: 130px;height: 164px;" :style="{background:'url('+avatarBg+')'}">
              <div class="w-full" style="height: 100px;padding: 0 25px">
                <img v-real-img="fileUrl+userInfo.userImg"
                     :src="avatarDef">
              </div>
              <div class="w-full pt-2 color1"
                   style="height: calc(100% - 100px);">
                <div class="w-full layout-center">士字第</div>
                <div class="w-full layout-center">{{ userInfo.wkno }}</div>
              </div>
            </div>
          </div>
          <div style="width: calc(100% - 130px);height: 100%;padding-left: 12px">
            <div style="width: 100%;height: 40px;" class="layout-left-center">
              <div class="bgItem" style="width: 5px;height: 5px;"></div>
              <span class="color1" style="margin-left: 10px;">姓名：</span>
              <span style="width:calc(100% - 80px)" class="emailClass color2" :title="userInfo.userName">{{userInfo.userName}}</span>
            </div>
            <div style="width: 100%;height: 40px;" class="layout-left-center">
              <div class="bgItem" style="width: 5px;height: 5px;"></div>
              <span  class="color1" style="margin-left: 10px;">用户名：</span>
              <span style="width:calc(100% - 100px)" class="emailClass color2" :title="userInfo.userAccount">{{userInfo.userAccount}}</span>
            </div>
            <div style="width: 100%;height: 40px;" class="layout-left-center">
              <div class="bgItem" style="width: 5px;height: 5px;"></div>
              <span class="color1" style="margin-left: 10px;">入伍时间：</span>
              <span style="width:calc(100% - 120px)" class="emailClass color2" :title="totalData.userEntity.eday">{{totalData.userEntity.eday}}</span>
            </div>
            <div style="width: 100%;height: 40px;" class="layout-left-center">
              <div class="bgItem" style="width: 5px;height: 5px;"></div>
              <span class="color1" style="margin-left: 10px;">电话：</span>
              <span style="width:calc(100% - 80px)" class="emailClass color2"
                    :title="totalData.userEntity.phone?totalData.userEntity.phone:'暂无'">{{totalData.userEntity.phone?totalData.userEntity.phone:'暂无'}}</span>
            </div>
          </div>
        </div>
        <!--左上E-->
        <!--左中S-->
        <div class="w-full" style="height: 320px;padding-left: 50px;">
          <div style="width: 295px;height: 68px;" class="xiaoBian xiaoBian1">
            <img :src="kaoshi" alt="" class="img">
            <div class="text1 fs_dispose_min">参加考试</div>
            <div class="text2">{{totalData.examNum}}场</div>
          </div>
          <div style="width: 295px;height: 68px;margin-top: 16px;" class="xiaoBian xiaoBian1">
            <img :src="jige" alt="" class="img">
            <div class="text1 fs_dispose_min">及格次数</div>
            <div class="text2">{{totalData.passNum}}次</div>
          </div>
          <div style="width: 295px;height: 68px;margin-top: 16px;" class="xiaoBian xiaoBian2">
            <img :src="yiXueKeJian" alt="" class="img">
            <div class="text1 fs_dispose_min">已学课件</div>
            <div class="text2">{{totalData.swfNum}}个</div>
          </div>
          <div style="width: 295px;height: 68px;margin-top: 16px;" class="xiaoBian xiaoBian2">
            <img :src="xueXiShiChang" alt="" class="img">
            <div class="text1 fs_dispose_min">学习时长</div>
            <div class="text2">{{totalData.studyTime}}小时</div>
          </div>
        </div>
        <!--左中E-->
        <!--左下S-->
        <div class="w-full" style="height: calc(100% - 520px);padding-left: 50px;">
          <div style="height: 20px;"></div>
          <!--切换标签-->
          <div style="height: 39px;width: 295px;" class="layout-center">
            <div style="height: 100%;width: 236px;" class="tabBacImg layout-center">
              <span class="fs_dispose">易错题</span>
            </div>
          </div>
          <!--文字-->
          <div style="height: 10px;"></div>
          <div
            style="height: 35px;width: 295px;border-bottom: 1px dashed #354971;color: #b4d5f0;"
            v-for=" (item,index) in totalData.errorTopic"
            :key="index"
            class="layout-left-center"
          >
            <span
              style="overflow: hidden;white-space: nowrap;text-overflow: ellipsis;"
              :title="item"
            >{{(index+1)+'、'+item}}</span>
          </div>
        </div>
        <!--左下E-->
      </div>
      <!--大左E-->
      <!--大右S-->
      <div class="h-full chartRBoxs" style="width: calc(100% - 400px);padding: 20px 40px 20px 0">
        <div class="w-full h-full">
          <!--右上S-->
          <div style="width: 100%;height: calc(50% - 10px);position: relative;" class="layout-side">
            <!--右上边框S-->
            <div class="h-full bianKuangZuo" style="width: 40px;"></div>
            <div class="h-full bianKuangYou" style="width: 40px;"></div>
            <div style="position: absolute;top: 0;left: 50%;transform: translateX(-50%);width:100%;height: 37px;">
              <img
                style="width: 100%;height: 100%;"
                :src="biaoTi"
                alt=""
              >
              <div style="width: 40%;max-width: 500px;min-width:380px;height: 37px;position: absolute;top: 0;left: 50%;transform: translateX(-50%);">
                <div class="w-full h-full layout-side" style="color: #6ebdff;">
                  <div style="height: 100%;" class="layout-left-center">
                    <div style="height: 100%;width: 17px;" class="layout-center">
                      <div style="width: 17px;height: 12px;">
                        <img
                          :src="jiantou" alt=""
                          style="height: 100%;width: 100%;"
                        >
                      </div>
                    </div>
                    <span :style="{fontSize: (15 + fs * 2)+'px'}" style="font-weight: bold;margin-left: 10px;">理论学习</span>
                  </div>
                  <div style="height: 100%;" class="layout-left-center">
                    <div style="height: 100%;width: 17px;" class="layout-center">
                      <div style="width: 17px;height: 12px;">
                        <img
                          :src="jiantou" alt=""
                          style="height: 100%;width: 100%;"
                        >
                      </div>
                    </div>
                    <span style="margin-left: 10px;">学习总时长：</span>
                    <span>{{totalData.theoryTime}}小时</span>
                    <span style="margin-left: 30px;">已得总学分：</span>
                    <span>{{totalData.totalCredit}}分</span>
                  </div>
                </div>
              </div>
            </div>
            <!--右上边框E-->
            <!--内容S-->
            <div class="w-full h-full layout-center" style="position: absolute;top:0;left: 0;">
              <div style="width: calc(100% - 50px);height: calc(100% - 50px);margin-top: 50px;">
                <div style="width: 100%;height: 50px;" class="layout-right-center">
                  <a-select
                    v-model:value="currentYear1"
                    style="width: 120px;"
                    @change="handleYearChange1"
                  >
                    <a-select-option
                      v-for="(item,index) in selectYearOptions"
                      :key="index"
                      :value="item.value"
                    >
                      {{item.label}}
                    </a-select-option>
                  </a-select>
                </div>
                <div class="w-full  layout-side" style="height: calc(100% - 50px);">
                  <div style="width: 450px;height: 191px;margin-top: 50px;" class="layout-side charBox">
                    <div class="charItem" style="width: 127px;height: 100%;position: relative;">
                      <img :src="jichulilunPic"
                           alt="">
                      <span :style="{fontSize: (15 + fs)+'px'}"
                            style="position: absolute;left: 40%;top:49px;font-weight: bold;transform: translateX(-40%);">基础理论</span>
                      <span class="emailClass2" :style="{fontSize: (18 + fs * 2)+'px'}"
                            style="position: absolute;left: 50%;transform: translateX(-50%);top:4px;font-weight: bold;">{{upData.one}}分</span>
                    </div>
                    <div class="charItem" style="width: 127px;height: 100%;position: relative;">
                      <img :src="jizhangzhuangbeiPic"
                           alt="">
                      <span :style="{fontSize: (15 + fs)+'px'}"
                            style="position: absolute;left: 40%;top:49px;font-weight: bold;transform: translateX(-40%);">职掌装备</span>
                      <span class="emailClass2" :style="{fontSize: (18 + fs * 2)+'px'}"
                            style="position: absolute;left: 50%;transform: translateX(-50%);top:4px;font-weight: bold;">{{upData.two}}分</span>
                    </div>
                    <div class="charItem" style="width: 127px;height: 100%;position: relative;">
                      <img :src="zhixingyewuPic"
                           alt="">
                      <span :style="{fontSize: (15 + fs)+'px'}"
                            style="position: absolute;left: 40%;top:49px;font-weight: bold;transform: translateX(-40%);">值勤业务</span>
                      <span class=" emailClass2" :style="{fontSize: (18 + fs * 2)+'px'}"
                            style="position: absolute;left: 50%;transform: translateX(-50%);top:4px;font-weight: bold;">{{upData.three}}分</span>
                    </div>
                  </div>
                  <div style="width:calc(100% - 450px);height: 100%;padding-left: 10px;padding-top: 20px;" class="charBoxR">
                    <div style="width: 100%;height: 100%;" id="containerTt"></div>
                  </div>
                </div>
              </div>
            </div>
            <!--内容E-->
          </div>
          <!--右上E-->
          <div style="width: 100%;height: 20px;"></div>
          <!--右下S-->
          <div style="width: 100%;height: calc(50% - 10px);position: relative;" class="layout-side">
            <!--右下边框S-->
            <div class="h-full bianKuangZuo" style="width: 40px;"></div>
            <div class="h-full bianKuangYou" style="width: 40px;"></div>
            <div style="position: absolute;top: 0;left: 50%;transform: translateX(-50%);width:100%;height: 37px;">
              <img
                style="width: 100%;height: 100%;"
                :src="biaoTi"
                alt=""
              >
              <div style="width: 40%;max-width: 530px;min-width:420px;height: 37px;position: absolute;left: 50%;top:0;transform: translateX(-48%);">
                <div class="w-full h-full layout-side" style="color: #6ebdff;">
                  <div style="height: 100%;" class="layout-left-center">
                    <div style="height: 100%;width: 17px;" class="layout-center">
                      <div style="width: 17px;height: 12px;">
                        <img
                          :src="jiantou" alt=""
                          style="height: 100%;width: 100%;"
                        >
                      </div>
                    </div>
                    <span :style="{fontSize: (16 + fs)+'px'}" style="font-weight: bold;margin-left: 10px;">理论测试</span>
                  </div>
                  <div style="height: 100%;" class="layout-left-center">
                    <div style="height: 100%;width: 17px;" class="layout-center">
                      <div style="width: 17px;height: 12px;">
                        <img
                          :src="jiantou" alt=""
                          style="height: 100%;width: 100%;"
                        >
                      </div>
                    </div>
                    <span style="margin-left: 10px;">最高分：</span>
                    <span>{{totalData.theoryTestMaxCredit}}分</span>
                    <span style="margin-left: 20px;">最低分：</span>
                    <span>{{totalData.theoryTestMinCredit}}分</span>
                    <span style="margin-left: 20px;">平均分：</span>
                    <span>{{Number(totalData.theoryTestAvgCredit).toFixed(2)}}分</span>
                  </div>
                </div>
              </div>
            </div>
            <!--右下边框E-->
            <!--内容S-->
            <div class="w-full h-full layout-center" style="position: absolute;top:0;left: 0;">
              <div style="width: calc(100% - 50px);height: calc(100% - 50px);margin-top: 50px;">
                <div style="width: 100%;height: 34px;" class="layout-right-center">
                  <a-select
                    v-model:value="currentYear2"
                    style="width: 120px;"
                    @change="handleYearChange2"
                  >
                    <a-select-option
                      v-for="(item,index) in selectYearOptions"
                      :key="index"
                      :value="item.value"
                    >
                      {{item.label}}
                    </a-select-option>
                  </a-select>
                </div>
                <div class="w-full" style="height: calc(100% - 40px);">
                  <!--主题S-->
                  <div style="width: 100%;height: 100%;" id="pointContainer"></div>
                  <!--主题E-->
                </div>
              </div>
              <!--内容E-->
            </div>
          </div>
          <!--右下E-->
        </div>
      </div>
      <!--大右E-->
    </div>
    <div class="w-full h-full layout-side" style="position: absolute; left: 0; top: 0" v-else>
      <!--大左S-->
      <div class="h-full" style="width: 400px">
        <!--左上S-->
        <div class="w-full layout-side" style="height: 200px; padding-left: 12px; padding-top: 20px">
          <div style="width: 40%; height: 100%" class="layout-left-center">
            <div style="width: 130px; height: 164px" :style="{ background: 'url(' + avatarBg + ')' }">
              <div class="w-full" style="height: 100px; padding: 0 25px">
                <img v-real-img="fileUrl + userInfo.userImg" :src="avatarDef" />
              </div>
              <div class="w-full pt-2 color1" style="height: calc(100% - 100px); font-size: 15px">
                <div class="w-full layout-center" style="font-size: 14px">士字第</div>
                <div class="w-full layout-center">{{ userInfo.wkno }}</div>
              </div>
            </div>
          </div>
          <div style="width: 60%; height: 100%">
            <div style="width: 100%; height: 40px" class="layout-left-center">
              <div class="bgItem" style="width: 5px; height: 5px;"></div>
              <span class="color1" style="margin-left: 10px">姓名：</span>
              <span class="emailClass color2" :title="userInfo.userName">{{ userInfo.userName }}</span>
            </div>
            <div style="width: 100%; height: 40px" class="layout-left-center">
              <div class="bgItem" style="width: 5px; height: 5px;"></div>
              <span class="color1" style=" margin-left: 10px">用户名：</span>
              <span class="emailClass color2" :title="userInfo.userAccount">{{ userInfo.userAccount }}</span>
            </div>
            <div style="width: 100%; height: 40px" class="layout-left-center">
              <div class="bgItem" style="width: 5px; height: 5px; "></div>
              <span class="color1" style=" margin-left: 10px">入伍时间：</span>
              <span class="emailClass color2" :title="totalData.userEntity.eday">{{ totalData.userEntity.eday }}</span>
            </div>
            <div style="width: 100%; height: 40px" class="layout-left-center">
              <div class="bgItem" style="width: 5px; height: 5px;"></div>
              <span class="color1" style="margin-left: 10px">电话：</span>
              <span  class="emailClass color2" :title="totalData.userEntity.phone ? totalData.userEntity.phone : '暂无'">{{ totalData.userEntity.phone ? totalData.userEntity.phone : '暂无' }}</span>
            </div>
          </div>
        </div>
        <!--左上E-->
        <!--左中S-->
        <div class="w-full" style="height: 343px; padding-left: 12px; padding-right: 12px">
          <div style="width: 100%; height: 82px" class="xiaoBian xiaoBian1">
            <img :src="kaoshi" alt="" class="img" />
            <div class="text1">参加考试</div>
            <div class="text2">{{ totalData.examNum }}场</div>
          </div>
          <div style="width: 100%; height: 82px; margin-top: 5px" class="xiaoBian xiaoBian1">
            <img :src="jige" alt="" class="img" />
            <div class="text1">及格次数</div>
            <div class="text2">{{ totalData.passNum }}次</div>
          </div>
          <div style="width: 100%; height: 82px; margin-top: 5px" class="xiaoBian xiaoBian2">
            <img :src="yiXueKeJian" alt="" class="img" />
            <div class="text1">已学课件</div>
            <div class="text2">{{ totalData.swfNum }}个</div>
          </div>
          <div style="width: 100%; height: 82px; margin-top: 5px" class="xiaoBian xiaoBian2">
            <img :src="xueXiShiChang" alt="" class="img" />
            <div class="text1">学习时长</div>
            <div class="text2">{{ totalData.studyTime }}小时</div>
          </div>
        </div>
        <!--左中E-->
        <!--左下S-->
        <div class="w-full" style="height: calc(100% - 543px); padding-left: 12px; padding-right: 12px">
          <div style="height: 20px"></div>
          <!--切换标签-->
          <div style="height: 39px; width: 100%" class="layout-center">
            <div style="height: 100%; width: 100%" class="tabBacImg">
              <span>易错题</span>
            </div>
          </div>
          <!--文字-->
          <div style="height: 10px"></div>
          <div style="height: 35px; width: 100%; color: #bfcde0; background: #1b2836; margin-bottom: 2px" v-for="(item, index) in totalData.errorTopic" :key="index" class="layout-left-center">
            <span style="overflow: hidden; white-space: nowrap; text-overflow: ellipsis; padding-left: 20px" :title="item">{{ index + 1 + '&nbsp;&nbsp;&nbsp;&nbsp;' + item }}</span>
          </div>
        </div>
        <!--左下E-->
      </div>
      <!--大左E-->
      <!--大右S-->
      <div class="h-full" style="width: calc(100% - 400px); padding: 20px 40px 20px 0">
        <div class="w-full h-full">
          <!--右上S-->
          <div style="width: 100%; height: calc(50% - 10px); position: relative" class="layout-side boxBorder">
            <!--右上边框S-->
            <!--            <div class="h-full bianKuangZuo" style="width: 40px;"></div>-->
            <!--            <div class="h-full bianKuangYou" style="width: 40px;"></div>-->
            <div style="position: absolute; top: 0; left: 50%; transform: translateX(-50%); width: 100%; height: 37px">
              <div style="width: 100%; height: 37px; position: absolute; top: 0; left: 50%; transform: translateX(-50%); padding-left: 20px; padding-top: 12px">
                <div class="w-full h-full" style="display: flex; justify-content: space-between">
                  <div class="layout-left-center chartTitle">
                    <span>理论学习</span>
                  </div>
                  <div style="height: 100%" class="layout-left-center">
                    <div class="itemBox">
                      <span style="margin-left: 10px">学习总时长：</span>
                      <span style="color: #e9deb2; font-size: 16px; font-weight: bold; padding: 0 10px">{{ totalData.theoryTime }}</span>
                      <span>小时</span>
                    </div>
                    <div class="itemBox">
                      <span style="margin-left: 30px">已得总学分：</span>
                      <span style="color: #e9deb2; font-size: 16px; font-weight: bold; padding: 0 10px">{{ totalData.totalCredit }}</span>
                      <span>分</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!--右上边框E-->
            <!--内容S-->
            <div class="w-full h-full layout-center" style="position: absolute; top: 0; left: 0">
              <div style="width: calc(100% - 40px); height: calc(100% - 50px); margin-top: 50px">
                <div style="width: 100%; height: 50px" class="layout-right-center">
                  <a-select v-model:value="currentYear1" style="width: 120px" @change="handleYearChange1">
                    <a-select-option v-for="(item, index) in selectYearOptions" :key="index" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </div>
                <div class="w-full layout-side" style="height: calc(100% - 50px)">
                  <div style="width: 320px; height: 191px; margin-top: 50px" class="layout-center">
                    <div style="width: 127px; height: 100%; position: relative">
                      <img :src="jichulilunPic" alt="" />
                      <span style="position: absolute; left: 50%; top: 49px; font-size: 15px; font-weight: bold; transform: translateX(-50%)">基础理论</span>
                      <span style="position: absolute; left: 50%; transform: translateX(-50%); top: 4px; font-size: 19px; font-weight: bold" class="emailClass2">{{ upData.one }}分</span>
                    </div>
                    <!--                    <div style="width: 127px; height: 100%; position: relative">-->
                    <!--                      <img :src="jizhangzhuangbeiPic" alt="" />-->
                    <!--                      <span style="position: absolute; left: 50%; top: 49px; font-size: 15px; font-weight: bold; transform: translateX(-50%)">值掌装备</span>-->
                    <!--                      <span style="position: absolute; left: 50%; transform: translateX(-50%); top: 4px; font-size: 19px; font-weight: bold" class="emailClass2">{{ upData.two }}分</span>-->
                    <!--                    </div>-->
                    <!--                    <div style="width: 127px; height: 100%; position: relative">-->
                    <!--                      <img :src="zhixingyewuPic" alt="" />-->
                    <!--                      <span style="position: absolute; left: 50%; top: 49px; font-size: 15px; font-weight: bold; transform: translateX(-50%)">值勤业务</span>-->
                    <!--                      <span style="position: absolute; left: 50%; transform: translateX(-50%); top: 4px; font-size: 19px; font-weight: bold" class="emailClass2">{{ upData.three }}分</span>-->
                    <!--                    </div>-->
                  </div>
                  <div style="width: calc(100% - 320px); height: 100%; padding-left: 10px; padding-top: 20px">
                    <div style="width: 100%; height: 100%" id="containerTt"></div>
                  </div>
                </div>
              </div>
            </div>
            <!--内容E-->
          </div>
          <!--右上E-->
          <div style="width: 100%; height: 20px"></div>
          <!--右下S-->
          <div style="width: 100%; height: calc(50% - 10px); position: relative" class="layout-side boxBorder">
            <!--右下边框S-->
            <!--            <div class="h-full bianKuangZuo" style="width: 40px;"></div>-->
            <!--            <div class="h-full bianKuangYou" style="width: 40px;"></div>-->
            <div style="position: absolute; top: 0; left: 50%; transform: translateX(-50%); width: 100%; height: 37px">
              <div style="width: 100%; height: 37px; position: absolute; top: 0; left: 50%; transform: translateX(-50%); padding-left: 20px; padding-top: 12px">
                <div class="w-full h-full layout-side" style="color: #6ebdff">
                  <div class="layout-left-center chartTitle">
                    <span>理论测试</span>
                  </div>
                  <div style="height: 100%" class="layout-left-center">
                    <div class="itemBox">
                      <span style="margin-left: 10px">最高分：</span>
                      <span style="color: #e9deb2; font-size: 16px; font-weight: bold; padding: 0 10px">{{ totalData.theoryTestMaxCredit }}</span>
                      <span>分</span>
                    </div>
                    <div class="itemBox">
                      <span style="margin-left: 30px">最低分：</span>
                      <span style="color: #e9deb2; font-size: 16px; font-weight: bold; padding: 0 10px">{{ totalData.theoryTestMinCredit }}</span>
                      <span>分</span>
                    </div>
                    <div class="itemBox">
                      <span style="margin-left: 30px">平均分：</span>
                      <span style="color: #e9deb2; font-size: 16px; font-weight: bold; padding: 0 10px">{{ Number(totalData.theoryTestAvgCredit).toFixed(2) }}</span>
                      <span>分</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!--右下边框E-->
            <!--内容S-->
            <div class="w-full h-full layout-center" style="position: absolute; top: 0; left: 0">
              <div style="width: calc(100% - 40px); height: calc(100% - 50px); margin-top: 50px">
                <div style="width: 100%; height: 50px" class="layout-right-center">
                  <a-select v-model:value="currentYear2" style="width: 120px" @change="handleYearChange2">
                    <a-select-option v-for="(item, index) in selectYearOptions" :key="index" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </div>
                <div class="w-full" style="height: calc(100% - 50px)">
                  <!--主题S-->
                  <div style="width: 100%; height: 100%" id="pointContainer"></div>
                  <!--主题E-->
                </div>
              </div>
              <!--内容E-->
            </div>
          </div>
          <!--右下E-->
        </div>
      </div>
      <!--大右E-->
    </div>

  </div>
</template>

<script>
  export default {
    name: 'ComprehensiveAnalyze'
  }
</script>
<script setup>

  import avatarDef from '../../../../../assets/HJ/main/avatar-def.png'
  import jichulilunPic from '../../../../../assets/HJ/basicTheory/studyManage/analyze/lilunxuexi.png'
  import jizhangzhuangbeiPic from '../../../../../assets/HJ/basicTheory/studyManage/analyze/zhizhangzhuangbei.png'
  import zhixingyewuPic from '../../../../../assets/HJ/basicTheory/studyManage/analyze/zhiqingyewu.png'
  import biaoTi from '../../../../../assets/HJ/basicTheory/studyManage/analyze/biaoTi.png'
  import jiantou from '../../../../../assets/HJ/basicTheory/studyManage/analyze/jiantou.png'

  import avatarBgHJ from '../../../../../assets/HJ/main/avatar-bg.png'
  import kaoshiHJ from '../../../../../assets/HJ/basicTheory/studyManage/analyze/kaoshi.png'
  import jigeHJ from '../../../../../assets/HJ/basicTheory/studyManage/analyze/ceyan.png'
  import yiXueKeJianHJ from '../../../../../assets/HJ/basicTheory/studyManage/analyze/kejian.png'
  import xueXiShiChangHJ from '../../../../../assets/HJ/basicTheory/studyManage/analyze/shichang.png'

  import avatarBgHJJ from '../../../../../assets/HJJ/main/avatar-bg.png'
  import kaoshiHJJ from '../../../../../assets/HJJ/basicTheory/studyManage/analyze/kaoshi.png'
  import jigeHJJ from '../../../../../assets/HJJ/basicTheory/studyManage/analyze/ceyan.png'
  import yiXueKeJianHJJ from '../../../../../assets/HJJ/basicTheory/studyManage/analyze/kejian.png'
  import xueXiShiChangHJJ from '../../../../../assets/HJJ/basicTheory/studyManage/analyze/shichang.png'

  import avatarBgLJ from '../../../../../assets/LJ/main/avatar-bg.png'
  import kaoshiLJ from '../../../../../assets/LJ/basicTheory/studyManage/analyze/kaoshi.png'
  import jigeLJ from '../../../../../assets/LJ/basicTheory/studyManage/analyze/ceyan.png'
  import yiXueKeJianLJ from '../../../../../assets/LJ/basicTheory/studyManage/analyze/kejian.png'
  import xueXiShiChangLJ from '../../../../../assets/LJ/basicTheory/studyManage/analyze/shichang.png'
  import { onMounted, ref, onUnmounted } from 'vue'
  import useChart2 from './js/useChart2.js'
  import pointUseChart from './js/pointUseChart'
  import totalApi from './js/totalApi.js'
  import useYearChange from './js/yearChange.js'
  const avatarBg = ref(avatarBgHJ)
  const kaoshi = ref(kaoshiHJ)
  const jige = ref(jigeHJ)
  const yiXueKeJian = ref(yiXueKeJianHJ)
  const xueXiShiChang = ref(xueXiShiChangHJ)

  const fileUrl = window.fileUrl
  let currentYear1 = ref('')
  let currentYear2 = ref('')
  const interfaceStyle = window.interfaceStyle
  if(interfaceStyle==='HJJ'){
    avatarBg.value =avatarBgHJJ
    kaoshi.value =kaoshiHJJ
    jige.value =jigeHJJ
    yiXueKeJian.value =yiXueKeJianHJJ
    xueXiShiChang.value =xueXiShiChangHJJ
  }else if(interfaceStyle==='LJ'){
    avatarBg.value =avatarBgLJ
    kaoshi.value =kaoshiLJ
    jige.value =jigeLJ
    yiXueKeJian.value =yiXueKeJianLJ
    xueXiShiChang.value =xueXiShiChangLJ
  }
  const fs = ref(JSON.parse(localStorage.getItem('fs')));
  let userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
  let { getChartDataSource2, theChart2Destroy, upData } = useChart2(currentYear1)
  let { pointGetChartDataSource } = pointUseChart(currentYear2)
  let { handleYearChange1, handleYearChange2, selectYearOptions } = useYearChange(getChartDataSource2, currentYear1, currentYear2, pointGetChartDataSource)
  let { totalData, getTotal } = totalApi()
  onMounted(() => {
    getTotal()
    getChartDataSource2()
    pointGetChartDataSource()
  })
  onUnmounted(() => {
    theChart2Destroy()
  })
</script>

<style lang="less">
  .HJ{
    .bgItem{background-color: #2f5d8e;}
    .color1{color:#a5b6d0 }
    .color1{color:#b4d5f0 }
    @blueColor: #6ebdff;
    @grayColor: #a5b6d0;
    @backgroundcolor: rgba(24, 45, 86, 0.7);
    @whiteColor: #e2f2ff;
    @defaultTextColor: #b4d5f0;
    .analyze {

      .emailClass2 {
        max-width: 100px;
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      .emailClass {
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      // 背景
      .zuo {
        background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/zuo.png");
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .you {
        background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/you.png");
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;
      }

      .bianKuangZuo {
        background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/bianKuangZuo.png");
        background-repeat: no-repeat;
        background-size: 40px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .bianKuangYou {
        background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/bianKuangYou.png");
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 40px 100%;
        // 左上角为起点
        background-position: right top;
      }

      // 参加考试
      .xiaoBian {
        position: relative;
        background-repeat: no-repeat;
        background-size: 100% 100%;
        background-position: left top;

        .img {
          position: absolute;
          left: 39px;
          top: 24px;
        }

        .text1 {
          position: absolute;
          left: 140px;
          top: 8px;
          color: #a5b6d0;
        }
      }

      .xiaoBian1 {
        background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/xiaoLan.png");

        .text2 {
          position: absolute;
          left: 140px;
          top: 28px;
          color: #6ebdff;
          font-size: 22px;
          font-weight: bolder;
        }
      }

      .xiaoBian2 {
        background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/xiaoCheng.png");

        .text2 {
          position: absolute;
          left: 140px;
          top: 28px;
          color: #fdb242;
          font-size: 22px;
          font-weight: bolder;
        }
      }

      // 错题切换
      .tabBacImg {
        background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/yicuoti.png");
        background-repeat: no-repeat;
        background-size: 100% 100%;
        background-position: left top;
        position: relative;

        span {
          color: @blueColor;
          font-size: 15px;
          font-weight: bold;
          margin-top: 5px;
        }

        .textColor {
          color: #6ebdff;
        }

        span:nth-child(1) {
          left: 30px;
        }

        span:nth-child(2) {
          left: 145px;
        }
      }


    }

    .boxBorderShadow {
      border: 1px solid transparent;
      box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
    }

    .innerBackgroundColor {
      background-color: @backgroundcolor;
    }
  }
  .HJJ{
    .bgItem{background-color: #2f5d8e;}
    .color1{color:#a5b6d0 }
    .color1{color:#b4d5f0 }
    @blueColor: #bfcde0;
    @grayColor: #6e7481;
    @backgroundcolor: rgba(23, 31, 41, 0.7);
    @whiteColor: #e2f2ff;
    @defaultTextColor: #b4d5f0;

    .boxBorder {
      border: 1px solid #364555;
      position: relative;
    }
    .boxBorder:before {
      position: absolute;
      content: '';
      background-image: url('../../../../../assets/HJJ/postTrain/leftTop.png'), url('../../../../../assets/HJJ/postTrain/leftBottom.png');
      background-repeat: no-repeat;
      background-position: left top, left bottom;
      height: calc(100% + 4px);
      width: 12px;
      left: -2px;
      top: -2px;
    }
    .boxBorder:after {
      position: absolute;
      content: '';
      background-image: url('../../../../../assets/HJJ/postTrain/rightTop.png'), url('../../../../../assets/HJJ/postTrain/rightBottom.png');
      background-repeat: no-repeat;
      background-position: right top, right bottom;
      height: calc(100% + 4px);
      width: 12px;
      right: -2px;
      top: -2px;
    }
    .itemBox {
      background: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/itemBox.png') no-repeat;
      width: 193px;
      height: 28px;
      margin-right: 20px;
      line-height: 28px;
      color: #7d8899;
      font-size: 13px;
    }
    .chartTitle {
      background: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/yicuoti.png') no-repeat;
      width: 321px;
      height: 34px;
      padding-left: 30px;
      color: #bfcde0;
      font-size: 18px;
    }
    .analyze {
      .emailClass2 {
        max-width: 100px;
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      .emailClass {
        max-width: 100px;
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      // 背景
      .zuo {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/zuo.png');
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .you {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/you.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;
      }

      .bianKuangZuo {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/bianKuangZuo.png');
        background-repeat: no-repeat;
        background-size: 40px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .bianKuangYou {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/bianKuangYou.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 40px 100%;
        // 左上角为起点
        background-position: right top;
      }

      // 参加考试
      .xiaoBian {
        position: relative;
        background-repeat: no-repeat;
        background-size: 100% 100%;
        background-position: left top;

        .img {
          position: absolute;
          left: 39px;
          top: 5px;
        }

        .text1 {
          position: absolute;
          left: 140px;
          top: 18px;
          color: #7d8899;
          font-size: 13px;
        }
      }

      .xiaoBian1 {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/xiaoLan.png');
        .text2 {
          position: absolute;
          left: 140px;
          top: 35px;
          color: #bfcde0;
          font-size: 20px;
          font-weight: bolder;
        }
      }
      .xiaoBian2 {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/xiaoLan.png');
        .text2 {
          position: absolute;
          left: 140px;
          top: 35px;
          color: #bfcde0;
          font-size: 20px;
          font-weight: bolder;
        }
      }

      // 错题切换
      .tabBacImg {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/yicuoti.png');
        background-repeat: no-repeat;
        background-size: 100% 100%;
        position: relative;
        line-height: 39px;
        padding-left: 30px;
        span {
          color: @blueColor;
          font-size: 18px;
          margin-top: 5px;
        }

        .textColor {
          color: #6ebdff;
        }

        span:nth-child(1) {
          left: 30px;
        }

        span:nth-child(2) {
          left: 145px;
        }
      }
    }

    .boxBorderShadow {
      border: 1px solid transparent;
      box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
    }

    .innerBackgroundColor {
      background-color: @backgroundcolor;
    }
  }
  .LJ{
    .bgItem{background-color: #a9abaa;}
    .color1{color:#a9abaa }
    .color1{color:#ffffff }
    @blueColor: #fff;
    @grayColor: #a9abaa;
    @backgroundcolor: rgba(38,41,36,0.3);
    @whiteColor: #ffffff;
    @defaultTextColor: #b4d5f0;

    .boxBorder {
      border: 1px solid #363a39;
      position: relative;
    }
    .boxBorder:before {
      position: absolute;
      content: '';
      background-image: url('../../../../../assets/LJ/postTrain/leftTop.png'), url('../../../../../assets/LJ/postTrain/leftBottom.png');
      background-repeat: no-repeat;
      background-position: left top, left bottom;
      height: calc(100% + 4px);
      width: 12px;
      left: -2px;
      top: -2px;
    }
    .boxBorder:after {
      position: absolute;
      content: '';
      background-image: url('../../../../../assets/LJ/postTrain/rightTop.png'), url('../../../../../assets/LJ/postTrain/rightBottom.png');
      background-repeat: no-repeat;
      background-position: right top, right bottom;
      height: calc(100% + 4px);
      width: 12px;
      right: -2px;
      top: -2px;
    }
    .itemBox {
      // background: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/itemBox.png') no-repeat;
      width: 193px;
      height: 28px;
      margin-right: 20px;
      line-height: 28px;
      color: #a9abaa;
      font-size: 13px;
    }
    .chartTitle {
      background: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/yicuoti.png') no-repeat;
      width: 321px;
      height: 34px;
      padding-left: 30px;
      color: #fff;
      font-size: 18px;
    }
    .analyze {
      .emailClass2 {
        max-width: 100px;
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      .emailClass {
        max-width: 100px;
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      // 背景
      .zuo {
        background-image: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/zuo.png');
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .you {
        background-image: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/you.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;
      }

      .bianKuangZuo {
        background-image: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/bianKuangZuo.png');
        background-repeat: no-repeat;
        background-size: 40px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .bianKuangYou {
        background-image: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/bianKuangYou.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 40px 100%;
        // 左上角为起点
        background-position: right top;
      }

      // 参加考试
      .xiaoBian {
        position: relative;
        background-repeat: no-repeat;
        background-size: 100% 100%;
        background-position: left top;

        .img {
          position: absolute;
          left: 39px;
          top: 5px;
        }

        .text1 {
          position: absolute;
          left: 140px;
          top: 18px;
          color: #a9abaa;
          font-size: 13px;
        }
      }

      .xiaoBian1 {
        background-image: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/xiaoLan.png');
        .text2 {
          position: absolute;
          left: 140px;
          top: 35px;
          color: #fff;
          font-size: 20px;
          font-weight: bolder;
        }
      }
      .xiaoBian2 {
        background-image: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/xiaoLan.png');
        .text2 {
          position: absolute;
          left: 140px;
          top: 35px;
          color: #fff;
          font-size: 20px;
          font-weight: bolder;
        }
      }

      // 错题切换
      .tabBacImg {
        background-image: url('../../../../../assets/LJ/basicTheory/studyManage/analyze/yicuoti.png');
        background-repeat: no-repeat;
        background-size: 100% 100%;
        position: relative;
        line-height: 39px;
        padding-left: 30px;
        span {
          color: @blueColor;
          font-size: 18px;
          margin-top: 5px;
        }

        .textColor {
          color: #34b34c;
        }

        span:nth-child(1) {
          left: 30px;
        }

        span:nth-child(2) {
          left: 145px;
        }
      }
    }

    .tabItem:hover {
      background: #343c39 !important;
    }
    .boxBorderShadow {
      border: 1px solid transparent;
      /*box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);*/
    }
  }
  .KJ{
    .bgItem{background-color: #a9abaa;}
    .color1{color:#a9abaa }
    .color1{color:#ffffff }
    @blueColor: #fff;
    @grayColor: #a9abaa;
    @backgroundcolor: rgba(38,41,36,0.3);
    @whiteColor: #ffffff;
    @defaultTextColor: #b4d5f0;

    .boxBorder {
      border: 1px solid #363a39;
      position: relative;
    }
    .boxBorder:before {
      position: absolute;
      content: '';
      background-image: url('../../../../../assets/KJ/postTrain/leftTop.png'), url('../../../../../assets/KJ/postTrain/leftBottom.png');
      background-repeat: no-repeat;
      background-position: left top, left bottom;
      height: calc(100% + 4px);
      width: 12px;
      left: -2px;
      top: -2px;
    }
    .boxBorder:after {
      position: absolute;
      content: '';
      background-image: url('../../../../../assets/KJ/postTrain/rightTop.png'), url('../../../../../assets/KJ/postTrain/rightBottom.png');
      background-repeat: no-repeat;
      background-position: right top, right bottom;
      height: calc(100% + 4px);
      width: 12px;
      right: -2px;
      top: -2px;
    }
    .itemBox {
      // background: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/itemBox.png') no-repeat;
      width: 193px;
      height: 28px;
      margin-right: 20px;
      line-height: 28px;
      color: #a9abaa;
      font-size: 13px;
    }
    .chartTitle {
      background: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/yicuoti.png') no-repeat;
      width: 321px;
      height: 34px;
      padding-left: 30px;
      color: #fff;
      font-size: 18px;
    }
    .analyze {
      .emailClass2 {
        max-width: 100px;
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      .emailClass {
        max-width: 100px;
        overflow: hidden;
        display: inline-block;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      // 背景
      .zuo {
        background-image: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/zuo.png');
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .you {
        background-image: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/you.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;
      }

      .bianKuangZuo {
        background-image: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/bianKuangZuo.png');
        background-repeat: no-repeat;
        background-size: 40px 100%;
        // 左上角为起点
        background-position: left top;
      }

      .bianKuangYou {
        background-image: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/bianKuangYou.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 40px 100%;
        // 左上角为起点
        background-position: right top;
      }

      // 参加考试
      .xiaoBian {
        position: relative;
        background-repeat: no-repeat;
        background-size: 100% 100%;
        background-position: left top;

        .img {
          position: absolute;
          left: 39px;
          top: 5px;
        }

        .text1 {
          position: absolute;
          left: 140px;
          top: 18px;
          color: #a9abaa;
          font-size: 13px;
        }
      }

      .xiaoBian1 {
        background-image: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/xiaoLan.png');
        .text2 {
          position: absolute;
          left: 140px;
          top: 35px;
          color: #fff;
          font-size: 20px;
          font-weight: bolder;
        }
      }
      .xiaoBian2 {
        background-image: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/xiaoLan.png');
        .text2 {
          position: absolute;
          left: 140px;
          top: 35px;
          color: #fff;
          font-size: 20px;
          font-weight: bolder;
        }
      }

      // 错题切换
      .tabBacImg {
        background-image: url('../../../../../assets/KJ/basicTheory/studyManage/analyze/yicuoti.png');
        background-repeat: no-repeat;
        background-size: 100% 100%;
        position: relative;
        line-height: 39px;
        padding-left: 30px;
        span {
          color: @blueColor;
          font-size: 18px;
          margin-top: 5px;
        }

        .textColor {
          color: #34b34c;
        }

        span:nth-child(1) {
          left: 30px;
        }

        span:nth-child(2) {
          left: 145px;
        }
      }
    }

    .tabItem:hover {
      background: #343c39 !important;
    }
    .boxBorderShadow {
      border: 1px solid transparent;
      /*box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);*/
    }
  }

  @media (max-width: 1320px) {
    .leftBoxs {
      width: 360px !important;
    }
    .chartRBoxs {
      width: calc(100% - 360px) !important;
    }
  }
  @media (max-width: 1200px) {
    .charBox {
      width: 352px !important;
      margin-top: 20px !important;
    }
    .charBoxR {
      width: calc(100% - 352px) !important;
      padding-left: 0 !important;
    }
    .charItem {
      transform: scale(.8);
      margin-left: -10px;
    }
  }
</style>
