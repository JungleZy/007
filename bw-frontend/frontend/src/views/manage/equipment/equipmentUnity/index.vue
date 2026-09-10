<template>
    <div class="h-full w-full">
        <UnityEquipment :unityPath="unityPath"></UnityEquipment>
    </div>
</template>
<script>
    export default {
        name: "EquipmentUnity"
    }
</script>
<script setup>
    import UnityEquipment from '../../../../components/unityEquipment/UnityEquipment.vue'
    import {ref,onMounted} from 'vue'
    import {useRoute} from "vue-router";
    const unityPath=ref('J210-742')
    const route = useRoute();
    onMounted(() => {
        if (route.query.deviceType && route.query.deviceType !== '') {
            unityPath.value = route.query.deviceType
        }
    })
</script>

<style scoped>

</style>