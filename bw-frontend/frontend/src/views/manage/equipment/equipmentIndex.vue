<template>
    <div class="h-full w-full">
        <div class="w-full h-full" v-if="openRoute">
            <div class="equipmentMain">
                <div class="imgBox">
                    <div class="equipBox">
                        <img v-if="deviceList[seeDeviceIndex]" :src="fileUrl+deviceList[seeDeviceIndex].deviceImg"
                             class="img" @click="seeEquipmentUnity(deviceList[seeDeviceIndex])">
                        <div class="name nobr" @click="seeEquipmentUnity(deviceList[seeDeviceIndex])">
                            {{deviceList[seeDeviceIndex]?deviceList[seeDeviceIndex].deviceName:'暂无设备'}}
                        </div>
                    </div>
                </div>
                <div class="textBox">
                    <div class="tabs" v-if="deviceList[seeDeviceIndex]">
                        <div v-for="(tab,t) in deviceList[seeDeviceIndex].descriptions" :key="t"
                             :class="{tabBtn: true, on: seeDeviceDescIndex==t}" @click="seeDeviceDescIndex = t">{{tab.title}}</div>
                        <div class="tabBtn" v-if="userRole.id != '2'" @click="addDescModal = true">
                            <PlusOutlined class="mr-1"/>新增说明
                        </div>
                    </div>

                    <div v-html="sanitizeHtml(deviceList[seeDeviceIndex].descriptions[seeDeviceDescIndex].content)"></div>
                </div>
            </div>
            <div class="equipListBox">
                <div class="bg"></div>
                <div class="typeBox">
                    <div class="ico"></div>
                    <div class="name nobr" :title="selectedType.typeName">{{selectedType.typeName}}</div>
                    <div class="typeList">
                        <div class="typeItem add" @click="addTypeModal()" v-if="userRole.id != '2'">
                            <PlusOutlined class="mr-1"/>新增分类
                        </div>
                        <div class="overflow-auto" style="max-height: 300px">
                            <template v-for="(type, t) in typeList" :key="t">
                                <div :class="{'typeItem nobr': true, on: selectedType.id==type.id}" :title="type.typeName"
                                     @click="selectTypeItemInfo(type)">
                                    <div class="typeIco" v-if="userRole.id != '2'">
                                        <IconFont type="icon-shanchu1" style="color: red" class="del" title="删除"
                                                  @click.stop="deleteType(type)"></IconFont>
                                        <IconFont type="icon-edit" style="color: #05b65b" class="edit" title="编辑"
                                                  @click.stop="addTypeModal(type)"></IconFont>
                                    </div>
                                    {{type.typeName}}
                                </div>
                            </template>
                        </div>
                    </div>
                </div>
                <div class="listBox">
                    <div class="switchL" @click="moveDeviceInfo(-1)"></div>
                    <div class="switchR" @click="moveDeviceInfo(1)"></div>
                    <div class="deviceListBox" ref="deviceListBoxRef">
                        <template v-for="(dev,d) in deviceList" :key="d">
                            <div :class="{item: true, on: seeDeviceIndex==d}" @click="seeDeviceIndex = d">
                                <div class="deviceIco" v-if="userRole.id != '2'">
                                    <IconFont type="icon-shanchu1" style="color: red" class="del" title="删除"
                                              @click.stop="deleteDeviceInfo(dev)"></IconFont>
                                    <IconFont type="icon-edit" style="color: #05b65b" class="edit" title="编辑"
                                              @click.stop="modifyDeviceInfo(dev)"></IconFont>
                                </div>
                                <img :src="fileUrl+dev.deviceImg" class="img">
                                <div class="name nobr" :title="dev.deviceName">{{dev.deviceName}}</div>
                            </div>
                        </template>
                    </div>
                    <div class="item" @click="addEquipmentModal()" v-if="userRole.id != '2'">
                        <PlusOutlined class="ico"/>
                        <div class="name nobr" title="HTJW-412A电台">添加设备</div>
                    </div>
                </div>
            </div>
            <!--设备分类-->
            <a-modal :destroyOnClose="true"
                     :width="350"
                     class="init_modal_style footer-border-none"
                     destroyOnClose="true"
                     v-model:visible="typeFormData.visible">
                <template #title>
                    <strong>{{typeFormData.type==0?'新增':'编辑'}}设备分类</strong>
                </template>
                <template #footer>
                    <div class="layout-right-center" >
                        <a-button @click="cancelTypeModal()">取消</a-button>
                        <a-button @click="confirmTypeModal()">确定</a-button>
                    </div>
                </template>
                <div  style="padding: 20px 20px;color: white">
                    <span>分类名称：</span>
                    <a-input v-model:value="typeFormData.name" placeholder="请输入分类名称" style="width: 214px"></a-input>
                </div>
            </a-modal>
            <!--添加设备-->
            <a-modal :destroyOnClose="true"
                     class="init_modal_style footer-border-none addEquipmentModal"
                     destroyOnClose="true"
                     v-model:visible="addDrillModal"
                     @cancel="cancelModal">
                <template #title>
                    <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">添加设备</strong>
                </template>
                <template #footer>
                    <div class="w-full layout-center">
                        <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
                             @click="confirmEquipmentInfo()" :style="{fontSize: (fs * 2 + 15) + 'px'}">
                            <a-spin v-if="loading" size="small"/> 确认保存
                        </div>
                    </div>
                </template>
                <a-spin :spinning="loading">
                    <div class="configurationBox">
                        <a-row>
                            <a-col :span="10">
                                <div class="rowItem" style="align-items: flex-start;">
                                    <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">设备图片：</div>
                                    <div class="item" style="width: 240px;margin: 5px 0;">
                                        <button-style>
                                            <template v-slot:content>
                                                <div class="w-full equipmentImgBox" style="height: 118px;min-width: 100px;max-width: 100%">
                                                    <a-upload v-model:file-list="fileList"
                                                              name="file"
                                                              list-type="picture-card"
                                                              class="avatar-uploader"
                                                              :show-upload-list="false"
                                                              :action="uploadFileUrl+'?currentPath=006/cover/main/'+new Date().getTime()"
                                                              :before-upload="beforeUpload"
                                                              @change="handleChange">
                                                        <img v-if="formData.deviceImg" :src="fileUrl+formData.deviceImg"
                                                             style="width: 100%;max-height: 106px;"/>
                                                        <div v-else>
                                                            <loading-outlined v-if="upLoading" style="font-size: 30px"></loading-outlined>
                                                            <plus-outlined v-else style="font-size: 30px"></plus-outlined>
                                                            <div class="ant-upload-text mt-2">上传</div>
                                                        </div>
                                                    </a-upload>
                                                </div>
                                            </template>
                                        </button-style>
                                    </div>
                                </div>
                            </a-col>
                            <a-col :span="14">
                                <div class="rowItem">
                                    <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">所属分类：</div>
                                    <div class="item" style="width: 300px;">
                                        <a-select v-model:value="formData.deviceTypeId" placeholder="请选择设备类型" style="width: 300px;text-align: left">
                                            <a-select-option v-for="(type, t) in typeList" :key="t" :value="type.id">
                                                {{type.typeName}}
                                            </a-select-option>
                                        </a-select>
                                    </div>
                                </div>
                                <div class="rowItem">
                                    <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">设备编码：</div>
                                    <div class="item" style="width: 300px;">
                                        <a-input onkeyup="value=value.replace(/[^A-Za-z0-9]/ig,'')" :maxlength="10" v-model:value="formData.deviceNumber" placeholder="设备编码"></a-input>
                                    </div>
                                </div>
                                <div class="rowItem">
                                    <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">设备名称：</div>
                                    <div class="item" style="width: 300px;">
                                        <a-input v-model:value="formData.deviceName" :maxLength="20"  placeholder="设备名称"></a-input>
                                    </div>
                                </div>
                            </a-col>
                        </a-row>
                        <div class="messageBox">
                            <div class="title">设备说明</div>
                            <div class="w-full layout-side" style="height: calc(100vh - 370px)">
                                <div class="h-full msgLeft">
                                    <div class="item_group btn big mb-2" @click="addDescOption()" style="justify-content: center;">
                                        <PlusOutlined/> 新增说明项
                                    </div>
                                    <div class="w-full overflow-auto" style="height: calc(100% - 40px)">
                                        <div class="w-full layout-left-center">
                                            <div v-for="(msg,m) in formData.descriptions" :key="m"
                                                 :class="{msgItem:true, active: m == formDescIndex}"
                                                 @click="seletDescOption(msg,m)">
                                                <div class="w-full nobr">{{msg.title}}</div>
                                                <CloseCircleOutlined class="close" @click.stop="closeEquipmentDesc(m)"/>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div class="msgUEditorBox overflow-hidden relative">
                                    <div class="rowItem">
                                        <a-input v-model:value="formData.descriptions[formDescIndex].title"
                                                 placeholder="说明标题"></a-input>
                                    </div>
                                    <div class="w-full" style="height: calc(100% - 32px);background: #183a66;">
                                        <NipUEditor :top="0"/>
                                        <div v-if="uploadType" class="layout-center UploadLoading" >
                                            文件读取中...
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <a-upload name="file" style="display: none" :action="action" :showUploadList="false"
                                  accept=".txt,.md,.csv" :headers="headers" :before-upload="beforeUploadFile"
                                  @change="uploadChange" >
                            <a-button id="uploadBtn" style="display: none">上传</a-button>
                        </a-upload>
                    </div>
                </a-spin>
            </a-modal>
            <!--添加设备说明-->
            <a-modal :destroyOnClose="true"
                     class="init_modal_style footer-border-none addEquipmentModal"
                     destroyOnClose="true"
                     v-model:visible="addDescModal"
                     @cancel="cancelModal">
                <template #title>
                    <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">添加设备说明</strong>
                </template>
                <template #footer>
                    <div class="w-full layout-center">
                        <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
                             @click="confirmSaveDescInfo()" :style="{fontSize: (fs * 2 + 15) + 'px'}">
                            <a-spin v-if="loading" size="small"/> 确认保存
                        </div>
                    </div>
                </template>
                <a-spin :spinning="loading">
                    <div class="configurationBox">
                        <div class="rowItem">
                            <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">说明标题：</div>
                            <div class="item" style="width: 300px;">
                                <a-input v-model:value="descTitle" placeholder="请输入说明标题"></a-input>
                            </div>
                        </div>
                        <div class="msgUEditorItem overflow-hidden relative mt-4" style="height: calc(100vh - 280px)">
                            <div class="w-full h-full" style="background: #183a66;">
                                <NipUEditor :top="0"/>
                                <div v-if="uploadType" class="layout-center UploadLoading" >
                                    文件读取中...
                                </div>
                            </div>
                        </div>

                        <a-upload name="file" style="display: none" :action="action" :showUploadList="false"
                                  accept=".txt,.md,.csv" :headers="headers" :before-upload="beforeUploadFile"
                                  @change="uploadChange" >
                            <a-button id="uploadBtn" style="display: none">上传</a-button>
                        </a-upload>
                    </div>
                </a-spin>
            </a-modal>
        </div>
        <router-view v-else/>
    </div>
</template>
<script>
    export default {
        name: "EquipmentOperation"
    }
</script>
<script setup>
    import NipUEditor from "../../../components/common/NipUEditor.vue";
    import {provide, ref, watch, onMounted, onUnmounted} from 'vue'
    import {createFromIconfontCN, PlusOutlined, LoadingOutlined, CloseCircleOutlined} from "@ant-design/icons-vue";
    import equipmentJS from './equipmentIndex.js'
    import {apiUrl} from '../../../common/http/endpoint.js'
    import {useRouter,useRoute} from "vue-router";
    import {message} from 'ant-design-vue'
    import {sanitizeHtml} from '../../../common/utils/sanitizeHtml.js'
    import {isUploadSizeAllowed, uploadSizeMessage} from '../../../common/utils/uploadLimits.js'

    const fs = ref(JSON.parse(localStorage.getItem('fs')));
    const userRole = ref(JSON.parse(localStorage.getItem('userRole')));
    const uploadFileUrl = ref(window.uploadFileUrl);
    const action = ref(apiUrl('/api/theoryKnowledge/uploadFileToNip'))
    const token = window.localStorage.getItem('token');
    const deviceId = window.localStorage.getItem('deviceId');
    const headers = ref({token,deviceId})
    const uploadType = ref(false)
    const fileUrl = ref(window.fileUrl);
    const openRoute=ref(true);
    const router = useRouter()
    const route = useRoute()
    const IconFont = createFromIconfontCN({
        scriptUrl: window.iconUrl,
    });
    const deviceListBoxRef = ref(null);
    watch(route,()=>{
        if(route.matched[route.matched.length-1].path==='/preview/equipmentOperationHJBW/equipmentUnityHJBW'){
            openRoute.value=false;
        } else {
            openRoute.value=true;
        }
    })
    const {
        typeFormData,typeList,selectedType,addTypeModal,cancelTypeModal,confirmTypeModal,deleteType,selectTypeItemInfo,
        deviceList,seeDeviceIndex,seeDeviceDescIndex,loading,addDrillModal,formData,fileList,upLoading,UEditorMsgContent,
        formDescIndex, addEquipmentModal,cancelModal, handleChange, beforeUpload,confirmEquipmentInfo,seletDescOption,
        addDescOption, closeEquipmentDesc,modifyDeviceInfo, deleteDeviceInfo, addDescModal,descTitle,confirmSaveDescInfo
    } = equipmentJS();

    onMounted(() => {
        if (route.matched[route.matched.length-1].path==='/preview/equipmentOperationHJBW') {
            openRoute.value=true;
        }else if (route.matched[route.matched.length-1].path==='/preview/equipmentOperationHJBW/equipmentUnityHJBW'){
            openRoute.value=false;
        }
        deviceListBoxRef.value.addEventListener('mousewheel', e => {
            moveDeviceInfo(e.deltaY)
        });
    })

    const moveDeviceInfo = (e) => {
        if (e > 0) {
            deviceListBoxRef.value.scrollLeft += 200;
        } else {
            deviceListBoxRef.value.scrollLeft -= 200;
        }
    }

    const seeEquipmentUnity = (item) => {
        router.push({
            path: route.matched[2].path + '/equipmentUnityHJBW',
            query: {deviceType: item.deviceNumber}
        })
    }
    const uploadFile = ()=>{
        const q = document.getElementById("uploadBtn")
        q.click()
    }
    const beforeUploadFile = file => {
        if (!/\.(txt|md|csv)$/i.test(file.name || '')) {
            message.error('仅支持 UTF-8 纯文本文档（txt/md/csv）')
            return false
        }
        if (!isUploadSizeAllowed(file)) {
            message.error(uploadSizeMessage())
            return false
        }
        uploadType.value = true
        return true
    }
    const uploadChange = ({file}) => {
        if (file.status !== 'done' && file.status !== 'error') return
        uploadType.value = false
        const response = file.response
        if (file.status === 'error' || response?.code !== 200) {
            file.status = 'error'
            message.error(response?.message || '文档上传失败，请重试')
            return
        }
        const text = response.data?.wordContent
        if (typeof text !== 'string' || !text.trim()) {
            file.status = 'error'
            message.error('文档内容为空或响应格式错误')
            return
        }
        const paragraph = document.createElement('p')
        paragraph.style.textIndent = '2em'
        UEditorMsgContent.value += text.split(/\r\n|\r|\n/).map(line => {
            paragraph.textContent = line
            return paragraph.outerHTML
        }).join('')
    }

    provide('content', UEditorMsgContent);
    provide('uploadFile', uploadFile);
</script>

<style scoped lang="less">
    @import "./equipmentIndex.less";
</style>