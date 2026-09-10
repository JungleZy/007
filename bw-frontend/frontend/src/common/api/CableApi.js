import axios from '../http/axios.js'

export const getCableAll = (data) => {
  return axios({
    method: "post",
    url: "/api/cable/find",
    data
  })
}
export const getTwelvemonth = () => {
  return axios({
    method: "get",
    url: "/api/tools/getTwelvemonth",
  })
}

export const getCableType = () => {
  return axios({
    method: "post",
    url: "/api/cable/type/find",
  })
}
export const saveCableType = (data) => {
  return axios({
    method: "post",
    url: "/api/cable/type/save",
    data
  })
}
export const deleteCableTypeByID = (data) => {
  return axios({
    method: "post",
    url: `/api/cable/type/delete?id=${data}`,
  })
}

export const getCableAllByID = (data) => {
  return axios({
    method: "post",
    url: "/api/cable/findById",
    data
  })
}
export const getCableFloorAllByID = (data) => {
  return axios({
    method: "post",
    url: "/api/cable/floor/find",
    data
  })
}
export const saveMesage = (data) => {
  return axios({
    method: "post",
    url: "/api/cable/save",
    data
  })
}
export const deleteList = (data) => {
  return axios({
    method: "post",
    url: `/api/cable/delete?id=${data}`,
  })
};