import axios from '../http/axios.js'

// 学时管理
export const getClassChartsDataByTypeAndTime = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/recordStatistice",
    data
  })
}

// 分数统计
export const getScoreChartsDataByTypeAndTime = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/gradeCount",
    data
  })
}

// 综合分析
export const analyzeTotal = (data) => {
  return axios({
    method: "get",
    url: "/api/comprehensive/getUserOverallInfo",
    data
  })
}

export const analyzeUp = (data) => {
  return axios({
    method: "get",
    url: "/api/comprehensive/getTheoryYear",
    data
  })
}

export const analyzeDown = (data) => {
  return axios({
    method: "get",
    url: "/api/comprehensive/getTheoryTestYear",
    data
  })
}