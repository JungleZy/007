// 下载端点的鉴权/业务错误仍然是 JSON，不能把它保存成看似成功的 SQL 文件。
export async function decodeDatabaseBackup(blob) {
  if (!(blob instanceof Blob) || blob.size === 0) throw new Error('未收到有效的数据库备份文件')
  if (blob.type.includes('json')) {
    const result = JSON.parse(await blob.text())
    throw new Error(result.message || '数据库备份失败，请检查登录状态和管理员权限')
  }
  return blob
}
