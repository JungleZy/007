import axios from '../http/axios.js'

export const findByUserIdAndType = (data) => {
  return axios({
    method: "post",
    url: "/api/enteringTelexPat/findByUserIdAndType",
    data
  })
};
export const hanziSaveRecods = (data) => {
  return axios({
    method: "post",
    url: "/api/enteringTelexPat/save",
    data
  })
};
export const hanziClearRecods = (data) => {
  return axios({
    method: "post",
    url: "/api/enteringTelexPat/clear",
    data
  })
};
