import {useRoute, useRouter, onBeforeRouteUpdate} from "vue-router";
import {onMounted, ref, watch} from "vue";
export default function getBackByRouter() {
  const router = useRouter();
  let isShowBackground = ref(false);
  onMounted(() => {
    if (router.options.history.location.startsWith('/preview/dashboard')) {
      isShowBackground.value = false
    } else {
      isShowBackground.value = true
    }
  })

  onBeforeRouteUpdate(to => {
    if (to.fullPath.startsWith('/preview/dashboard')) {
      isShowBackground.value = false
    } else {
      isShowBackground.value = true
    }
  })

  return {
    isShowBackground
  }
}

