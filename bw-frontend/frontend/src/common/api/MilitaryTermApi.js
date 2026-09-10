import axios from '../http/axios.js'

export const saveAll = (data) => {
  return axios({
    method: "post",
    url: "/api/mtd/saveAll",
    data
  })
};

export const getMilitaryType = (data) => {
  return axios({
    method: "post",
    url: "/api/mtd/findAll",
    data
  })
};

export const addPostMilitaryTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postMilitaryTermTrain/add",
    data
  })
};

export const getPostMilitaryTrainList = (data) => {
  return axios({
    method: "post",
    url: "/api/postMilitaryTermTrain/listPage",
    data
  })
};

export const getPostMilitaryTrainDetails = (data) => {
  return axios({
    method: "post",
    url: "/api/postMilitaryTermTrain/details",
    data
  })
};

export const beginPostMilitaryTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postMilitaryTermTrain/begin",
    data
  })
};

export const finishPostMilitaryTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/postMilitaryTermTrain/finish",
    data
  })
};
export const getMilitaryAll = (data) => {
  return axios({
    method: "post",
    url: "/api/mtd/findAll",
    data
  })
};
export const addMilitarys = (data) => {
  return axios({
    method: "post",
    url: "/api/mtd/save",
    data
  })
};
export const updateMilitarys = (data) => {
  return axios({
    method: "post",
    url: "/api/mtd/update",
    data
  })
};
export const deleteMilitarys = (data) => {
  return axios({
    method: "post",
    url: "/api/mtd/delete",
    data
  })
};

export const moveMilitarys = (data) => {
  return axios({
    method: "post",
    url: "/api/mtd/move",
    data
  })
};
export const saveBatchData = (data) => {
  return axios({
    method: 'post',
    url: '/api/mtd/saveBatch',
    data
  })
}

export const deleteList = (data) => {
  return axios({
    method: "get",
    url: `/api/postMilitaryTermTrain/delete?trainId=${data}`,
  })
};