import axios from '../http/axios.js'

export const saveTelexPat = data => {
  return axios({
    method: 'post',
    url: '/api/telexPat/saveTelexPat',
    data
  })
}
export const deleteTexPatByToken = data => {
  return axios({
    method: 'post',
    url: '/api/telexPat/deleteTexPatByToken',
    data
  })
}
export const findTelexPatById = data => {
  return axios({
    method: 'post',
    url: '/api/telexPat/findTelexPatById',
    data
  })
}

export const saveTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/saveTelegramTrain',
    data
  })
}
export const saveTexPatTrain = data => {
  return axios({
    method: 'post',
    url: '/api/telexPat/saveTexPatTrain',
    data
  })
}
export const saveTelexTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelexPatTrain/save',
    data
  })
}
export const findPrevPatTrainInfo = data => {
  return axios({
    method: 'post',
    url: '/api/telexPat/lastPatTrain',
    data
  })
}
export const findPrePatTrainTotal = () => {
  return axios({
    method: 'post',
    url: '/api/telexPat/statisticalPage'
  })
}
export const findAll = data => {
  return axios({
    method: 'post',
    url: `/api/postTelexPatTrain/findAll?trainType=${data.trainType}`,
    data
  })
}
export const hanziAdd = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/add',
    data
  })
}
export const listPage = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/listPage',
    data
  })
}
export const getById = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/getById',
    data
  })
}
export const hanziFinish = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/finish',
    data
  })
}
export const hanziPause = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/pause',
    data
  })
}
export const begin = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/begin',
    data
  })
}
export const goTo = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/goTo',
    data
  })
}
export const findTexPatTrainById = data => {
  return axios({
    method: 'post',
    url: '/api/telexPat/findTexPatTrainById',
    data
  })
}
export const getAllTelegramTrain = () => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/getAll'
  })
}
export const findHandKeyTrainTotal = () => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/statisticalPage'
  })
}

export const getTelegramTrainById = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/getById',
    data
  })
}

export const startTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/startTelegramTrain',
    data
  })
}

export const pauseTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/pauseTelegramTrain',
    data
  })
}

export const endTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/endTelegramTrain',
    data
  })
}

export const getFloorContentByFloorId = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/getFloorContentByFloorId',
    data
  })
}

export const getFloorContentByFloorIdAsync = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/getFloorContentByFloorIdAsync',
    data
  })
}
export const getFloorContentByFloor = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/getFloorContentByFloor',
    data
  })
}

export const saveFloorContent = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/saveFloorContent',
    data
  })
}

export const getTelegramTrainLog = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/getTelegramTrainLog',
    data
  })
}

export const saveBasicSetting = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/saveSetting',
    data
  })
}

export const getBasicSetting = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/getSetting',
    data
  })
}

export const savePostTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/save',
    data
  })
}

export const getAllPostTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/findAll',
    data
  })
}

export const getPostTelegramTrainById = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/detail',
    data
  })
}

export const getPostTelegramMsgBody = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/findMessageBody',
    data
  })
}

export const getTelexTrainByID = data => {
  return axios({
    method: 'post',
    url: '/api/postTelexPatTrain/detail',
    data
  })
}
export const postTelexPatTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelexPatTrain/begin',
    data
  })
}
export const endTelexPatTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelexPatTrain/finish',
    data
  })
}
export const beginPostTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/begin',
    data
  })
}

export const resetPostTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/stop',
    data
  })
}

export const savePostTelegramContent = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/saveContentValue',
    data
  })
}

export const finishPostTelegramTrain = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/finish',
    data
  })
}

export const saveHandKeyBasicTrain = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/saveBaseTrain',
    data
  })
}

export const findPrevHandKeyTrainInfo = data => {
  return axios({
    method: 'post',
    url: '/api/telegramTrain/lastTrain',
    data
  })
}

export const saveReceiveBasicTrain = data => {
  return axios({
    method: 'post',
    url: '/api/tickerTapeTrain/saveBaseTrain',
    data
  })
}

export const findPinYinTrainTotal = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/statisticalPage',
    data
  })
}

export const findPrevPYTrainTotal = data => {
  return axios({
    method: 'post',
    url: '/api/enteringExercise/lastTrain',
    data
  })
}

export const findWuBiTrainTotal = data => {
  return axios({
    method: 'post',
    url: '/api/enteringTelexPat/statisticalPage',
    data
  })
}

export const findPostExamTrainList = data => {
  return axios({
    method: 'post',
    url: '/api/PostTelegraphKeyPatTrain/listPage',
    data
  })
}

export const addPostExamTrainList = data => {
  return axios({
    method: 'post',
    url: '/api/PostTelegraphKeyPatTrain/add',
    data
  })
}

export const getPostExamTrainDetails = data => {
  return axios({
    method: 'post',
    url: '/api/PostTelegraphKeyPatTrain/details',
    data
  })
}

export const startPostExamTrainInfo = data => {
  return axios({
    method: 'post',
    url: '/api/PostTelegraphKeyPatTrain/begin',
    data
  })
}

export const endPostExamTrainInfo = data => {
  return axios({
    method: 'post',
    url: '/api/PostTelegraphKeyPatTrain/finish',
    data
  })
}

export const getPreTermTrainTotal = data => {
  return axios({
    method: 'post',
    url: '/api/radiotelephone/listPage',
    data
  })
}

export const savePreTermTrainTotal = data => {
  return axios({
    method: 'post',
    url: '/api/radiotelephone/finish',
    data
  })
}

export const getTermListData = data => {
  return axios({
    method: 'post',
    url: '/api/postRadiotelephoneTrain/findByType',
    data
  })
}

export const getTermDeployListData = data => {
  return axios({
    method: 'post',
    url: '/api/postRadiotelephoneTermData/findByType',
    data
  })
}

export const addTermItemData = data => {
  return axios({
    method: 'post',
    url: '/api/postRadiotelephoneTermData/save',
    data
  })
}

export const updateTermItemData = data => {
  return axios({
    method: 'post',
    url: '/api/postRadiotelephoneTermData/update',
    data
  })
}

export const deleteTermItemData = data => {
  return axios({
    method: 'post',
    url: '/api/postRadiotelephoneTermData/delete',
    data
  })
}

export const apiPostTelexPatTrainFinishPage = data => {
  return axios({
    method: 'post',
    url: '/api/postTelexPatTrain/finishPage',
    data
  })
}

export const apiPostTelegraphKeyPatTrainGetPage = data => {
  return axios({
    method: 'get',
    url: '/api/PostTelegraphKeyPatTrain/getPage',
    data
  })
}

export const apiPostTelexPatTrainGetPage = data => {
  return axios({
    method: 'get',
    url: '/api/postTelexPatTrain/getPage',
    data
  })
}

export const apiPostTelegramTrainPrintBottomReport = data => {
  return axios({
    method: 'post',
    url: '/api/postTelegramTrain/printBottomReport',
    data
  })
}

export const apiPostTelegraphKeyPatTrainFinishPage = (data, pageNumber, trainId) => {
  return axios({
    method: 'post',
    url: `/api/PostTelegraphKeyPatTrain/finishPage/?pageNumber=${pageNumber}&trainId=${trainId}`,
    data
  })
}

export const deleteList = (data) => {
  return axios({
    method: "get",
    url: `/api/postTelegramTrain/delete?trainId=${data}`,
  })
};
export const deleteExamList = (data) => {
  return axios({
    method: "get",
    url: `/api/PostTelegraphKeyPatTrain/delete?trainId=${data}`,
  })
};
export const deleteDataGramList = (data) => {
  return axios({
    method: "get",
    url: `/api/postTelexPatTrain/delete?trainId=${data}`,
  })
};
