<template>
  <a-tabs v-model:activeKey="activeKey" @change="changeTab">
<!--    <a-tab-pane key="1" tab="训练用时统计" force-render>-->
<!--      <div class="w-full personalData">-->
<!--        &lt;!&ndash;        <div class="title">训练时长：</div>&ndash;&gt;-->
<!--        <div class="layout-right-center">-->
<!--          <a-range-picker v-model:value="time" @change="changeTime" :disabled-date="disabledDate"  />-->
<!--        </div>-->
<!--        <div class="layout-left-center box overflow-auto" id="trainTimeCharts">-->
<!--          &lt;!&ndash;          <div v-for="(v,index) of trainType" class="line">&ndash;&gt;-->
<!--          &lt;!&ndash;            <div class="layout-center item" >{{v.type}}</div>&ndash;&gt;-->
<!--          &lt;!&ndash;            <div class="layout-center item">{{computationTime(trainData[v.key])}}</div>&ndash;&gt;-->
<!--          &lt;!&ndash;          </div>&ndash;&gt;-->
<!--        </div>-->
<!--      </div>-->
<!--    </a-tab-pane>-->
<!--    <a-tab-pane key="2" tab="近十次训练统计">-->
<!--      <div class="w-full personalData">-->
<!--        <div class="layout-right-center">-->
<!--          <a-select-->
<!--              ref="select"-->
<!--              v-model:value="trainType10"-->
<!--              style="width: 120px"-->
<!--              @change="handleChangeTrainType"-->
<!--          >-->
<!--            <a-select-option :value="v.key" v-for="v of trainType">{{ v.type }}</a-select-option>-->
<!--          </a-select>-->
<!--        </div>-->
<!--        <div class="layout-center box relative" id="trainCharts10">-->
<!--            <div class="layout-center w-full " v-if="isHaveTrainData" style="color: #ffffff;font-size: 18px;z-index: 9;position: absolute">暂无训练数据！</div>-->
<!--        </div>-->
<!--      </div>-->
<!--    </a-tab-pane>-->
    <a-tab-pane key="3" tab="修改密码">
      <div class="layout-center" style="color: white;height: 400px">
        <div class="password layout-center" style="align-content: center">
          <div>原始密码：
            <a-input placeholder="请输入原始密码" v-model:value="editPasswordData.oldPassword"
                     style="width: 200px; text-align: left"></a-input>
          </div>
          <div style="margin: 20px 0 20px 13px">新密码：
            <a-input placeholder="请输入新密码" type="password" v-model:value="editPasswordData.newPassword"
                     style="width: 200px; text-align: left"></a-input>
          </div>
          <div>确认密码：
            <a-input type="password" placeholder="请确认密码" v-model:value="editPasswordData.newPasswordV"
                     style="width: 200px; text-align: left"></a-input>
          </div>
        </div>
        <div class="w-full layout-center">
          <div class="createDrillBtn" @click="editPassword">
            修改密码
          </div>
        </div>
      </div>
    </a-tab-pane>
  </a-tabs>
</template>
<script>
export default {
  name: "Personal"
}
</script>
<script setup>
import personal from './js/personal'
import {ref} from 'vue'
import zhCN from 'ant-design-vue/es/date-picker/locale/zh_CN'
import zhCN1 from 'ant-design-vue/lib/date-picker/locale/zh_CN'

console.log(zhCN1);
const qwe = ref(zhCN)

qwe.value.months=["一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"]
qwe.value.lang.months=["一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"]
qwe.value.weekdays=["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
qwe.value.lang.weekdays=["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
console.log(qwe.value);
const {trainType,trainData,
  changeTime,editPassword,
  userInfo,editPasswordData,
  handleChangeTrainType,
  trainType10,
  changeTab,isHaveTrainData,
  time,activeKey} = personal()
const disabledDate = ( current) => {
  return current && current >= dayjs().endOf('day');
};
</script>



<style scoped lang="less">
@LJboder:#38403d;
@HJboder:#415684;
@HJJboder:#3f5669;
@KJboder:#5d7f76;
.password{
  border: 1px solid @KJboder;
  padding: 12px;
  width: 60%;
  max-width: 400px;
  height: 80%;
  border-radius: 10px;
}
.LJ{
  .title{color: #ffffff;display: none}
  .personalData{
    background: #18231f;
    height: 600px;
    width: 100%;
    .box{
      //border-left: 1px solid @LJboder;
      height: 100%;
      justify-content: space-between;
      padding:12px;
    }
    .line{
      background: url("../../assets/LJ/basicTheory/analysis-bg.png");
      background-repeat: no-repeat;
      background-size: 100% 100%;
      width: calc((100% - 12px) / 2);
      height: calc(481px / 2);
      margin-top: 12px;
      //width: calc(100% / 8);
      color: #ffffff;
      //border-top: 1px solid @LJboder;
      //border-bottom: 1px solid @LJboder;
      //border-right: 1px solid @LJboder;
      .item{
        height: 40px;
        &:last-of-type{
          border-top: 1px solid @LJboder;
        }
      }
    }
  }
}
.HJ{
  .title{color: #ffffff}
  .personalData{
    background: #0a1936;
    height: 400px;
    width: 100%;
    padding: 0 12px;
    .box{
      border-left: 1px solid @HJboder;
    }
    .line{
      width: calc(100% / 8);
      color: #ffffff;
      border-top: 1px solid @HJboder;
      border-bottom: 1px solid @HJboder;
      border-right: 1px solid @HJboder;
      .item{
        height: 40px;
        &:last-of-type{
          border-top: 1px solid @HJboder;
        }
      }
    }
  }
}
.HJJ{
  .title{color: #ffffff}
  .personalData{
    background: rgba(30, 46, 58, 0.4);
    height: 400px;
    width: 100%;
    padding: 0 12px;
    .box{
      border-left: 1px solid @HJJboder;
    }
    .line{
      width: calc(100% / 8);
      color: #ffffff;
      border-top: 1px solid @HJJboder;
      border-bottom: 1px solid @HJJboder;
      border-right: 1px solid @HJJboder;
      .item{
        height: 40px;
        &:last-of-type{
          border-top: 1px solid @HJJboder;
        }
      }
    }
  }
}
.KJ{
  .title{color: #ffffff}
  .personalData{
    background: #0e3040;
    height: 400px;
    width: 100%;
    padding: 0 12px;
    .box{
      border-left: 1px solid @KJboder;
    }
    .line{
      width: calc(100% / 8);
      color: #ffffff;
      border-top: 1px solid @KJboder;
      border-bottom: 1px solid @KJboder;
      border-right: 1px solid @KJboder;
      .item{
        height: 40px;
        &:last-of-type{
          border-top: 1px solid @KJboder;
        }
      }
    }
  }
}

</style>