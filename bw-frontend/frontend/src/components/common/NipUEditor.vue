<template>
  <div class="w-full h-full overflow-auto" ref="ueditor">
    <Editor v-if="init !== null" v-model="content" :init="init"/>
  </div>
</template>
<script>
  export default {
    name: 'NipUEditor'
  }
</script>
<script setup>
  import {ref, inject,defineProps} from 'vue'
  import Editor from '@tinymce/tinymce-vue'

  const content = inject('content')
  const uploadFile = inject('uploadFile')
  const editorConfig = ref(null)
  const props = defineProps({
    top: {
      default: 0,
      type: Number
    }
  })
  const init = {
    language: 'zh-Hans',
    skin: 'tinymce-5-dark',
    content_css: 'tinymce-5-dark',
    height: '100%',
    width: '100%',
    resize: false,
    toolbar_sticky: true,
    toolbar_persist: true,
    branding: false,
    promotion: false,
    toolbar: [
      'removeformat blocks fontfamily fontsize forecolor lineheight subscript superscript strikethrough underline bold italic blockquote link alignnone alignleft aligncenter alignright bullist numlist checklist outdent indent table image media'
    ],
    plugins: 'advlist autolink table lists image media link wordcount',
    file_picker_types: 'file image media', // 设置本参数可以允许/禁用某分类上传
    file_picker_callback: (callback, value, meta) => {
      //文件分类
      let filetype =
          '.zip, .rar, .7z, .pdf, .txt, .doc, .docx, .xls, .xlsx, .ppt, .pptx, .jpg, .jpeg, .png, .gif, .mp3, .mp4, .mov, .wmv, .avi, .3gp'
      //后端接收上传文件的地址
      let upurl = window.uploadFileUrl + '?currentPath=006/upfiles/' + new Date().getTime()
      //为不同插件指定文件类型及后端地址
      switch (meta.filetype) {
        case 'image':
          filetype = '.jpg, .jpeg, .png, .gif'
          break
        case 'media':
          filetype = '.mp3, .mp4, .mov, .wmv, .avi, .3gp'
          break
        case 'file':
        default:
          filetype =
              '.zip, .rar, .7z, .pdf, .txt, .doc, .docx, .xls, .xlsx, .ppt, .pptx'
          break
      }
      //模拟出一个input用于添加本地文件
      let input = document.createElement('input')
      input.setAttribute('type', 'file')
      input.setAttribute('accept', filetype)
      // input.setAttribute('multiple', 'multiple') //多选状态，tinymce这个位置激活的上传并不适合多文件传送
      input.click()
      input.onchange = function () {
        let file = this.files[0]

        let xhr, formData
        formData = new FormData()
        formData.append('files', file, file.name)
        formData.append('currentPath', 'news')

        xhr = new XMLHttpRequest()
        xhr.withCredentials = false
        xhr.open('POST', upurl) // 方式

        xhr.onload = function () {
          if (xhr.status !== 200) {
            console.log('HTTP Error: ' + xhr.status)
            return
          }
          let json = JSON.parse(xhr.responseText)
          console.log(json)
          if (!json || typeof json.data[0] != 'string') {
            console.log('Invalid JSON: ' + xhr.responseText)
            return
          }
          callback(`${window.fileUrl}/${json.data}`, {
            text: "/"+json.data[0]
          })
        }
        xhr.send(formData)
      }
    }
  }
</script>
<style>
  .edui-label,
  .edui-default {
    color: #0a1429;
  }

  .tinymce-box,
  .tox-tinymce {
    width: 100%;
    height: 100%;
  }

  .mce-panel {
    border: none !important;
  }

  .mce-panel,
  .mce-container,
  .mce-content-body {
    background: transparent !important;
  }

  .mce-top-part,
  .mce-statusbar {
    background-color: #fff !important;
  }
</style>
