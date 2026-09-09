import {onMounted, ref} from "vue";
import {getRoleAll} from "../../../../../../common/api/RoleApi.js";

export default function ( tableLoading) {
  const columns = ref([
    {
      title: '角色名称',
      dataIndex: 'role.title',
      key: 'role.title',
      align: "center",
    },
    {
      title: '角色描述',
      dataIndex: 'role.remark',
      ellipsis: true,
      key: 'role.remark',
      align: "center",
    },
    {
      title: '权限数',
      dataIndex: 'menus',
      key: 'menus',
      width: 120,
      align: "center",
      slots: {customRender: 'menus'},
    },
    {
      title: '是否超管',
      dataIndex: 'role.isAdmin',
      width: 120,
      key: 'role.isAdmin',
      align: "center",
      slots: {customRender: 'roleIsAdmin'},
    },
    {
      title: '默认角色',
      dataIndex: 'role.isDefault',
      width: 120,
      key: 'role.isDefault',
      align: "center",
      slots: {customRender: 'roleIsDefault'},
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      align: "center",
      slots: {customRender: 'action'},
    },
  ]);
  const roleData = ref([]);
  onMounted(() => {
    getRoleList();
  });
  const getRoleList = () => {
    tableLoading.value = true;
    getRoleAll().then(res => {
      roleData.value = [];
      if (res.code === 200) {
        res.data.forEach(d => {
          roleData.value.push({
            key: d.role.id,
            role: d.role,
            menus: d.menus,
          });

        });
      }
      tableLoading.value = false;
    })
  }

  return {
    columns,
    roleData,
    getRoleList
  }
}