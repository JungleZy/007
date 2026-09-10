<template>
  <div class="w-full h-full bg-white relative">
    <div id="luckysheet" class="w-full h-full"></div>
    <input v-show="false"
           ref="fileRef"
           @click="e=>{e.target.value = '';}"
           @change="getFileData"
           type="file"
           id="fileInput"/>
  </div>
</template>

<script>
import {onMounted, ref, defineComponent} from "vue";
import {message} from 'ant-design-vue';
import {createFromIconfontCN} from "@ant-design/icons-vue";
import {mergeExcelInfo} from "../../common/utils/Utils.js";
import * as R from 'ramda';

const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl,
});
export default defineComponent({
  components: {IconFont},
  setup() {
    const fileRef = ref(null);
    const isShow = ref(true);
    const handleIsShow = (d) => {
      isShow.value = d
    }
    let excelData = [];
    const handleExcelData = (data, t) => {
      if (excelData.length === 0) {
        excelData = data;
      } else {
        data.map(item => {
          excelData.push(item);
        })
      }
      if (excelData.length > 1) {
        excelData[0] = mergeExcelInfo(excelData);
      }

      window.luckysheet.destroy();
      window.luckysheet.create({
        container: 'luckysheet',
        title: '',
        lang: 'zh',
        data: data,
        loading: {
          image: `${window.fileUrl}/cdn/luckysheet/css/loading.gif`,
          text: "数据加载中"
        },
        allowEdit: R.isNil(t) ?  true: t, // 是否可以编辑
        showtoolbar: R.isNil(t) ?  true: t, // 显示工具栏
        sheetFormulaBar: R.isNil(t) ?  true: t, // 是否显示公式栏
        showinfobar: false, // 顶部信息栏
        showsheetbar: true, // 底部sheet按钮
        showsheetbarConfig: {
          add: R.isNil(t) ?  true: t,
        },
        showtoolbarConfig: {
          excelImport: true, // 是否可以导入
          excelExport: false,// 是否可以导出
          import: () => {
            uploadFile()
          },
        }
      })
    }
    const handleExcelData2 = (data) => {
      window.luckysheet.create(data)
    }
    const uploadFile = () => {
      fileRef.value.dispatchEvent(new MouseEvent("click"));
    }
    const getFileData = () => {
      let files = fileRef.value.files;
      if (files == null || files.length === 0) {
        message.error("禁止导入空文件")
        return;
      }
      let name = files[0].name;
      let suffixArr = name.split("."), suffix = suffixArr[suffixArr.length - 1];
      if (suffix !== "xlsx") {
        message.error("系统暂时仅支持后缀名为xlsx的Excel文档")
        return;
      }
      LuckyExcel.transformExcelToLucky(files[0], (exportJson, luckySheetFile) => {
        if (exportJson.sheets == null || exportJson.sheets.length === 0) {
          message.error("您所选择的Excel文档为空")
          return;
        }
        handleExcelData(exportJson.sheets);
      })
    }
    const getExcelData = () => {
      return window.luckysheet.toJson();
    }
    return {
      fileRef,
      isShow,
      uploadFile,
      getFileData,
      getExcelData,
      handleExcelData,
      handleExcelData2,
      handleIsShow
    }
  }
})

</script>

<style>
#luckysheet * {
  box-sizing: revert !important;
}

#luckysheet input {
  padding: 0 !important;
  background: transparent;
}

#luckysheet .luckysheet-toolbar-textinput {
  padding: 1px 0 1px 8px !important;
}

#luckysheet .luckysheet-datavisual-config-input, .luckysheet-datavisual-config-input-no {
  background: #fff !important;
  line-height: 24px !important;
  padding: 3px !important;
}
</style>