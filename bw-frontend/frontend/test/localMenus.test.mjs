import test from 'node:test'
import assert from 'node:assert/strict'
import { withLocalMenus } from '../src/config/router/localMenus.js'

test('数据库没有备份菜单时管理员仍能看到本地数据备份入口', () => {
  const input = [{ id: '1600', name: 'SystemManage', children: [{ name: 'Basic', children: [] }] }]
  const result = withLocalMenus(input, { isAdmin: 0 })
  assert.equal(result[0].children[1].meta.title, '数据备份')
  assert.equal(result[0].children[1].path, 'database')
  assert.equal(input[0].children.length, 1)
  assert.deepEqual(withLocalMenus(result, { isAdmin: 0 }), result)
})

test('旧菜单缓存被本地定义替换，普通角色不获得本地入口', () => {
  const input = [{ name: 'SystemManage', children: [{
    id: 'system-database-transfer', name: 'DatabaseManagement', meta: { title: '数据库备份与导入' }
  }] }]
  assert.equal(withLocalMenus(input, { isAdmin: 0 })[0].children.length, 1)
  assert.equal(withLocalMenus(input, { isAdmin: 0 })[0].children[0].meta.title, '数据备份')
  for (const role of [null, {}, { isAdmin: 1 }]) {
    assert.equal(withLocalMenus(input, role)[0].children.length, 0)
  }
})

test('管理员无需数据库提供系统管理父菜单也可以加载本地入口', () => {
  const result = withLocalMenus([], { isAdmin: 0 })
  assert.equal(result[0].path, 'systemManage')
  assert.equal(result[0].children[0].name, 'DatabaseManagement')
  assert.deepEqual(withLocalMenus([], { isAdmin: 1 }), [])
})
