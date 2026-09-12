import axios from '../http/axios.js'

export const getBasicSetting = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrainSetting/findAll",
    data
  })
};

export const saveBasicSetting = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrainSetting/addOrUpdate",
    data
  })
};

export const saveTelegramTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/add",
    data
  })
};

export const getAllTelegramTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/listPage",
    data
  })
};

export const getTelegramTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/getById",
    data
  })
};

export const startReceiveTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/begin",
    data
  })
};

export const pauseReceiveTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/pause",
    data
  })
};

export const goOnReceiveTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/goOn",
    data
  })
};

export const endReceiveTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/finish",
    data
  })
};

export const getPostReceiveSetting = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrainSetting/findAll",
    data
  })
};

export const savePostReceiveSetting = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrainSetting/addOrUpdate",
    data
  })
};

export const getAllReceivePostTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrain/listPage",
    data
  })
};

export const saveReceivePostTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrain/add",
    data
  })
};

export const getReceiveTrainDetails = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrain/getById",
    data
  })
};

export const startReceivePostTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrain/begin",
    data
  })
};

export const endReceivePostTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrain/finish",
    data
  })
};

export const resetReceivePostTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrain/reset",
    data
  })
};

export const uploadReceiveResult = (data) => {
  return axios({
    method: "post",
    url: "/api/postTickerTapeTrain/upLoadResult",
    data
  })
};

export const findReceiveTrainTotal = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/statisticalPage",
    data
  })
};

export const findPrevReceiveTrainInfo = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrain/lastTrain",
    data
  })
};


export const getPreKochStageArray = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrainStageSetting/findAll",
    data
  })
};



export const updatePreKochStageArray = (data) => {
  return axios({
    method: "post",
    url: "/api/tickerTapeTrainStageSetting/add",
    data
  })
};

export const apiPostTickerTapeTrainFindPage = (data) => {
  return axios({
    method: "get",
    url: "/api/postTickerTapeTrain/findPage",
    data
  })
};
export const deleteList = (data) => {
  return axios({
    method: "get",
    url: `/api/postTickerTapeTrain/delete?trainId=${data}`,
  })
};

export const saveHeader = (data) => {
  return axios({
    method: "post",
    url: `/api/masthead/save`,
    data
  })
};
export const findHeader = (data) => {
  return axios({
    method: "get",
    url: `/api/masthead/findByTrainId?trainId=${data}`,
  })
};