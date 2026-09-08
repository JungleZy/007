import {onMounted, ref} from "vue";

export default function (userInfo) {
  const greetings = ref("");
  const getGreetings = () => {
    let h = new Date().getHours();
    if (h <= 4) {
      greetings.value = `亲爱的${userInfo.value.userName}，夜已深啦！`;
    } else if (h <= 7) {
      greetings.value = `亲爱的${userInfo.value.userName}，早上好呀！`;
    } else if (h <= 12) {
      greetings.value = `亲爱的${userInfo.value.userName}，上午好呀！`;
    } else if (h <= 13) {
      greetings.value = `亲爱的${userInfo.value.userName}，中午好呀！`;
    } else if (h <= 18) {
      greetings.value = `亲爱的${userInfo.value.userName}，下午好呀！`;
    } else if (h <= 21) {
      greetings.value = `亲爱的${userInfo.value.userName}，晚上好哟！`;
    } else {
      greetings.value = `晚安全世界，晚安${userInfo.value.userName}！`;
    }
  }
  getGreetings();
  return {
    greetings
  }
}