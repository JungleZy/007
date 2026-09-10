<template>
  <div class="w-full mb-1 test" >
    <div class="checkType w-full" style="position: relative">
      <a-radio-group v-model:value="question.type" name="radioGroup" :disabled="question.isType">
        <span style="font-size: 15px;color: #e2f2ff;"><span v-if="indexShow">{{index+1}}、</span><span v-else>题目类型：</span></span>
        <template v-for="( item , i ) in typeCheckList" >
          <a-radio :value="item.id"  v-if="item.name==='简答'?isShort===1?false:true:true" @change="changeType" >
            <span style="color:#e2f2ff">{{item.name}}</span>
          </a-radio>
        </template>
      </a-radio-group>
      <div style="position: absolute;right: 0; top: 0;">
        <slot name="delete" class="" ></slot>
      </div>
    </div>
    <div class="checkType w-full mt-20px" style="position: relative " v-if="!indexShow" >
      <div class="" style="display: flex;align-items: center">
        <span style="font-size: 15px;color: #e2f2ff;white-space: nowrap">知识节点：</span>
        <!--          :getPopupContainer="(triggerNode)=>triggerNode.parentNode.parentNode"-->
        <a-tree-select v-model:value="selectTruelyValue" :dropdown-style="{maxHeight:'400px'}"  :tree-data="selectTree" :dropdownClassName="'dropdown'" tree-default-expand-all class="" style="width: 916px"></a-tree-select>
      </div>
    </div>
    <div class="w-full" style="height: 100%;" :class="[indexShow? 'padding':'']" :style="!indexShow? 'overflow-y: auto;':''">
      <div class="quesTitle mt-20px " style="font-size: 15px">题目</div>
      <div class="mt-4px" >
        <a-input v-model:value="question.topic" v-if="question.type!=4" style="background-color:rgb(23 41 67); "></a-input>
      </div>
      <div class="layout-left-center mt-4px" v-if="question.type == 4">
        <a-textarea placeholder="输入题目内容" :id="'titleTextarea'+ index" @change="changeTitle(question.topic)"
                    v-model:value="question.topic" style="width:calc(100% - 110px);"
                    :auto-size="{minRows:2,maxRows:6}"></a-textarea>
        <div v-if="question.type == 4"  type="primary" class="item_group btn ml-1 " @click="addSpacing">
          添加输入空
        </div>
      </div>
      <div class="quesTitle mt-20px layout-left-center" v-if="question.type!=4&&question.type!=5" style="font-size: 15px">选项
        <PlusSquareOutlined @click="addSelect" class="ml-1 cursor-pointer-def" v-if="question.type!=3" style="font-size: 18px">

        </PlusSquareOutlined>
      </div>
      <div class="manyCheck layout-left-top">
        <a-row class="w-full" v-if="question.type!=3">
          <a-col  :span="11" :offset="(index+1) % 2 == 0 ? '2':'0'" v-for="(item,index) in question.options" class="mt-4px">
            <div class="layout-left-center w-full relative" style="margin-bottom: 6px">
              <div style="width: 19px;" class="layout-left-center">{{Earray[index]}}</div>
              <a-input v-model:value="item.label" style="width: calc(100% - 20px);background-color:rgb(23 41 67);"></a-input>
              <CloseOutlined  @click="deleteClass(index)" class="layout-center" title="删除" style="color:#d11d1d;position: absolute;top: 6px;right: 8px;background: #354971;color: #d8e8f7;border-radius: 50%;width: 18px;height: 18px"></CloseOutlined>
            </div>
          </a-col>
        </a-row>
        <a-radio-group v-model:value="question.answer" name="radioGroup" v-if="question.type==3" class="mt-4px">
          <a-radio :value="item.id" v-for="( item , i ) in question.options"  >
            <span style="color:#e2f2ff">{{item.name}}</span>
          </a-radio>
        </a-radio-group>
      </div>
      <div class="quesTitle mt-20px layout-left-center c-53a165" v-if="question.type!=3">正解</div>
      <a-row class="w-full" v-if="question.type==4">
        <a-col  :span="11" :offset="(index+1) % 2 == 0 ? '2':'0'" v-for="(item,index) in question.answer" class="p-1">
          <div class="layout-left-center w-full">
            <!--                <div style="width: 18px;" class="layout-left-center"></div>-->
            <a-input v-model:value="question.answer[index]" style="width: calc(100% - 18px);background-color:rgb(23 41 67);"></a-input>
          </div>
        </a-col>
      </a-row>
      <a-textarea v-model:value="question.answer" style="resize: none" v-if="question.type==5"></a-textarea>

      <div class="greenInput mt-4px">
        <a-select v-model:value="question.answer" class="w-full" v-if="question.type==1" dropdowmClassName="infoDrop">
          <a-select-option v-for="(item,index) in question.options" :value="item.value" > {{Earray[index]}} {{item.label}}</a-select-option>
        </a-select>
        <!--              option-label-prop="label" -->
        <a-select v-model:value="question.answer" class="w-full" v-if="question.type==2"  mode="multiple" >
          <a-select-option v-for="(item,index) in question.options" :value="item.value"> {{Earray[index]}} {{item.label}}</a-select-option>
        </a-select>
      </div>
      <div class="quesTitle mt-20px layout-left-center c-8b5f2f">解析</div>
      <div class="greenText mt-4px mb-1">
        <a-textarea v-model:value="question.analysis" style="resize: none"></a-textarea>
      </div>
    </div>
  </div>
</template>

<script>
  import {defineComponent, toRefs, ref, watch} from "vue"
  import useRoomTest from './js/useRoomTest.js'
  import {
    PlusSquareOutlined,
    DeleteOutlined,
    CloseCircleOutlined,
    CloseOutlined
  } from '@ant-design/icons-vue';
  // import useRoomTest from './js/useRoomTest'
  export default defineComponent ({
    name: "RoomTest",
    // props:{params:Object},
    components: {
      PlusSquareOutlined,
      DeleteOutlined,
      CloseCircleOutlined,
      CloseOutlined
    },
    props: {
      params : Object ,
      selectedKnowledgeSwfs : Object ,
      index : Number,
      indexShow:{
        default:true,
        type: Boolean
      },//true为显示外部传入index;false为显示选题目
      selectTree:Array, //节点树，需要选择题目时添加
      selectValue:String,
      isShort:Number,
    },
    setup(props,context){
      const {params,selectedKnowledgeSwfs,index,indexShow,selectTree,selectValue}=toRefs(props);
      const selectTruelyValue=ref('');//选中节点//需要外部自行组装
      const findFirSon=(tree)=>{
        for(let i in tree){
          if(tree[i].children){
            findFirSon(tree[i].children)
            break
          }else if(tree[i].type){
            selectTruelyValue.value=tree[i].value;
            break
          }
        }
      };
      const dg = (tree,value)=>{
        for(let i in tree){
          if(value==-1){//查第一个孙子
            if(tree[i].children){
              findFirSon(tree[i].children)
              break
            }
          }else if(!tree[i].type) { //查第一个儿子
            if(tree[i].children){
              if(tree[i].value==value){
                findFirSon(tree[i].children)
                break
              }else {
                dg(tree[i].children,value)
              }
            }else if(!tree[i].children){
              dg(selectTree.value,"-1")
              break
            }
          }else {
            if(tree[i].value==value){
              selectTruelyValue.value=tree[i].value;
              break
            }
          }
        }
      };
      // dg(selectTree.value,selectValue.value);
      const { question,typeCheckList,Earray,addSelect,changeType,spacing,spacingLength,addSpacing,changeTitle,deleteClass}=useRoomTest(params,selectedKnowledgeSwfs,index);
      question.value.type = ""+ question.value.type
      selectTruelyValue.value = question.value.levelId
      return{
        changeTitle,question,typeCheckList,Earray,addSelect,changeType,spacing, spacingLength,index,addSpacing,deleteClass,selectTruelyValue
      }
    },
  })
</script>

<script setup>

</script>

<style lang="less" scoped>
  .padding{
    padding-left: 24px;
  }
  .mt-4px{
    margin-top: 4px;
  }
  .mt-20px{
    margin-top: 20px;
  }
  .HJ{
    .test{
    .ant-input-number .ant-input{
      background-color: #172b47 !important;
    }
  }
    .greenText{
      .ant-input{
        background-color: #1e3552;
        border-color: #5c5145;
        color: #fff;
        height: 114px;
      }
    }
    .infoDrop{
      .ant-select-dropdown-menu{
        background: #fff !important;
      }
    }
    .infoDrop:active{
      background: #354971;
    }
    .infoDrop:hover{
      background: #354971;
    }
    .greenInput{
      .ant-select:not(.ant-select-customize-input) .ant-select-selector{
        background-color: #1a344d;
        border-color: #346558;
        color: #ffffff;
      }
    }
    .dropdown{
      top: 315px !important;
    }
    .quesTitle{
      color: #7b90af;
      font-size: 14px;
    }
    .c-53a165{
      color:#78e775 ;
    }
    .c-8b5f2f{
      color: #ff8f10;
    }
  }
  .HJJ{
    .test{
      .ant-input-number .ant-input{
        background-color: #172b47 !important;
      }
    }
    .greenText{
      .ant-input{
        background-color: #1e3552;
        border-color: #5c5145;
        color: #fff;
        height: 114px;
      }
    }
    .infoDrop{
      .ant-select-dropdown-menu{
        background: #fff !important;
      }
    }
    .infoDrop:active{
      background: #354971;
    }
    .infoDrop:hover{
      background: #354971;
    }
    .greenInput{
      .ant-select:not(.ant-select-customize-input) .ant-select-selector{
        background-color: #141e28;
        border-color: #346558;
        color: #ffffff;
      }
    }
    .dropdown{
      top: 315px !important;
    }
    .quesTitle{
      color: #7b90af;
      font-size: 14px;
    }
    .c-53a165{
      color:#78e775 ;
    }
    .c-8b5f2f{
      color: #ff8f10;
    }
  }
  .LJ{
    .test{
      .ant-input,.ant-input-number,/deep/.ant-radio-inner, /deep/.ant-select:not(.ant-select-customize-input) .ant-select-selector{
        background-color: #413F33 !important;
      }
    }
    .greenText{
      .ant-input{
        background-color: #413F33 !important;
        border-color: #5c5145;
        color: #fff;
        height: 114px;
      }
    }
    .infoDrop{
      .ant-select-dropdown-menu{
        background: #fff !important;
      }
    }
    .infoDrop:active{
      background: #26332e;
    }
    .infoDrop:hover{
      background: #26332e;
    }
    .greenInput{
      .ant-select:not(.ant-select-customize-input) .ant-select-selector{
        /*background-color: #413F33;*/
        border-color: #346558;
        color: #ffffff;
      }
    }
    .dropdown{
      top: 315px !important;
    }
    .quesTitle{
      /*color: #a9abaa;*/
      font-size: 14px;
    }
    .c-53a165{
      background:#00910d ;
      color:#ffffff ;
      width: max-content;
      padding: 0 10px;
    }
    .c-8b5f2f{
      background: #d7551e;
      color: #ffffff;
      width: max-content;
      padding: 0 10px;
    }
  }
  .GD{
    .test{
      .ant-input,.ant-input-number,/deep/.ant-radio-inner, /deep/.ant-select:not(.ant-select-customize-input) .ant-select-selector{
        background-color: rgba(255,255,255,0.6) !important;
      }
    }
    .greenText{
      .ant-input{
        background-color: #413F33 !important;
        border-color: #5c5145;
        color: #fff;
        height: 114px;
      }
    }
    .infoDrop{
      .ant-select-dropdown-menu{
        background: #fff !important;
      }
    }
    .infoDrop:active{
      background: #26332e;
    }
    .infoDrop:hover{
      background: #26332e;
    }
    .greenInput{
      .ant-select:not(.ant-select-customize-input) .ant-select-selector{
        /*background-color: #413F33;*/
        border-color: #346558;
        color: #ffffff;
      }
    }
    .dropdown{
      top: 315px !important;
    }
    .quesTitle{
      /*color: #a9abaa;*/
      font-size: 14px;
    }
    .c-53a165{
      background:#00910d ;
      color:#ffffff ;
      width: max-content;
      padding: 0 10px;
    }
    .c-8b5f2f{
      background: #d7551e;
      color: #ffffff;
      width: max-content;
      padding: 0 10px;
    }
  }
</style>