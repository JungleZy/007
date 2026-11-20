// 升级结果与版本检查结论的文案。主进程只给出状态码，文案集中在这里，版本提示与管理页共用。

/** 组网版本检查的结论 → 提示说明。只有 mismatch 的状态才会展示；提示不限制使用。 */
export function versionMismatchMessage(result) {
  const local = result.localVersion
  const server = result.serverVersion
  const risk = '版本不一致时部分功能可能无法正常工作。'
  switch (result.state) {
    case 'server-newer':
      return `本机版本 ${local} 低于服务端版本 ${server}。${risk}建议升级本机，可直接从服务端下载升级包。`
    case 'server-newer-no-package':
      return `本机版本 ${local} 低于服务端版本 ${server}，服务端没有适用于本机平台与风格的升级包。${risk}`
        + '建议联系管理员在服务端导入对应的升级包，或用U盘在本机导入升级包。'
    case 'server-older':
      return `服务端版本 ${server} 低于本机版本 ${local}。${risk}建议升级服务端。`
    case 'server-legacy':
      return `服务端是尚不支持版本检查的旧版本，本机版本为 ${local}。${risk}建议升级服务端。`
    default:
      return ''
  }
}

/** 导入或下载升级包的结果 → {type, text, restart}。 */
export function updateResultMessage(result) {
  if (!result.ok) return {type: 'error', text: result.message || '升级失败', restart: false}
  if (result.action === 'canceled') return null
  if (result.action === 'stored') {
    return {type: 'info', restart: false,
      text: `已留存 ${result.target} ${result.style} 版本 ${result.version} 的升级包，可分发给连接本机的客户端。它与本机的平台或风格不同，未在本机安装。`}
  }
  return {type: 'success', restart: true, text: `版本 ${result.version} 已准备就绪，重启软件后生效。`}
}
