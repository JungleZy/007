<template>
  <div class="w-full" style="height: 30px;padding: 0 12px 0 0;margin-bottom: 12px">
    <div class="h-full w-full layout-side" style="background:rgba(24, 45, 86, 0.7)">
      <div class="h-full layout-left-center">
        <div style="background: rgba(110,189,255,.2);height: 100%;line-height: 30px;padding: 0 12px 0 12px;font-size: 14px;cursor: pointer"
            @click="goBack">
          <IconFont type="icon-rollback"
                    style="font-size: 20px;vertical-align: middle;margin-bottom: 4px;margin-right: 6px;color: #a2afc0"></IconFont>
          <span>返回</span>
        </div>
        <div class="triangle">

        </div>
        <div style="color: #7b90af;height: 100%;line-height: 30px;padding: 0 0 0 20px;font-size: 13px">
          您的位置：
        </div>
        <a-breadcrumb separator="" style="height: 30px;line-height: 30px;font-size: 13px;">
          <template v-for="(item,index) in route.matched">
            <a-breadcrumb-item href="" v-if="item.meta.isBread" @click="skipDetails(item)">
              <span style="text-decoration: underline">{{ item.meta.title }}</span>
              <a-breadcrumb-separator v-if="index!==0"><span
                  style="color:#7b90af;font-size: 13px ">></span></a-breadcrumb-separator>
            </a-breadcrumb-item>
            <a-breadcrumb-item v-else style="cursor: default;color: #7b90af">
              {{ item.meta.title }}
              <a-breadcrumb-separator v-if="index!==0">
                <span style="color:#7b90af;font-size: 13px ">></span>
              </a-breadcrumb-separator>
            </a-breadcrumb-item>
          </template>
        </a-breadcrumb>
      </div>
      <div class="h-full layout-right-center"></div>
    </div>
  </div>
</template>

<script>
export default {
  name: "BreadCrumb"
}
</script>
<script setup>
import {useRouter, useRoute} from 'vue-router'
import {createFromIconfontCN} from '@ant-design/icons-vue';
import {watch, ref} from 'vue'

const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl,
});
const router = useRouter();
const route = useRoute();
const pc = ref([
  '/preview/systemManage',
  '/preview/basicTheoretical/theoryTest/theoryTest',
  '/preview/basicSkill/preJob/telegram',
  '/preview/basicSkill/preJob/receive',
  '/preview/basicSkill/preJob/ditto',
    '/preview/basicSkill/postJob/ditto'
])
const skipDetails = (e) => {
  if (e.path === '/preview') {
    e.path = '/preview/dashboard'
  }
  const pc = ['/preview/basicTheoretical']
  if (pc.indexOf(e.path) > -1) {
    router.push({
      path: e.path
    })
  } else if(e.path.indexOf("basicSkill")>-1){
    router.push({
      path: e.path,
    })
  }else {
    router.push({
      path: e.path,
      query: route.query
    })
  }
}
watch(route, () => {
  if (pc.value.indexOf(route.path) > -1) {
    router.go(-1)
  }
})
const goBack = () => {
  router.go(-1)
}
</script>

<style scoped>
.triangle {
  border-bottom: 0 solid transparent;
  border-top: 30px solid transparent;
  border-left: 10px solid rgba(110, 189, 255, .2);
  border-right: 0 solid transparent;

}
</style>