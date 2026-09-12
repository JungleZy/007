import axios from '../http/axios.js'
export const getUserTrainDurationStat = (data) => {
  return axios({
    method: "POST",
    url: '/api/user/getUserTrainDurationStat',
    data
  })
}

// 最近十次统计：统计对象由后端按 token 推导，无请求体
export const getRecentHandKeyTrains = () => {
  return axios({
    method: "POST",
    url: '/api/user/getRecentHandKeyTrains'
  })
}
export const getRecentElectronicKeyTrains = () => {
  return axios({
    method: "POST",
    url: '/api/user/getRecentElectronicKeyTrains'
  })
}