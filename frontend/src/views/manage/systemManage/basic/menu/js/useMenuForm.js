import {reactive, ref, onMounted} from 'vue'
import {message} from 'ant-design-vue';
import {addMenu, getMenuById} from "../../../../../../common/api/MenusApi.js";
import * as R from 'ramda';
import {deepClone} from "../../../../../../common/utils/Utils.js";

export default function (tableLoading, getUserList) {
  const state = reactive({
    visible: false,
    modelTitle: '',
    onlyRead: false,
    confirmLoading: false,
  });
  const menuFormState = ref({
    id: null,
    parentId: "-1",
    key: "",
    path: "",
    name: "",
    icon: "",
    title: "",
    isMenu: 0,
    component: "-1",
    sort: 0,
  });
  const permissionsState = ref([
    {
      id: "",
      menusId: "",
      title: "新增",
      key: 'add',
    }, {
      id: "",
      menusId: "",
      title: "修改",
      key: 'edit',
    }, {
      id: "",
      menusId: "",
      title: "删除",
      key: 'delete',
    }, {
      id: "",
      menusId: "",
      title: "详情",
      key: 'details',
    }]);
  const selectPermission = ref({});
  const selectPermissionIndex = ref(-1);
  const menuList = ref([]);
  const vueFileTree = ref([]);
  onMounted(() => {
    let modules = Object.keys(import.meta.globEager('../../../../**/*.vue'));
    // console.log("modules")
    // console.log(modules)
    // console.log("modules")
    let filePath = [];
    for (const p of modules) {
      let ls = p.slice(12).split("/");
      for (let i = 1; i < ls.length; i++) {
        let dValue = '';
        for (let j = 1; j <= i; j++) {
          dValue = dValue + ls[j] + '/';
        }
        let path = '/manage/' + (ls[i - 2] ? ls[i - 2] : ls[i - 1]) + '/' + dValue.slice(0, -1);
        let path1 =  '/manage'
        for (let k =0;k<=i;k++){
          path1+="/"+ls[k]
        }
        if (path.endsWith(".vue")) {
          path = path.substring(0, path.length - 4);
          path1 = path1.substring(0, path1.length - 4);
        }
        filePath.push({
          id: ls[i],
          key: filePath.length + 1,
          title: ls[i],
          value: path1,
          parentId: ls[i - 1],
          parentIds: ls[i - 2],
        })
      }
    }
    // console.log("filePath");
    // console.log(filePath);
    // console.log("filePath");

    const filePathTree = [];
    for (let r of filePath) {
      if (R.isNil(r.parentIds)) {
        let ct = dataRouteTree(r, filePath);
        if (ct.length !== 0) {
          let obj = {};
          let peon = ct.reduce((cur, next) => {
            obj[next.value] ? "" : obj[next.value] = true && cur.push(next);
            return cur;
          }, [])
          filePathTree.push({
            title: r.title,
            key: r.key,
            value: r.value,
            children: peon
          })
        } else {
          filePathTree.push({
            title: r.title,
            key: r.key,
            value: r.value,
          })
        }
      }
    }
    let obj = {};
    let peon = filePathTree.reduce((cur, next) => {
      obj[next.value] ? "" : obj[next.value] = true && cur.push(next);
      return cur;
    }, [])
    vueFileTree.value = [{
      title: '公共根页面',
      key: '-1',
      value: '-1',
      children: peon
    }];
  })
  const dataRouteTree = (pt, tree) => {
    let newRR = [];
    for (let t of tree) {

      if (pt.id === t.parentId && pt.parentId === t.parentIds) {
        let ct = dataRouteTree(t, tree);
        if (ct.length !== 0) {
          let obj = {};
          let peon = ct.reduce((cur, next) => {
            obj[next.value] ? "" : obj[next.value] = true && cur.push(next);
            return cur;
          }, [])
          newRR.push({
            title: t.title,
            key: t.key,
            value: t.value,
            children: peon
          });
        } else {
          newRR.push({
            title: t.title,
            key: t.key,
            value: t.value,
          });
        }
      }
    }
    return newRR;
  };
  const showModal = (type, record) => {
    resetFrom();
    switch (type) {
      case 0:
        state.modelTitle = "新增菜单";
        state.onlyRead = false;
        break;
      case 1:
        state.modelTitle = "菜单详情";
        findMenuById(record.id);
        state.onlyRead = true;
        break;
      case 2:
        state.modelTitle = "编辑菜单";
        findMenuById(record.id);
        state.onlyRead = false;
        break;
      default:
        break;
    }
    state.visible = true;
  };
  const findMenuById = (id) => {
    getMenuById({id: id}).then(res => {
      menuFormState.value = res.data.menus;
      permissionsState.value = res.data.permissions;
    })
  }
  const handleCancel = () => {
    state.visible = false;
    resetFrom();
  }
  const handleOk = () => {
    handleSaveMenu();
  };
  const handleSaveMenu = () => {
    state.confirmLoading = true;
    addMenu({menus: menuFormState.value, permissions: permissionsState.value}).then(res => {
      if (res.code === 200) {
        state.visible = false;
        state.confirmLoading = false;
        message.success("数据操作成功！");
        getUserList();
      } else {
        message.error(res.message);
        state.confirmLoading = false;
      }
    })
  }
  const handlePermission = (type, index) => {
    switch (type) {
      case 0: // 修改
        selectPermission.value = deepClone(permissionsState.value[index]);
        selectPermissionIndex.value = index;
        break;
      case 1: // 删除
        permissionsState.value.splice(index, 1);
        break;
      case 2: // 新增
        permissionsState.value.push({
          id: "",
          menusId: "",
          title: "新增",
          key: 'add',
        });
        selectPermission.value = deepClone(permissionsState.value[permissionsState.value.length - 1]);
        selectPermissionIndex.value = permissionsState.value.length - 1;
        break;
      case 3: // 确认
        selectPermissionIndex.value = -1;
        break;
      case 4: // 取消
        permissionsState.value[index] = selectPermission.value;
        selectPermissionIndex.value = -1;
        break;
      default:
        break;
    }
  }
  const resetFrom = () => {
    menuFormState.value = {
      id: null,
      parentId: "-1",
      key: "",
      path: "",
      name: "",
      icon: "",
      title: "",
      isMenu: 0,
      component: "-1",
      sort: 0,
    }
    permissionsState.value = [
      {
        id: "",
        menusId: "",
        title: "新增",
        key: 'add',
      }, {
        id: "",
        menusId: "",
        title: "修改",
        key: 'edit',
      }, {
        id: "",
        menusId: "",
        title: "删除",
        key: 'delete',
      }, {
        id: "",
        menusId: "",
        title: "详情",
        key: 'details',
      }
    ];
    selectPermission.value = {};
    selectPermissionIndex.value = -1;
  }

  return {
    state,
    showModal,
    handleCancel,
    handleOk,
    labelCol: {span: 5},
    wrapperCol: {span: 18},
    menuFormState,
    permissionsState,
    selectPermission,
    selectPermissionIndex,
    menuList,
    vueFileTree,
    handlePermission
  }
}