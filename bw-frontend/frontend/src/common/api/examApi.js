import axios from '../http/axios.js'

export const getExamBasicTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/telegraphKeyPatTrain/findByType",
    data
  })
};

export const clearExamBasicTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/telegraphKeyPatTrain/clear",
    data
  })
};

export const saveExamBasicTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/telegraphKeyPatTrain/save",
    data
  })
};

export const totalExamComplexTrainInfo = (data) => {
  return axios({
    method: "post",
    url: "/api/telegraphKeyPatTrainSynthetical/statisticalPage",
    data
  })
};

export const findPrevExamTrainInfo = (data) => {
  return axios({
    method: "post",
    url: "/api/telegraphKeyPatTrainSynthetical/lastTrain",
    data
  })
};

export const addExamComplexTrainInfo = (data) => {
  return axios({
    method: "post",
    url: "/api/telegraphKeyPatTrainSynthetical/save",
    data
  })
};

export const findExamPatTrainById = (data) => {
  return axios({
    method: "post",
    url: "/api/telegraphKeyPatTrainSynthetical/findById",
    data
  })
};

export const beginExamTrainInfo = (data, config) => { return axios({method: "post",
url: "/api/telegraphKeyPatTrainSynthetical/begin",
data, config}) }

export const stopExamTrainInfo = (data, config) => { return axios({method: "post",
url: "/api/telegraphKeyPatTrainSynthetical/stop",
data, config}) }

export const goTopExamTrainInfo = (data, config) => { return axios({method: "post",
url: "/api/telegraphKeyPatTrainSynthetical/goTo",
data, config}) }

export const endExamTrainInfo = (data, config) => { return axios({method: "post",
url: "/api/telegraphKeyPatTrainSynthetical/finish",
data, config}) }
