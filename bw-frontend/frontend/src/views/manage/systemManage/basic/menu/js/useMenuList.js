import {onMounted, ref} from "vue";
import {getMenusAll} from '../../../../../../common/api/MenusApi.js'

export default function (tableLoading) {
  const columns = ref([
    {
      title: '图标',
      dataIndex: 'meta.icon',
      key: 'meta.icon',
      width: 140,
      slots: {customRender: 'icon'},
    },
    {
      title: '标题',
      dataIndex: 'meta.title',
      align: "center",
      width: 120,
      key: 'meta.title',
    },
    {
      title: '唯一值',
      dataIndex: 'name',
      align: "center",
      key: 'name',
    },
    {
      title: '路由地址',
      dataIndex: 'path',
      align: "center",
      key: 'path',
      ellipsis: true,
    },
    {
      title: '文件路径',
      key: 'component',
      dataIndex: 'component',
      align: "center",
      ellipsis: true,
      slots: {customRender: 'component'},
    },
    {
      title: '权限数量',
      key: 'permissions',
      dataIndex: 'permissions',
      align: "center",
      ellipsis: true,
      slots: {customRender: 'permissions'},
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      align: "center",
      slots: {customRender: 'action'},
    },
  ]);
  const menusData = ref([]);
  onMounted(() => {
    getMenusList();
  })
  const getMenusList = () => {
    tableLoading.value = true;
    getMenusAll().then(res => {
      if (res.code === 200) {
        menusData.value = [];
        res.data.forEach(d => {
          if (d.children.length > 0) {
            menusData.value.push(d)
          } else {
            let {children, ...params} = d;
            menusData.value.push(params)
          }
        })
      }
      tableLoading.value = false;
    })
  }

  return {
    columns,
    menusData,
    getMenusList
  }
}