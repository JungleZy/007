import {ref, onMounted, createVNode} from "vue";
import {message, Modal} from "ant-design-vue";
import {
  getAllDeviceType,updateDeviceType,deleteDeviceType,getDeviceTypeEquipment,saveDeviceEquipmentMsg,deleteDeviceEquipment,
  saveDeviceEquipmentDescMsg
} from "../../../common/api/EquipmentApi.js";
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";

export default function equipmentOperation() {
  const typeFormData = ref({
    visible: false,
    type: 0,
    id: '',
    name: '',
  });
  const typeList = ref([]);
  const selectedType = ref({});
  const loading = ref(false);
  const addDrillModal = ref(false);
  const fileList = ref([]);
  const upLoading = ref(false);
  const UEditorMsgContent = ref('');
  const formData = ref({
    id: '',
    deviceTypeId: '',
    deviceImg: '',
    deviceNumber: '',
    deviceName: '',
    descriptions: [
      {
        id: '',
        title: '操作说明',
        content: ''
      },
    ]
  });
  const formDescIndex = ref(0)
  const deviceList = ref([])
  const seeDeviceIndex = ref(0)
  const seeDeviceDescIndex = ref(0)
  const addDescModal = ref(false)
  const descTitle = ref('')

  onMounted(() => {
    getAllDeviceTypeInfo()
  })

  /*获取所有设备分类*/
  const getAllDeviceTypeInfo = () => {
    getAllDeviceType().then(res => {
      if (res.code === 200) {
        typeList.value = res.data;
        if (res.data && res.data.length > 0) {
          selectedType.value = res.data[0];
          getDeviceTypeEquipmentInfo()
        }
      }
    })
  }
  /*获取当前分类下面的所有设备信息*/
  const getDeviceTypeEquipmentInfo = () => {
    getDeviceTypeEquipment({id: selectedType.value.id}).then(res => {
      if (res.code === 200) {
        deviceList.value = res.data;
      }
    })
  }

  /*新增/编辑分类弹窗*/
  const addTypeModal = (item) => {
    typeFormData.value.type = item?1:0;
    typeFormData.value.visible = true;
    typeFormData.value.name = item?item.typeName:'';
    typeFormData.value.id = item?item.id:'';
  }
  /*选择分类查询设备列表*/
  const selectTypeItemInfo = (item) => {
    selectedType.value = item;
    getDeviceTypeEquipmentInfo();
  }
  /*关闭新增/编辑分类弹窗*/
  const cancelTypeModal = () => {
    typeFormData.value.visible = false;
    typeFormData.value.name = '';
    typeFormData.value.id = '';
  }
  /*确认新增/编辑分类弹窗*/
  const confirmTypeModal = () => {
    if (!typeFormData.value.name || typeFormData.value.name == '') {
      message.error('请输入设备分类名称！');
      return false;
    }
    updateDeviceType({
      id: typeFormData.value.id,
      typeName: typeFormData.value.name
    }).then(res => {
      if (res.code === 200) {
        message.success('分类保存成功!');
        if (typeFormData.value.id == '') {
          typeList.value.push(res.data)
        } else {
          typeList.value.map(type => {
            if (type.id == typeFormData.value.id) {
              type.typeName = typeFormData.value.name
            }
          })
        }
      }
    })
    typeFormData.value.visible = false;
  }
  /*删除设备分类*/
  const deleteType = (item) => {
    let title = '是否删除当前分类？';
    if (item.existDevice > 0) {
      title = '当前分类下存在 '+item.existDevice+' 台设备，确认删除当前分类？'
    }
    Modal.confirm({
      class: 'init_modal_style',
      content: title,
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deleteDeviceType({id: item.id}).then(res => {
          if (res.code === 200) {
            message.success('分类删除成功!');
            if (item.id == selectedType.value.id) {
              getAllDeviceTypeInfo();
            } else {
              typeList.value = typeList.value.filter(type => type.id!=item.id)
            }
          }
        })
      }
    })
  }

  /*新增/编辑设备信息弹窗*/
  const addEquipmentModal = () => {
    addDrillModal.value = true;
    formDescIndex.value = 0;
    UEditorMsgContent.value = ''
    formData.value = {
      id: '',
      deviceTypeId: selectedType.value.id,
      deviceImg: '',
      deviceNumber: '',
      deviceName: '',
      descriptions: [
        {
          id: '',
          title: '操作说明',
          content: ''
        },
      ]
    }
  }
  /*设备图片上传*/
  const handleChange = (info) => {
    if (info.file.status === 'uploading') {
      upLoading.value = true;
      return;
    }
    if (info.file.status === 'done') {
      upLoading.value = false;
      formData.value.deviceImg = '/'+info.file.response.data
    }
    if (info.file.status === 'error') {
      upLoading.value = false;
      message.error('上传错误');
    }
  };
  /*设备图片上传格式验证*/
  const beforeUpload = (file) => {
    const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png';
    if (!isJpgOrPng) {
      message.error('上传文件格式错误，只支持jpg或者png图片!');
    }
    const isLt2M = file.size / 1024 / 1024 < 4;
    if (!isLt2M) {
      message.error('上传文件超出4MB大小限制！');
    }
    return isJpgOrPng && isLt2M;
  };
  /*新增设备说明项*/
  const addDescOption = () => {
    formData.value.descriptions.push({
      id: '',
      title: '说明项',
      content: ''
    })
  }
  /*删除设备说明项*/
  const closeEquipmentDesc = (i) => {
    if (formData.value.descriptions.length == 1) {
      message.error('设备说明项至少保留一条！');
      return false;
    }
    if (formDescIndex.value == i) {
      formDescIndex.value --;
      UEditorMsgContent.value = formData.value.descriptions[formDescIndex.value].content
    }
    formData.value.descriptions = formData.value.descriptions.filter((item,index) => index != i)
  }
  /*选择切换设备说明项*/
  const seletDescOption = (item,i) => {
    formData.value.descriptions[formDescIndex.value].content = UEditorMsgContent.value
    formDescIndex.value = i;
    UEditorMsgContent.value = item.content
  }
  /*确认保存设备信息*/
  const confirmEquipmentInfo = () => {
    if (formData.value.deviceName == '') {
      message.error('设备名称不能为空！');
      return false;
    } else if (formData.value.deviceNumber == '') {
      message.error('设备编码不能为空！');
      return false;
    } else if (formData.value.deviceImg == '') {
      message.error('设备图片不能为空！');
      return false;
    }
    formData.value.descriptions[formDescIndex.value].content = UEditorMsgContent.value
    let desc = formData.value.descriptions.filter(item => item.title!=''&&item.content!='');
    if (desc.length == 0) {
      message.error('设备说明不能为空！');
      return false;
    }
    loading.value = true;
    saveDeviceEquipmentMsg(formData.value).then(res => {
      loading.value = false;
      if (res.code === 200) {
        message.success('设备信息保存成功！');
        if(formData.value.id == '') {
          deviceList.value.push(res.data)
        } else {
          deviceList.value = deviceList.value.map(item => {
            if (item.id == formData.value.id) {
              item = res.data;
            }
            return item
          })
        }
        cancelModal();
      }
    })
  }
  /*关闭设备编辑弹窗*/
  const cancelModal = () => {
    addDrillModal.value = false;
    addDescModal.value = false;
    formDescIndex.value = 0;
    descTitle.value = ''
    UEditorMsgContent.value = ''
    formData.value = {
      id: '',
      deviceTypeId: '',
      deviceImg: '',
      deviceNumber: '',
      deviceName: '',
      descriptions: [
        {
          id: '',
          title: '操作说明',
          content: ''
        }
      ]
    }
  }
  /*更新编辑当前设备信息*/
  const modifyDeviceInfo = (item) => {
    addDrillModal.value = true;
    formDescIndex.value = 0;
    UEditorMsgContent.value = item.descriptions[0].content
    formData.value = {
      id: item.id,
      deviceTypeId: item.deviceTypeId,
      deviceImg: item.deviceImg,
      deviceNumber: item.deviceNumber,
      deviceName: item.deviceName,
      descriptions: item.descriptions
    }
  }
  /*删除当前设备*/
  const deleteDeviceInfo = (item) => {
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除当前设备？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deleteDeviceEquipment({id: item.id}).then(res => {
          if (res.code === 200) {
            message.error('设备删除成功！')
            getDeviceTypeEquipmentInfo()
          }
        })
      }
    })
  }

  /*保存设备说明信息*/
  const confirmSaveDescInfo = () => {
    if (descTitle.value == '') {
      message.error('设备的说明标题不能为空！');
      return false;
    } else if (UEditorMsgContent.value == '') {
      message.error('设备的说明内容详情不能为空！');
      return false;
    }
    loading.value = true;
    saveDeviceEquipmentDescMsg({
      deviceId: deviceList.value[seeDeviceIndex.value].id,
      title: descTitle.value,
      content: UEditorMsgContent.value,
    }).then(res => {
      loading.value = false;
      if (res.code === 200) {
        message.success('设备说明信息保存成功！');
        deviceList.value[seeDeviceIndex.value].descriptions.push(res.data)
        cancelModal();
      }
    })
  }

  return {
    typeFormData,typeList,selectedType,addTypeModal,cancelTypeModal,confirmTypeModal,deleteType,selectTypeItemInfo,
    deviceList,seeDeviceIndex,seeDeviceDescIndex,loading,addDrillModal,formData,fileList,upLoading,UEditorMsgContent,
    formDescIndex, addEquipmentModal,cancelModal, handleChange, beforeUpload,confirmEquipmentInfo,seletDescOption,
    addDescOption, closeEquipmentDesc,modifyDeviceInfo, deleteDeviceInfo,addDescModal,descTitle,confirmSaveDescInfo
  }
}