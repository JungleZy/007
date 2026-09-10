import { ref, onMounted } from 'vue'
import { findHanziByType, saveHanziPoints } from '../../../../../../common/api/ExplainApi'
import num1 from '../../../../../../assets/HJ/explain/num1.png'
import num2 from '../../../../../../assets/HJ/explain/num2.png'
import num3 from '../../../../../../assets/HJ/explain/num3.png'
import num11 from '../../../../../../assets/HJ/explain/num11.png'
import num22 from '../../../../../../assets/HJ/explain/num22.png'
import num33 from '../../../../../../assets/HJ/explain/num33.png'
import { message, Modal } from 'ant-design-vue'
export default function explain() {
  const listData = ref([])
  const active = ref(0)
  const activeS = ref(0)
  const newKnowledge = ref({ name: '', content: '' })
  const addDrillModal = ref(false)
  const clickId = ref('')
  const fileList = ref([])
  const uploadLoading = ref(false)
  onMounted(() => {
    labelClick(0)
  })
  const clickTitle = e => {
    active.value = e
  }
  const labelClick = e => {
    activeS.value = e
    clickTitle(0)
    listData.value = []
    clickId.value = ''
    findHanziByType({ type: e }).then(res => {
      if (res.data && res.data.content != null) {
        listData.value = JSON.parse(res.data.content)
        clickId.value = res.data.id
      }
    })
  }
  const addMainPoints = () => {
    if (newKnowledge.value.type == 1 || newKnowledge.value.type == 2 || newKnowledge.value.type == 3) {
      let list = []
      fileList.value.forEach(e => {
        list.push({
          uid: e.uid,
          name: e.name,
          status: e.status,
          url: e.url ? e.url : newKnowledge.value.type == 1 ? e.response.data[0]:e.response.data
        })
      })
      newKnowledge.value.content = list
    }
    if (!newKnowledge.value.name) {
      message.warning('请填写要点名')
      return
    }
    if (!newKnowledge.value.content) {
      message.warning('请填写要点内容')
      return
    }
    if (newKnowledge.value.update) {
      listData.value[active.value] = {
        name: newKnowledge.value.name,
        content: newKnowledge.value.content,
        type: newKnowledge.value.type
      }
    } else {
      listData.value.push(newKnowledge.value)
    }
    addDrillModal.value = false
    let obj = {
      type: activeS.value,
      id: clickId.value,
      content: JSON.stringify(listData.value)
    }
    saveHanziPoints(obj).then(res => {
      if (res.code === 200) {
        if (newKnowledge.value.update) {
          message.success('修改成功')
        } else {
          message.success('新增成功')
        }
      }
    })
  }
  const openAddMainPoints = e => {
    addDrillModal.value = true
    newKnowledge.value = { name: '', content: '', type: '0' }
    fileList.value = []
    newKnowledge.value.update = false
  }
  const modifyMainPoints = e => {
    addDrillModal.value = true
    newKnowledge.value = listData.value[active.value]
    newKnowledge.value.update = true
    if (newKnowledge.value.type != 0) {
      let arr = JSON.parse(JSON.stringify(listData.value[active.value].content))
      arr.forEach(e => {
        e.thumbUrl = window.fileUrl + '/' + e.url
      })
      fileList.value = arr
    }
  }
  const deleteMainPoints = e => {
    Modal.confirm({
      title: () => '您确定删除' + listData.value[active.value].name + '该要点么?',
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        if (listData.value.length < 2) {
          message.warning('请至少保留一个要点')
          return
        }
        listData.value.splice(active.value, 1)
        active.value = 0
        let obj = {
          type: activeS.value,
          id: clickId.value,
          content: JSON.stringify(listData.value)
        }
        saveHanziPoints(obj).then(res => {
          if (res.code === 200) {
            message.success('删除成功')
          }
        })
      }
    })
  }
  const clickMainPoints = e => {
    newKnowledge.value.type = e
    fileList.value = []
  }
  const handleChange = info => {
    if (newKnowledge.value.type == 1) {
      const isJpgOrPng = info.file.type === 'video/mp4'
      if (!isJpgOrPng) {
        fileList.value = fileList.value.slice(0, -1)
      }
    } else if (newKnowledge.value.type == 2) {
      const isJpgOrPng = info.file.type === 'image/jpeg' || info.file.type === 'image/png'
      if (!isJpgOrPng) {
        fileList.value = fileList.value.slice(0, -1)
      }
    } else if (newKnowledge.value.type == 3) {
      const isJpgOrPng = info.file.type === 'audio/mpeg'
      if (!isJpgOrPng) {
        fileList.value = fileList.value.slice(0, -1)
      }
    }
    if (info.file.status === 'uploading') {
      uploadLoading.value = true
      return
    }
    if (info.file.status === 'done') {
      message.success('上传成功')
    }
    if (info.file.status === 'error') {
      uploadLoading.value = false
      message.error('上传错误')
    }
  }
  const beforeUpload = file => {
    if (newKnowledge.value.type == 1) {
      const isJpgOrPng = file.type === 'video/mp4'
      if (!isJpgOrPng) {
        message.error('上传文件格式错误，只支持mp4格式的视频!')
      }
      return isJpgOrPng
    } else if (newKnowledge.value.type == 2) {
      const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
      if (!isJpgOrPng) {
        fileList.value = []
        message.error('上传文件格式错误，只支持jpg或者png图片!')
      }
      return isJpgOrPng
    } else if (newKnowledge.value.type == 3) {
      const isJpgOrPng = file.type === 'audio/mpeg'
      if (!isJpgOrPng) {
        fileList.value = []
        message.error('上传文件格式错误，只支持mp3格式的音频!')
      }
      return isJpgOrPng
    }
  }
  return {
    uploadFileUrl: window.uploadFileUrl+'?currentPath=mainPoints',
    fileUrl: window.fileUrl,
    fileList,
    listData,
    active,
    activeS,
    addDrillModal,
    newKnowledge,
    clickTitle,
    labelClick,
    openAddMainPoints,
    modifyMainPoints,
    deleteMainPoints,
    addMainPoints,
    clickMainPoints,
    handleChange,
    beforeUpload
  }
}
