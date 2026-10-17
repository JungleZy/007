const fs = require('node:fs')
const path = require('node:path')

function statIfPresent(filename) {
  try {
    return fs.lstatSync(filename)
  } catch (error) {
    if (error.code === 'ENOENT') return null
    throw error
  }
}

// Called only by the single-instance owner, before NeDB or the file service opens.
// NSIS restores the old bin data before this first-launch migration runs.
function prepareWindowsRuntime(appPath, userData) {
  const runtime = path.join(userData, 'runtime')
  const existing = statIfPresent(runtime)
  if (existing) {
    const bin = statIfPresent(path.join(runtime, 'bin'))
    if (!existing.isDirectory() || existing.isSymbolicLink() || !bin?.isDirectory() || bin.isSymbolicLink()) {
      throw new Error(`用户数据目录不完整，已停止启动以避免重置：${runtime}`)
    }
    return runtime // Existing user state always wins over stale installer defaults.
  }

  fs.mkdirSync(userData, {recursive: true})
  const staging = fs.mkdtempSync(path.join(userData, 'runtime-import-'))
  try {
    const legacyBin = path.join(appPath, 'bin')
    if (statIfPresent(legacyBin)?.isSymbolicLink()) {
      throw new Error(`旧数据目录是链接，请先备份并处理：${legacyBin}`)
    }
    const bin = path.join(staging, 'bin')
    fs.mkdirSync(bin)
    for (const name of ['nip.db', 'nip.db~', 'config.json', 'file']) {
      const source = path.join(appPath, 'bin', name)
      if (!statIfPresent(source)) continue
      fs.cpSync(source, path.join(bin, name), {
        recursive: true, force: false, errorOnExist: true,
        filter(filename) {
          const stat = fs.lstatSync(filename)
          if (stat.isSymbolicLink() || (!stat.isDirectory() && !stat.isFile())) {
            throw new Error(`旧数据包含链接或特殊文件，请先备份并处理：${filename}`)
          }
          return true
        }
      })
    }
    fs.mkdirSync(path.join(bin, 'file'), {recursive: true})
    // Publish the whole dataset, never a partially copied profile. Leave the old
    // bin and installer backup intact for recovery; they are no longer writers.
    fs.renameSync(staging, runtime)
    return runtime
  } catch (error) {
    fs.rmSync(staging, {recursive: true, force: true})
    throw error
  }
}

module.exports = {prepareWindowsRuntime}
