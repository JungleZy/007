import {ref} from "vue";
import {message} from "ant-design-vue";

export default function (callback) {
  const fileList = ref([]);
  const uploadLoading = ref(false);

  const handleChange = (info) => {
    if (info.file.status === 'uploading') {
      uploadLoading.value = true;
      return;
    }
    if (info.file.status === 'done') {
      callback(`/${info.file.response.data}`);
    }
    if (info.file.status === 'error') {
      uploadLoading.value = false;
      message.error('上传错误');
    }
  };
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

  return {
    fileList,
    uploadLoading,
    fileUrl: window.fileUrl,
    uploadFileUrl: window.uploadFileUrl,
    handleChange,
    beforeUpload,
  }
}