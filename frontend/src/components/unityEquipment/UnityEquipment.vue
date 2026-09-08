<template>
    <div class="h-full w-full">
        <div class="h-full w-full videoBox" v-show="isLoading">
            <video :src="loadingV"
                   class="myVideo"
                   autoplay muted loop></video>
        </div>
        <div class="h-full w-full"  v-show="!isLoading">
            <iframe
                    width="100%"
                    height="100%"
                    id="unity"
                    ref="iframeDom"
                    :src="'./'+unityPath+'/index.html'"
                    frameborder="0"></iframe>
        </div>
    </div>
</template>

<script>
    export default {
        name: "UnityEquipment"
    }
</script>

<script setup>
    import {ref,onMounted,onBeforeUnmount,defineProps,defineEmits} from 'vue'
    import loadingV from '../../assets/HJ/loading.mp4'

    const iframeDom=ref(null);
    const props = defineProps({
        unityPath: {
            type: String,
            default: true
        }
    });
    console.log(55555555)
    const isLoading=ref(true);
    const emit=defineEmits(['addParam','isShow'])
    onMounted(()=>{
        window.addEventListener('unityWatch',unityWatch)
        window.addEventListener('equipmentLoading',equipmentLoading)
    })
    onBeforeUnmount(()=>{
        window.removeEventListener('unityWatch',unityWatch)
        window.removeEventListener('equipmentLoading',equipmentLoading)
    })
    //监听设备加载中
    const equipmentLoading=(e)=>{
        isLoading.value=false;
        emit('isShow')
    }
    //接收unity消息
    const unityWatch=(e)=>{
        addParam(JSON.parse(e.detail))
    }
    //
    const addParam=(e)=>{
        emit('addParam',e)
    }
    //给unity发送消息
    const unitySend=()=>{
        iframeDom.value.contentWindow.send('DWM')
    }
</script>

<style scoped>
    .myVideo {
        max-height: 100%;
        max-width: 100%;
        object-fit: fill;
    }
    .videoBox {
        display: flex;
        align-items: center;
        justify-content: center;
        background-color: #0c1630;
    }
</style>