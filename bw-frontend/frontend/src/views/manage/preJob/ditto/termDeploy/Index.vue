<template>
  <div class="w-full h-full pageContent">
    <div class="termDataList">
      <div :class="{tabs: true, single: formData.type=='single', frase: formData.type=='frase'}">
        <div :class="{'tab single fs_dispose': true, on: formData.type=='single'}" @click="selectTermDataType('single')">单词</div>
        <div :class="{'tab frase fs_dispose': true, on: formData.type=='frase'}" @click="selectTermDataType('frase')">语句</div>
      </div>
      <div style="height: calc(100% - 50px);">
        <div class="searchBox">
          <a-input v-model:value="keyword" style="height: 30px" @input="keywordsSearchInfo" placeholder="请输入检索关键字"></a-input>
          <div class="item_group btn " style="margin-left: 16px;height: 30px"  @click="emptyFormDataInfo();formData.id=null"><PlusOutlined style="margin-right: 5px;"/>添加</div>
        </div>
        <div class="listBox" :style="{height: 'calc(100% - 54px)', paddingLeft: formData.type=='single'?'20px':'2px'}">
          <div class="indexs" v-if="formData.type=='single'">
            <div v-for="(idx, i) in indexs" class="idx" @click="anchorJump(idx)">{{idx}}</div>
          </div>
          <div class="overflow-auto w-full h-full " ref="anchorJumpBoxRef" style="padding-left: 10px">
            <template v-for="(item, i) in termData[formData.type]">
              <div :id="'anchor'+keyLabs[i]" style="height: 36px;margin:10px 0 10px -10px"
                   v-if="item.key && item.key.split('')[0] != keyLabs[i-1] && formData.type == 'single'">
                <a-affix :target="() => anchorJumpBoxRef">
                  <div class="keyLab" :style="{fontSize: (fs * 2 + 24) + 'px'}">{{item.key.split('')[0]}}</div>
                </a-affix>
              </div>
              <div :class="{item: true,}" style="padding: 0;margin: 0px" class="relative" :style="[formData.type=='frase'?'margin-left:10px':'']">
                <div class="w-full box" @click="selectTermItemDataInfo(item)" style="padding: 10px 10px 0px 0px">
                  <div class=" mark" :style="{fontSize: (fs * 2 + 12) + 'px'}">{{i+1}}</div>
                  <div class="key " :class="{ bg: formData.id==item.id}" :style="{fontSize: (fs * 2 + 15) + 'px'}">{{item.key}}</div>
                  <div class="value " :class="{ bg: formData.id==item.id}" :style="{fontSize: (fs * 2 + 13) + 'px'}">{{item.value}}</div>
                </div>
                <div class="icons">
                  <a-popconfirm :title="['是否删除该'+(formData.type=='single'?'单词':'语句')+'！']" ok-text="确定" cancel-text="取消" @confirm="deleteTermItemDataInfo(item.id)">
                    <DeleteOutlined style="color: red;margin-left: 10px"/>
                  </a-popconfirm>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
    <div class="termCont">
      <div class="cont" style="height: calc(100% - 100px);">
        <div class="iptBox keys fs_dispose">
          <a-textarea v-model:value="formData.key" class="iptKey fs_dispose" @input="changeTermKey"
                      placeholder="请输入用语/话句的字码"></a-textarea>
        </div>
        <img :src="parting" class="parting" v-if="interfaceStyle==='HJ'">
        <div class="iptBox value">
          <a-textarea v-model:value="formData.value" type="textarea" class="iptKey text fs_dispose" @input="changeTermKey"
                      placeholder="请输入字码的释义"></a-textarea>
        </div>
      </div>
      <div class="btns">
        <div class="bCont">
          <div class="item_group btn" style="margin-left: 16px;height: 30px" @click="saveTermItemDataInfo()"><CheckOutlined style="margin-right: 5px;"/>提交</div>
          <div class="item_group btn" style="margin-left: 16px;height: 30px" @click="resetTermItemDataInfo()"><RedoOutlined style="margin-right: 5px;"/>重置</div>
          <div class="item_group btn" style="margin-left: 16px;height: 30px" @click="emptyFormDataInfo()"><ClearOutlined style="margin-right: 5px;"/>清空</div>
        </div>
      </div>
    </div>
  </div>
</template>


<script>
export default {
  name: "TermDeploy"
}
</script>
<script setup>
import {PlusOutlined,EditOutlined,DeleteOutlined,CheckOutlined,ClearOutlined,RedoOutlined,ExclamationCircleOutlined} from '@ant-design/icons-vue';
import {onMounted, ref, createVNode, nextTick, onBeforeUnmount} from "vue";
import {message,Modal} from "ant-design-vue";
import {deepClone} from "../../../../../common/utils/Utils.js";
import {getTermDeployListData,addTermItemData,updateTermItemData,deleteTermItemData} from "../../../../../common/api/TelegramApi.js";
import parting from '../../../../../assets/HJ/term/parting.png';
const interfaceStyle = window.interfaceStyle
const termData = ref({
  single: [],
  frase: [],
  cache_single: [],
  cache_frase: []
});
const formData = ref({
  id: null,
  type: 'single',
  key: '',
  value: ''
});
const indexs = ref(['A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z']);
const keyLabs = ref([]);
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const keyword = ref(undefined);
const anchorJumpBoxRef = ref(null);

onMounted(() => {
  getTermDeployData();
});

/**
 * 获取通报用语数据
 * @param type
 */
const getTermDeployData = (type) => {
  let data = {};
  if (type) {
    data = {type: type==='single'?0:1};
  }
  getTermDeployListData(data).then(res => {
    if (res.code === 200) {
      if (!type || type==='single') {
        termData.value.single = res.data.filter(item => item.type===0);
        termData.value.cache_single = deepClone(termData.value.single);
        keyLabs.value = termData.value.cache_single.map(item=>item.key.split('')[0]);
      }
      if (!type || type==='frase') {
        termData.value.frase = res.data.filter(item => item.type===1).sort((a,b) => a.sort-b.sort);
        termData.value.cache_frase = deepClone(termData.value.frase);
      }
    }
  })
};

/**
 * 通报用语选项卡切换
 * @param type
 */
const selectTermDataType = (type) => {
  formData.value.id = null;
  formData.value.key = '';
  formData.value.value = '';
  formData.value.type = type;
  keyword.value = '';
  keywordsSearchInfo();
};

/**
 * 重置表单数据
 */
const resetTermItemDataInfo = () => {
  if (formData.value.id) {
    for (let item of termData.value[formData.value.type]) {
      if (item.id === formData.value.id) {
        formData.value.key = item.key;
        formData.value.value = item.value;
      }
    }
  } else {
    emptyFormDataInfo();
  }
};

/**
 * 清空表单数据
 */
const emptyFormDataInfo = () => {
  formData.value.key = '';
  formData.value.value = '';
};

/**
 * 关键字检索
 */
const keywordsSearchInfo = () => {
  keyword.value = keyword.value.toUpperCase();
  termData.value[formData.value.type] = termData.value['cache_'+formData.value.type].filter(item => item.key.indexOf(keyword.value) > -1);
};

/**
 * 锚点跳转
 * @param idx
 */
const anchorJump = (idx) => {
  let move_top = document.getElementById('anchor'+idx).offsetTop;
  nextTick(() => {
    anchorJumpBoxRef.value.scrollTo({top: (move_top + 1), behavior: 'smooth'})
  })
};

/**
 * 校验用语key值输入
 */
const changeTermKey = () => {
  formData.value.key = formData.value.key.toUpperCase();
};

/**
 * 修改通报用语数据
 */
const saveTermItemDataInfo = () => {
  if (!formData.value.key || formData.value.key === '') {
    message.error('请输入用语/话句的字码！');
    return false;
  }
  if (!formData.value.value || formData.value.value === '') {
    message.error('请输入字码的释义！');
    return false;
  }

  if (formData.value.id) {
    updateTermItemDataInfo();
  } else {
    addTermItemDataInfo();
  }
};

/**
 * 选择修改的用语数据
 * @param item
 */
const selectTermItemDataInfo = (item) => {
  formData.value.id = item.id;
  formData.value.key = item.key;
  formData.value.value = item.value;
};

/**
 * 修改通报用语数据
 */
const updateTermItemDataInfo = () => {
  updateTermItemData({
    id: formData.value.id,
    type: (formData.value.type==='single'?0:1),
    key: formData.value.key,
    value: formData.value.value
  }).then(res => {
    if (res.code === 200) {
      message.success((formData.value.type==='single'?'单词':'语句')+'用语修改成功！');
      getTermDeployData(formData.value.type);
    } else {
      message.error(res.message)
    }
  })
};

/**
 * 添加通报用语数据
 */
const addTermItemDataInfo = () => {
  addTermItemData({
    type: (formData.value.type==='single'?0:1),
    key: formData.value.key,
    value: formData.value.value
  }).then(res => {
    if (res.code === 200) {
      message.success((formData.value.type==='single'?'单词':'语句')+'用语添加成功！');
      getTermDeployData(formData.value.type);
      emptyFormDataInfo();
    } else {
      message.error(res.message)
    }
  })
};

/**
 * 删除通报用语数据
 * @param id
 */
const deleteTermItemDataInfo = (id) => {
  deleteTermItemData({id: id}).then(res => {
    if (res.code === 200) {
      message.success((formData.value.type==='single'?'单词':'语句')+'用语删除成功！');
      getTermDeployData(formData.value.type);
      formData.value.id = null;
    } else {
      message.error(res.message)
    }
  })
};

</script>
<style scoped>
@import "./css/index.less";
</style>