import axios from '../http/axios.js'
import { decodeDatabaseBackup } from '../utils/databaseBackup.js'

const config = { timeout: 960000, skipErrorToast: true }

export const getDatabaseStatus = () => axios({ method: 'get', url: '/api/database/status' })

export const backupDatabase = async () => {
  const result = await axios({
    method: 'post', url: '/api/database/backup',
    config: { ...config, responseType: 'blob' }
  })
  return decodeDatabaseBackup(result)
}

export const importDatabase = (file, confirmation) => {
  const data = new FormData()
  data.append('file', file)
  data.append('confirmation', confirmation)
  return axios({
    method: 'post', url: '/api/database/import', data,
    config: { ...config, headers: { 'Content-Type': 'multipart/form-data' } }
  })
}
