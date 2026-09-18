<template>
  <div class="w-full h-full">
    <component :is="activeZuXunPage" @changeStatus="changeStatus"></component>
  </div>
</template>
<script>
  export default {
    name: 'ElectronKeyZuXunTrain'
  }
</script>
<script setup>
  import { onMounted, defineAsyncComponent, shallowRef } from 'vue'
  import {useRoute} from 'vue-router'

  const ZuXunTeacher = defineAsyncComponent(()=>import('./teacher/teacher.vue'))
  const ZuXunStudent = defineAsyncComponent(()=>import('./student/student.vue'))
  const ZuXunStudentScore = defineAsyncComponent(()=>import('./student/score.vue'))
  const activeZuXunPage = shallowRef(null)
  const route = useRoute();
  onMounted(() => {
    if (route.query.teacher == '1') {
      activeZuXunPage.value = ZuXunTeacher
    } else if (route.query.status == '2') {
      activeZuXunPage.value = ZuXunStudentScore
    } else {
      activeZuXunPage.value = ZuXunStudent
    }
  })

  const changeStatus = () => {
    activeZuXunPage.value = ZuXunStudentScore;
  }
</script>
<style scoped>

</style>