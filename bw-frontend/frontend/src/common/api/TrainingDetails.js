import axios from '../http/axios.js'

export const groupNetTrainSaveTrain = (data) => {
  return axios({
    method: 'post',
    url: '/api/groupNetTrain/saveTrain',
    data
  })
}

export const groupNetTrainListPage = (data) => {
  return axios({
    method: 'post',
    url: '/api/groupNetTrain/listPage',
    data
  })
}

export const deviceScoringRuleSave = (data) => {
  return axios({
    method: 'post',
    url: '/api/deviceScoringRule/save',
    data
  })
}

export const deviceScoringRuleFindAllByDeviceId = (data) => {
  return axios({
    method: 'get',
    url: '/api/deviceScoringRule/findAllByDeviceId',
    data
  })
}

export const groupNetTrain = (data) => {
  return axios({
    method: 'get',
    url: '/api/groupNetTrain/details',
    data
  })
}
export const groupNetTrainSubmitAnswer = ({id, answer}) => {
  return axios({
    method: 'POST',
    url: '/api/groupNetTrain/submitAnswer',
    data: {id, answer}
  })
}
