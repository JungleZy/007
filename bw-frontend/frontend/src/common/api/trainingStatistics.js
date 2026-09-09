import axios from '../http/axios.js'
export const getUserTrainDurationStat = (data) => {
  return axios({
    method: "POST",
    url: '/api/user/getUserTrainDurationStat',
    data
  })
}

export const getRecentHandKeyTrains = (data) => {
  return axios({
    method: "POST",
    url: '/api/user/getRecentHandKeyTrains',
    data
  })
}
export const getRecentElectronicKeyTrains = (data) => {
  return axios({
    method: "POST",
    url: '/api/user/getRecentElectronicKeyTrains',
    data
  })
}