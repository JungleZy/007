import {message, Modal} from "ant-design-vue";
import {ref} from 'vue';
import moment from "moment";
import 'moment/dist/locale/zh-cn.js';
import {deepClone} from "../../../../../../common/utils/Utils.js";
import {
   findAllTheoryKnowledgeQuestionLevel,
   saveTheoryKnowledgeQuestionLevel,
   deleteTheoryKnowledgeQuestionLevelById
} from "../../../../../../common/api/TheoryQuestionBankApi";
import {findTestPaperByLevelIdAndName} from "../../../../../../common/api/TestApi";
import {treeOrganizeSb} from "../../../../../../components/test/nodeTree/organizationNodeTree"
import {listSort} from "../../../../../../components/test/nodeTree/listSort";

export default function useQuestionBank(listData) {
   const selecttreeA=ref([])
   const knowledgeList = ref([]); //知识节点树
   const knowledgeListTwo = ref([]); //知识节点树2
   const addDrillModal = ref(false);
   const activeKnowledge = ref({});
   const newKnowledge = ref({});
   const addAndUpdate = ref(null);
   const openMenu=ref(true);
   const menuIcon=ref('icon-xiangshangshousuo1');
   const queryKnowledgeTree = (e) => {
      findAllTheoryKnowledgeQuestionLevel().then(res => {
         if (res.code === 200) {
            knowledgeList.value = treeOrganizeSb(res.data, []);
            knowledgeListTwo.value = deepClone(knowledgeList.value)
            selecttreeA.value=[
               {
                  title:'知识总览',
                  value:"-1",
                  children:deepClone(knowledgeList.value)
               }
            ]
         }
      })
   };
   const clickMenu = (e, index) => {
      knowledgeList.value[index].isOpen = !knowledgeList.value[index].isOpen
   };
   const openMenus = ()=>{
     openMenu.value = !openMenu.value
     if(openMenu.value){
       menuIcon.value = 'icon-xiangshangshousuo1'
     }else {
       menuIcon.value = 'icon-xiangxiazhankai1'
     }
   }
   const clickKnowledge = (e, index) => {
      activeKnowledge.value.index = index;
      if (index === -1) {
         activeKnowledge.value.name = e;
         activeKnowledge.value.id="-1";
         newKnowledge.value.parentId = 1;
      } else {
         activeKnowledge.value.name = e.title;
         activeKnowledge.value.children = e.children;
         newKnowledge.value.parentId = e.key;
         newKnowledge.value.parentIds = e.parentId;
         activeKnowledge.value.type = e.type;
         activeKnowledge.value.id=e.value;
      }
      if (listData===undefined) return;
      searchPaper({levelId:e=='知识总览'?'1':e.key})
   };
   const searchPaper = (e)=>{
      findTestPaperByLevelIdAndName(e).then(res => {
         if (res.code === 200) {
            listData.value=listSort(res.data)
         }
      })
   }

   const addKnowledge = (index) => {
      if (!activeKnowledge.value.name) {
         message.error('请选择节点');
         return
      }
      newKnowledge.value.name = '';
      addAndUpdate.value = index;
      if (index === 1) {
         if (activeKnowledge.value.type === 'childNode') {
            message.error('该节点下不支持新增');
            return
         }
         addDrillModal.value = true;
      } else if (index === 2) {
         if (activeKnowledge.value.index === -1) {
            message.error('根节点不支持修改');
            return
         }
         newKnowledge.value.name = activeKnowledge.value.name;
         addDrillModal.value = true;
      } else {
         if (activeKnowledge.value.id == -1) {
            message.error('根节点不支持删除');
            return;
         }
         if (activeKnowledge.value.children) {
            if (activeKnowledge.value.children.length !== 0) {
               message.error('请删除该节点下的子节点');
               return;
            }
         }
         Modal.confirm({
            title: () => '确定删除该节点？',
            okType: 'danger',
            okText: () => '确定',
            cancelText: () => '取消',
            onOk() {
               deleteTheoryKnowledgeQuestionLevelById({
                  id: newKnowledge.value.parentId
               }).then(res => {
                  if (res.code === 200) {
                     message.success('删除成功');
                     queryKnowledgeTree();
                     activeKnowledge.value = {}
                  }
               })
            }
         })
      }
   };
   const addNode = (index) => {
      if (index === 1) {
         if (!newKnowledge.value.name) {
            message.error('请输入节点名');
            return
         }
         saveTheoryKnowledgeQuestionLevel({
            name: newKnowledge.value.name,
            parentId: newKnowledge.value.parentId
         }).then(res => {
            if (res.code === 200) {
               message.success('新增节点成功');
               queryKnowledgeTree();
               addDrillModal.value = false;
               newKnowledge.value.name = '';
            }
         })
      } else {
         if (!newKnowledge.value.name) {
            message.error('修改的节点名不能为空');
            return
         }
         saveTheoryKnowledgeQuestionLevel({
            name: newKnowledge.value.name,
            id: newKnowledge.value.parentId,
            parentId: newKnowledge.value.parentIds,
         }).then(res => {
            if (res.code === 200) {
               message.success('修改节点成功');
               queryKnowledgeTree();
               addDrillModal.value = false;
               newKnowledge.value.name = '';
            }
         })
      }
   };
   //搜索知识节点
   const searchForKnowledge = (e) => {
      const value = e.target.value;
      knowledgeList.value = value ? selectTree(value, knowledgeListTwo.value) : knowledgeListTwo.value;
   };
   //查找方法
   const selectTree = (value, gData, expandedKeys) => {
      return gData.map((item) => {
         return getParentValue(value, item, expandedKeys);
      }).filter((item, i, self) => item && self.indexOf(item) === i);
   };
   // 查找树的节点
   const getParentValue = (value, tree, expandedKeys) => {
      let parentValue;
      if (tree.title.indexOf(value) > -1) {
         parentValue = {
            key: tree.key,
            title: tree.title,
            type: tree.type,
            isOpen: true,
         };
         if (tree.children !== undefined) {
            parentValue.children = [];
            for (let t of tree.children) {
               let childNode = getParentValue(value, t, expandedKeys);
               if (childNode !== undefined) {
                  parentValue.children.push(childNode);
               }
            }
         }
         if (expandedKeys !== undefined) {
            expandedKeys.push(parentValue.key);
         }
         return parentValue
      } else {
         if (tree.children !== undefined) {
            parentValue = {
               key: tree.key,
               title: tree.title,
               type: tree.type,
               isOpen: true,
               children: []
            };
            for (let t of tree.children) {
               let childNode = getParentValue(value, t, expandedKeys);
               if (childNode !== undefined) {
                  parentValue.children.push(childNode);
               }
            }
            if (parentValue.children.length !== 0) {
               return parentValue;
            }
         }
      }
   };
   return {
      knowledgeList,
      addDrillModal,
      activeKnowledge,
      newKnowledge,
      addAndUpdate,
      openMenu,
      queryKnowledgeTree,
      clickMenu,
      clickKnowledge,
      addKnowledge,
      addNode,
      searchForKnowledge,
      openMenus,
      selecttreeA,
     menuIcon
   }
}