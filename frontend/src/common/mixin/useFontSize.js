import {ref} from "vue";

export default function (userInfo) {
  const fontSizeScale = ref(
    window.localStorage.getItem('fs') ? window.localStorage.getItem('fs') : 0
  )
  const fontSizeVisible = ref(false)

  const reloadWindow = () => {
    window.location.reload(true)
  }
  const settingFontSize = () => {
    window.localStorage.setItem('fs', fontSizeScale.value)
    fontSizeVisible.value = false
    reloadWindow()
  }
  return {
    fontSizeScale,
    fontSizeVisible,
    reloadWindow,
    settingFontSize
  }
}