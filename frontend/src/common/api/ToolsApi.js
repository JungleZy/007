import axios from '../http/axios.js'

export const getNowTime = () => {
  return axios({
    method: "get",
    url: "/api/tools/getNowTime",
  })
}
export const getTwelvemonth = () => {
  return axios({
    method: "get",
    url: "/api/tools/getTwelvemonth",
  })
}