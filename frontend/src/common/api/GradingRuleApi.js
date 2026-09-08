import axios from '../http/axios.js'

export const getGradingRuleListByType = (data) => {
  return axios({
    method: "post",
    url: "/api/gradingRule/getGradingRuleListByType",
    data,
  })
}
export const getGradingRuleById = (data) => {
  return axios({
    method: "post",
    url: "/api/gradingRule/getGradingRuleById",
    data,
  })
}
export const saveGradingRule = (data) => {
  return axios({
    method: "post",
    url: "/api/gradingRule/saveGradingRule",
    data,
  })
}
export const updateGradingRuleStatus = (data) => {
  return axios({
    method: "post",
    url: "/api/gradingRule/updateGradingRuleStatus",
    data,
  })
}
export const changeGradingRuleIsDefault = (data) => {
  return axios({
    method: "post",
    url: "/api/gradingRule/changeGradingRuleIsDefault",
    data,
  })
}

export const deleteGradingRule = (data) => {
  return axios({
    method: "post",
    url: "/api/gradingRule/deleteGradingRule",
    data,
  })
}
