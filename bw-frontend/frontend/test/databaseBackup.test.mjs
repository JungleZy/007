import test from 'node:test'
import assert from 'node:assert/strict'
import { decodeDatabaseBackup } from '../src/common/utils/databaseBackup.js'

test('鉴权和业务错误 JSON 不会被保存为 SQL 备份', async () => {
  for (const code of [202, 203, 204, 206, 207]) {
    const blob = new Blob([JSON.stringify({ code, message: `拒绝 ${code}` })], { type: 'application/json' })
    await assert.rejects(decodeDatabaseBackup(blob), new RegExp(`拒绝 ${code}`))
  }
})

test('SQL 下载保留原始内容，空文件拒绝下载', async () => {
  const sql = new Blob(['CREATE TABLE example (id int);'], { type: 'application/octet-stream' })
  assert.equal(await decodeDatabaseBackup(sql), sql)
  await assert.rejects(decodeDatabaseBackup(new Blob([])), /有效/)
})
