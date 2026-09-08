import axios from '../http/axios.js'

export const wordingAdd = (data) => {
  return axios({
    method: "post",
    url: "/api/postRadiotelephoneTrain/add",
    data
  })
};
export const listPage = (data) => {
  return axios({
    method: "post",
    url: "/api/postRadiotelephoneTrain/listPge",
    data
  })
};
export const wordinngDetails = (data) => {
  return axios({
    method: "post",
    url: "/api/postRadiotelephoneTrain/details",
    data
  })
};
export const wordinngBegin = (data) => {
  return axios({
    method: "post",
    url: "/api/postRadiotelephoneTrain/begin",
    data
  })
};
export const wordinngFinish = (data) => {
  return axios({
    method: "post",
    url: "/api/postRadiotelephoneTrain/finish",
    data
  })
};

export const apiPostTrainGlobalRuleAddRule = (data) => {
  return axios({
    method: "post",
    url: "/api/postTrainGlobalRule/addRule",
    data
  })
};

export const apiPostTrainGlobalRuleDeleteById= (data) => {
  return axios({
    method: "post",
    url: "/api/postTrainGlobalRule/deleteById",
    data
  })
};

export const apiPostTrainGlobalRuleType = (data) => {
  return axios({
    method: "post",
    url: "/api/postTrainGlobalRule/findByType",
    data
  })
};
export const deleteList = (data) => {
  return axios({
    method: "get",
    url: `/api/postRadiotelephoneTrain/delete?trainId=${data}`,
  })
};