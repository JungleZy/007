import {onMounted, ref, watch,nextTick} from "vue";
export default function () {
  const isTableStriped = ref(false);
  const tableLoading = ref(true);
  const tableSize = ref("default");
  const page = ref({
    showQuickJumper: true,
    showSizeChanger: true,
  });
  onMounted(() => {
    const tSize = window.localStorage.getItem("tableSize");
    const tStriped = window.localStorage.getItem("tableStriped");
    tableSize.value = tSize ? tSize : "default";
    isTableStriped.value = tStriped === "true";
  })
  watch(isTableStriped, () => {
    window.localStorage.setItem("tableStriped", isTableStriped.value);
  })
  const changeTableSize = (size) => {
    tableSize.value = size;
    window.localStorage.setItem("tableSize", size);
  }
  return {
    isTableStriped,
    tableSize,
    tableLoading,
    page,
    changeTableSize
  }

}