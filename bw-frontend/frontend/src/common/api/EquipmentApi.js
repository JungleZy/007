import axios from '../http/axios.js'

export const getAllDeviceType = (data) => {
  return axios({
    method: 'post',
    url: '/api/deviceType/findAll',
    data
  })
}

export const updateDeviceType = (data) => {
  return axios({
    method: 'post',
    url: '/api/deviceType/save',
    data
  })
}

export const deleteDeviceType = (data) => {
  return axios({
    method: 'post',
    url: '/api/deviceType/delete',
    data
  })
}

export const getDeviceTypeEquipment = (data) => {
  return axios({
    method: 'post',
    url: '/api/device/listPage',
    data
  })
}

export const saveDeviceEquipmentMsg = (data) => {
  return axios({
    method: 'post',
    url: '/api/device/save',
    data
  })
}

export const deleteDeviceEquipment = (data) => {
  return axios({
    method: 'post',
    url: '/api/device/delete',
    data
  })
}

export const saveDeviceEquipmentDescMsg = (data) => {
  return axios({
    method: 'post',
    url: '/api/device/addDeviceDescription',
    data
  })
}
