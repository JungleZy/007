import axios from '../http/axios.js'

export const findKeyPointsByType = (data) => {
   return axios({
      method: "post",
      url: "/api/keyPoints/findKeyPointsByType",
      data
   })
}
export const saveKeyPoints = (data) => {
   return axios({
      method: "post",
      url: "/api/keyPoints/saveKeyPoints",
      data
   })
}

export const findReceivePointsByType = (data) => {
   return axios({
      method: "post",
      url: "/api/receiveKeyPoints/getByType",
      data
   })
}
export const findHanziByType = (data) => {
  return axios({
    method: "post",
    url: "/api/enteringKeyPoints/getByType",
    data
  })
}
export const saveReceivePoints = (data) => {
   return axios({
      method: "post",
      url: "/api/receiveKeyPoints/save",
      data
   })
}
export const saveHanziPoints = (data) => {
  return axios({
    method: "post",
    url: "/api/enteringKeyPoints/save",
    data
  })
}
