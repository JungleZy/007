# 授权机制修复方案（"未到期却要求重新授权"）

- 日期：2026-09-09
- 范围：`bw-frontend`（Vue 渲染层 + Electron 主进程 + 打包配置）
- 关联：`frontend/docs/2026-09-08-frontend-review.md:67-70`
- 前置约束（项目方已确认）：
  1. **发号器不能重做**——签发工具不可改动。
  2. **到期语义取「累计运行时长」**——不是日历天。
  3. 授权非安全边界，本方案不做防破解加固。

---

## 1. 问题现象

浏览器部署正常；打包为 Electron 后，在国产化系统（麒麟 / UOS）与 Windows 上频繁出现
**授权未到期却弹出授权页、要求重新授权**。

## 2. 根因结论

**不是到期判断出错，是授权数据丢失，且代码把"读不到"当成了"没授权"。**

### 2.1 排除「到期算错」

`common/utils/VerifyLicense.js:179-182`：

```js
179: const diff = new Date().getTime() - info[1]
180: const deadline = (info.length === 2 ? 30 : info[2]) * 24 * 60 * 60
181: // if (diff > 0 && diff < 86400000 * (...) && license.licenseTime <= diff) {
182: if (license.duration <= deadline) {
```

- 第 181 行才是墙钟日历到期判断，**已被注释**。
- 现行第 182 行判的是 `duration`，它在 `:145-156` 每 10 秒 `+= 10`，即**累计运行秒数**。
  30 天授权 = 2,592,000 秒 = **720 小时运行时长**。
- `diff`（本地时钟 − 签发时间）只赋给 `licenseTime` 存盘，**从不参与判断**。

**推论：改系统时间 / 时区 / NTP 都不会造成提前过期。这个方向可以排除。**

已排除的其它假设：
- 多窗口重复计时 → `electron/index.js:45` 是唯一的 `BrowserWindow`，`openSettingWindow`（`App.vue:93`）是页内弹窗。
- 网络/后端因素 → 授权链路零网络请求，`backend/` 无任何 license 接口、表、实体。

### 2.2 四个根因

| 编号 | 根因 | 证据 |
|---|---|---|
| **R0（主因）** | **MAC 变化时主动删除授权码与设备码**；取 MAC 的循环无 `break`，拿的是最后一个非内部 IPv4 网卡 | `system.js:102-127`，已实证，详见 2.5 |
| **R1** | 设备码是 `Math.random()` 随机 UUID，**丢失即永久不可复原** | `VerifyLicense.js:47-61` |
| **R2** | 授权状态**唯一副本**在 `file://` 源的 IndexedDB（`WisdomJ233`） | `frontend/index.html:31-33`、`electron/index.js:61` |
| **R3** | 读/写失败一律判"未授权"，且**触发自毁**（重生设备码 + 删授权） | `VerifyLicense.js:62-85, 157-201` |

### 2.3 触发"要求重新授权"的全部分支

| # | 位置 | 条件 | 副作用 |
|---|---|---|---|
| **A** | `:157-159` | IndexedDB 读不到设备码 `"2"` | **删 `"1"`+`"2"`，重新随机生成设备码 → 旧授权码永久作废** |
| **B** | `:161-163` | 读不到授权记录 `"1"` | 要求重新授权 |
| C | `:167-175` | AES 解密抛异常 | 同上 |
| D | `:199-201` | 解密结果为空串 | 同上 |
| E | `:196-198` | 明文段数非 2/3 | 同上 |
| F | `:196-198` | 设备码不匹配 | 同上 |
| G | `:193-195` | `duration > deadline`（真过期） | 同上 |
| **H** | `:184, 190-192` | **`setItem("1")` 写入失败** | **授权完全合法，仅因写盘失败即判未授权** |
| I | `:152-154` | 心跳超限 | 运行中无提示切回授权页 |

分支 A 是"自毁"的：一次瞬时读取失败即永久摧毁授权，无重试、无备份、无确认。
分支 H 让只读校验依赖写入成功。整条链路（`:157` 顶层 Promise）**没有任何 `.catch()`**。

### 2.4 为什么 Electron 上高发、浏览器上没事

| # | 差异 | 说明 |
|---|---|---|
| 1 | userData 路径随环境漂移 | `productName: "多功能报务综合训练系统"`（`package.json:4`）→ userData 为中文目录。**不同 OS 账户 / "以管理员身份运行" / Linux 下 sudo 与普通用户混用 → 路径不同 → 授权"丢失"**。`main.js:12` 未使用 `userData`，也无 `app.setPath`。nsis 无自定义配置，electron-builder 默认每用户安装，放大此问题。 |
| 2 | 页面走 `file://` | `electron/index.js:61` `loadFile`。`file://` 源无法通过 `navigator.storage.persist()` 提权，存储永远是 best-effort，**磁盘紧张时被 Chromium 配额管理器按 LRU 静默清除**。国产化系统系统盘普遍偏小。 |
| 3 | 写放大 | `:149` 每 10 秒写同一个 key，一天 8640 次，LevelDB 长期处于脏状态；异常退出后 Chromium 检测到损坏会**删库重建**。`ActionBtn.vue:49-57` 虽有正常退出路径（`closeApp` → `system.js:36-43` → `app.quit()`），但 `frame:false + fullscreen` 下现场仍普遍直接断电/强杀。 |
| 4 | 非 UTF-8 locale | 国产化系统上中文 userData 目录可能导致 LevelDB 打开失败。 |
| 5 | 系统清理工具 | 国产化系统自带安全中心会清理 `~/.config`、`~/.cache`。 |

### 2.5 主因：MAC 绑定路径主动删除授权（已实证，代码已删除）

> **2026-09-09 更新**：本节最初列为「待确认的死代码」，现已确认为**主因**，并已删除相关代码。
>
> **证据一**：开发机 `bin/nip.db` 中存在 `{"_id":7,"MAC":"c0:35:32:1d:e6:5d"}` 以及
> `_id:5`（授权码）、`_id:6`（设备码）。这三条记录**只有** `VerifyLicenseDB.js` 会写，
> 证明启用了 MAC 绑定的版本确实运行过。
>
> **证据二**：在该机器上按 `system.js:102-113` 原样逻辑复算，得到两块候选网卡：
>
> ```
> 以太网 4  ->  c8:a3:62:3e:98:ee  (192.168.10.43)
> WLAN      ->  c0:35:32:1d:e6:5d  (10.240.103.135)
> 原逻辑取值（最后一个）: c0:35:32:1d:e6:5d   ← 与 nip.db 存储值一致
> ```
>
> 该机器同时启用有线与无线。**只要关闭 WiFi / WiFi 断连 / 插 USB 网卡 /
> 启用虚拟机网卡 / 网卡枚举顺序变化**，取值即变为 `c8:a3:62:3e:98:ee`，
> 触发 `system.js:119-121` 的 `remove(_id:5)` + `remove(_id:6)`，授权与设备码被一并删除。
>
> 这与「经常出现、未到期、Windows 与国产化系统均有、与时间无关」的现象完全吻合。
>
> **处置**：`getVersion` / `changeVersion` / `getMac` 三个 IPC 与
> `VerifyLicenseDB.js` 已于 2026-09-09 全部删除（见 5.5）。
> 替代方案 `fingerprint.js` 明确不采集 MAC。

原始记录如下：

`electron/controller/system.js:102-127` 的 `getMac`：

```js
107: for (const name of Object.keys(ifaces)) {
108:   const iface = ifaces[name].find(d => d.family === 'IPv4' && !d.internal);
109:   if (iface) { mac = iface.mac }     // 不 break，被后面的网卡覆盖
...
119: if(findOne.MAC!==mac){
120:   context.db.remove({_id: 5})       // 删 license
121:   context.db.remove({_id: 6})       // 删设备码
122: }
```

取**最后一个**非内部 IPv4 网卡的 MAC。插拔 USB 网卡、开关 WiFi、虚拟机/VPN 虚拟网卡、
枚举顺序变化都会导致 MAC 变化，然后直接删除授权。

该路径因 `VerifyLicense.vue:81` 的 import 被注释而**当前未生效**（`VerifyLicenseDB.js` 为死代码）。
**若现场故障机安装包对应的历史版本启用了它，则它是最直接的元凶——需核对故障机的包版本。**

### 2.6 附带发现（独立 bug）

`VerifyLicense.js:122` 的 `localforage.clear()` 会清空整个 `WisdomJ233` 库，
连带删除 `autoLoginInfo`（`views/manage/login/useLogin.js:128`）与 `cool`（`App.vue:91`）。
即每次授权都会重置用户的自动登录与特效设置。

---

## 3. 关键设计判断：发号器零改动下仍可做硬件指纹

发号器的行为是：接收**设备码字符串**，输出 `AES-ECB(设备码 + ':' + 时间戳[+ ':' + 天数], 'wisdom23')`。
**它不关心设备码字符串的内容。** 因此只改「设备码怎么算」即可，发号器一行不用改。

保持字节级相同的外形（`VerifyLicense.js:48` 的 `xxxx-` × 8 = 32 个十六进制字符 + 7 个连字符），
把内容从随机数换成硬件派生：

| 槽位 | 字符 | 因子 | Windows | Linux（麒麟/UOS） |
|---|---|---|---|---|
| 1-2 组 | 8 hex | 主机标识 | `HKLM\SOFTWARE\Microsoft\Cryptography` → `MachineGuid`（需 `/reg:64`） | `/etc/machine-id`，回退 `/var/lib/dbus/machine-id` |
| 3-4 组 | 8 hex | 主板/整机 UUID | `Win32_ComputerSystemProduct.UUID` | `/sys/class/dmi/id/product_uuid`（需 root，**允许缺失**） |
| 5-6 组 | 8 hex | 系统盘序列号 | `Win32_DiskDrive.SerialNumber` | `lsblk -dno SERIAL` / `/dev/disk/by-id/` |
| 7-8 组 | 8 hex | 整机出厂序列号 | `Win32_BIOS.SerialNumber` | `/sys/class/dmi/id/product_serial` |

- 每槽 = `sha256(因子值)` 前 4 字节转 hex；因子取不到填哨兵 `00000000`。
- **校验规则（在我方代码内，可自由定义）**：把 `info[0]` 去连字符切成 4 段，
  与当前计算值逐段比对，**双方均非哨兵的槽位中命中 ≥ 2 段即通过**。

容差效果：

| 场景 | 结果 |
|---|---|
| 换硬盘 | 槽 3 变，1/2/4 命中 → 通过 |
| 换主板、沿用硬盘 | 槽 2/4 变，1/3 命中 → 通过 |
| 重装系统（machine-id 重生成） | 槽 1 变，2/3/4 命中 → 通过 |
| 整机更换 | 全不命中 → 正确拒绝 |

**明确排除的因子**：

- **MAC 地址**——网卡插拔 / WiFi 开关 / 虚拟网卡都会变（见 2.5）。
- **CPU ID**——同批次整机的 `Win32_Processor.ProcessorId` 常完全相同，会造成跨机误判。

### 3.1 存量机器的局限（必须明确）

存量机器存的是旧随机 UUID，丢失后**无法用硬件指纹算回来**。因此：

- **硬件指纹（P2）保护"以后"**：新装机 + 按硬件码换发授权之后。
- **保护"现在"的是三副本 + 首启迁移（P1）**：趁存量机器仍处于已授权状态，把旧设备码与
  旧授权码钉到更稳的存储位置。**这是对现场投诉见效最快的一步。**

---

## 4. 分阶段方案

### P0 —— 纯渲染层，不动任何数据格式，当天可发版

| # | 动作 | 位置 |
|---|---|---|
| 1 | **删除自毁逻辑**：读不到设备码时不再重新生成、不再覆盖存储 | `VerifyLicense.js:62-85` |
| 2 | **状态三分**：`checking / authorized / unauthorized / storage_error` | `VerifyLicense.js`、`VerifyLicense.vue` |
| 3 | **写入失败改为放行**，只读校验不依赖写盘成功 | `VerifyLicense.js:184,190-192` |
| 4 | `localforage.clear()` 改为只删 `"1"`/`"2"` | `VerifyLicense.js:122` |
| 5 | 顶层读取补 `.catch()` + 超时 + 重试（3 次指数退避） | `VerifyLicense.js:157` |
| 6 | 心跳落盘 10s → 5min（内存仍按 10s 计数，仅合并落盘） | `VerifyLicense.js:145-156` |
| 7 | `isPass` 默认值改掉，消除主界面闪现 | `VerifyLicense.js:17` |

`storage_error` 渲染**错误页而非授权页**：文案"授权信息读取失败，请重启软件"+ 重试按钮，
**不显示设备码、不接受输入、不清除任何数据**。

分支重新映射：

| 原分支 | 现状 | 改为 |
|---|---|---|
| A 读不到设备码 | 重生 + 删授权 | 重试后仍空 → `unauthorized`；**永不重生** |
| B 读不到授权 | unauthorized | 干净返回空 → `unauthorized`；**抛异常/超时 → `storage_error`** |
| C/D/E 解密或格式错 | unauthorized | 不变 |
| F 设备码不匹配 | unauthorized | 不变（P2 后改阈值匹配） |
| G duration 超限 | unauthorized | 不变 |
| **H 写入失败** | **unauthorized** | **`authorized` 放行**，仅告警 |
| I 心跳超限 | 无提示切页 | 弹确认框再切 |

### P1 —— 三副本 + 存量迁移（主力）

| 副本 | 位置 | 角色 |
|---|---|---|
| **M** | Win `%ProgramData%\NipTrafficSystem\license.json`<br>Linux `/var/lib/nip-traffic-system/license.json` | **权威**，跨 OS 账户 / 跨提权 |
| **U** | `app.getPath('userData')/license.json` | M 不可写时兜底 |
| **R** | IndexedDB `WisdomJ233` 的 `"1"`/`"2"` | 缓存；浏览器部署时的唯一副本 |

- **读**：M → U → R，任一命中即用，随后异步回写补齐其余副本（自愈）。
- **判定**：三处均干净返回空 → `unauthorized`；任一抛异常/超时 → `storage_error`。
- **写**：并发写三处，**≥1 成功即成功**。
- **首启迁移**：升级后首次启动，若 R 中存在有效旧设备码 + 授权码，立即写入 M 与 U。

> **必办项**：Linux 下 `/var/lib/...` 普通用户默认不可写。必须在 deb 的 `postinst` 中
> `install -d -m 0777 /var/lib/nip-traffic-system`（electron-builder 用 `linux.deb.afterInstall`）。
> 不做此步 M 副本形同虚设，P1 收益砍半。

### P2 —— 硬件设备码（发号器零改动）

- `info[0]` 优先按 v1 精确匹配（存量），失败再按第 3 节的 4 槽阈值匹配。两条路径并存。
- 授权页展示规则：存储中存在旧设备码 → 展示旧的（客服流程不变）；全新安装 → 展示硬件码。
- 提供隐藏入口显示硬件码，便于给存量客户做"换发升级"。
- 废弃 `system.js:102-127` 的 MAC 逻辑。

### P3 —— Electron 侧加固（回归面大，单独发版）

1. `main.js` 中 `app.setPath('userData', path.join(app.getPath('appData'), 'NipTrafficSystem'))`，
   规避非 UTF-8 locale 下中文目录问题。**必须在 P1 之后做**，否则此步本身会丢授权。
2. `electron/index.js:61` 的 `loadFile`（`file://`）换成自定义标准协议 `app://`：
   - `protocol.registerSchemesAsPrivileged([{scheme:'app', privileges:{standard:true, secure:true, supportFetchAPI:true}}])`
   - `protocol.handle('app', ...)` 映射到 `public/dist`，`loadURL('app://local/index.html')`
   - 收益：获得真实 origin → IndexedDB 拿到正常配额待遇，`navigator.storage.persist()` 可生效；
     顺带可关闭 `webSecurity:false`。
   - 成本：`index.html` 中 `/js/...`、`/cdn/...` 绝对路径需同步核对。

---

## 5. 运行时长语义专项

到期语义定为**累计运行时长**后，`duration` 成为关键状态。

### 5.1 计时方式：坚持计数器 `+= 间隔`，不改成墙钟差值

看似"用 `Date.now() - lastTick` 累加更准确"，但笔记本合盖 8 小时后唤醒，
一次心跳会一口气加 8 小时 → **直接制造提前过期**。
计数器式在休眠/节流时只会**少算**，方向对用户有利。

若必须用墙钟差值，须对单次增量做上限钳制（单次最多计 2× 心跳间隔）。**建议不折腾。**

### 5.2 完整性保护

```
duration_effective = min( max(M.duration, U.duration, R.duration),
                          (now - 签发时间戳) / 1000 )
```

- 多副本取 **max**：防回滚，也防陈旧副本把已用时长清零。
- 用 `(now - info[1]) / 1000` 做**上限钳制**：运行时长在物理上不可能超过"自签发以来的墙钟时长"。
  这正好把目前完全没用上的 `info[1]`（签发时间戳，现仅赋给 `licenseTime` 存盘）派上用场。
- 写入时取 `max(旧, 新)`，保证单调不减。

### 5.3 必须向用户展示剩余量

`deadline = 天数 × 86400 秒`，即 30 天 = **720 小时运行时长**。
授权页与主界面状态栏须显示：

```
已用 342 小时 / 共 720 小时（剩余 378 小时）
```

不做此项，客服无法向客户解释"买了 30 天为什么用了 3 个月还没到期"。

### 5.4 心跳降频的代价

崩溃最多少记 5 分钟运行时长。方向对用户有利，可接受。

---

## 5.5 实施状态（2026-09-09）

### 已完成

| 阶段 | 文件 | 说明 |
|---|---|---|
| P0 | `frontend/src/common/utils/VerifyLicense.js` | 重写：去自毁、状态三分、写失败放行、重试+超时、心跳降频、运行时长钳制 |
| P0 | `frontend/src/components/common/VerifyLicense.vue` | 新增 `checking` 加载态与 `storage_error` 错误页 |
| P1 | `electron/controller/license.js` | 新增。机器级 / 用户级文件副本，原子写（tmp + rename），Windows `icacls` 授权，Linux `chmod` |
| P1 | `electron/controller/index.js` | 注册 `license` / `fingerprint` 控制器 |
| P1 | `frontend/src/electron/api.js` | 新增 4 条 IPC 路由 |
| P1 | `frontend/src/common/utils/licenseStore.js` | 新增。三副本读写、自愈回写、存量首启迁移、`duration` 取 max |
| P1 | `build/linux/after-install.sh` | 新增。deb postinst：建 `/var/lib/nip-traffic-system` 并放开权限 |
| P1 | `package.json` | `build.linux.deb.afterInstall` 挂接上述脚本 |
| P2 | `electron/controller/fingerprint.js` | 新增。4 槽硬件因子采集（Win: reg + PowerShell + wmic 回退；Linux: machine-id + DMI 快照 + by-id） |
| P2 | `frontend/src/common/utils/machineCode.js` | 新增。设备码格式化与阈值比对 |
| P2 | `build/linux/after-install.sh` | 追加：root 身份落盘 DMI 快照 `hwid`，使非 root 运行也能拿到槽2/槽4 |
| 清理 | `electron/controller/system.js` | **删除 `getVersion` / `changeVersion` / `getMac`**（MAC 绑定路径，见 2.5），并移除未使用的 `exec` 引用 |
| 清理 | `frontend/src/common/utils/VerifyLicenseDB.js` | **整个文件删除**（MAC 绑定路径的唯一调用方） |
| 清理 | `frontend/src/electron/api.js` | 删除对应的 3 条 IPC 路由 |
| 清理 | `frontend/src/components/common/VerifyLicense.vue` | 移除 `VerifyLicenseDB` 的注释残留 import |
| 工具 | `VerifyLicense.js` / `VerifyLicense.vue` / `license.js` / `licenseStore.js` | 新增「清除本机授权信息」隐藏入口，见 5.6 |

### 5.6 清除授权信息（隐藏入口）

打包版没有开发者工具，售后与测试需要一个能在**已授权状态下**也可用的清除入口。

**两种触发方式：**

| 方式 | 适用状态 | 说明 |
|---|---|---|
| 连击 Logo 5 次（1.5 秒内） | 未授权 / 存储错误页 | 这两个页面下 Logo 可见 |
| `Ctrl + Alt + Shift + L` | **任意状态，含已授权** | 已授权时授权页不渲染，只能靠快捷键 |

两者都会弹二次确认，确认后清除**四处**并展示逐项结果，然后 `location.reload()`：

1. 机器级文件 `%ProgramData%\NipTrafficSystem\license.json` / `/var/lib/nip-traffic-system/license.json`
2. 用户级文件 `<userData>/license.json`
3. 浏览器存储 IndexedDB `WisdomJ233` 的 `'1'` / `'2'`
4. 历史遗留 `bin/nip.db` 的 `_id:5/6/7`

> 第 4 项是必须的：旧版本（MAC 绑定版）以 `nip.db` 为权威存储，
> 只删 IndexedDB 的话刷新后会被它写回来 —— 实测确认过这个现象。
> 删除后会调用 `compactDatafileAsync()` 压实文件，否则 NeDB 只追加删除标记，
> 直接查看文件仍能看到旧授权码，造成「没删掉」的误判。
> `_id:1/2/3`（http 配置、后端地址、串口号）**不会被删**。

**手动清除（不用界面时）**：见第 8 节。

### 已验证

- `vite build` 三轮全部通过。
- Windows 硬件因子采集实测 **4/4**，耗时约 1.5s，
  设备码形如 `ace4-e3dc-1d0d-1551-c28c-00ac-4e7e-cba1`，与原随机码外形完全一致。
- 项目方已确认：该外形的设备码在发号器上**可正常出码**，P2 的「发号器零改动」前提成立。

### 实现中确定的细节

- **阈值规则**（`machineCode.js` 的 `required()`）：可比对槽位 ≥ 3 时需命中 2 个；
  ≤ 2 时降为 1 个。否则 Linux 非 root（通常只有 machine-id + 磁盘序列号两个因子）
  会失去全部容差，重装系统即失效。
  代价：克隆镜像批量部署的机器可能因共享 machine-id 而互相通过校验。
  鉴于授权已确认非安全边界（万能码与 AES 密钥本就明文硬编码），此处以可用性优先。
- **BIOS 占位串过滤**（`fingerprint.js` 的 `JUNK`）：白牌机 / 组装机常返回
  `To Be Filled By O.E.M.`、`Default string`、`System Serial Number` 等跨机器完全相同的值，
  不过滤会把大量不同机器判成同一台。命中则填哨兵。
- **自动升级到硬件绑定**：已授权机器不动其原有设备码；一旦进入未授权状态，
  展示的设备码即切换为硬件设备码，用户申请的下一张授权码自然是硬件绑定的，
  无需任何人工换发流程。
- **`resetCode` 行为分叉**：有硬件指纹时「刷新设备码」是非破坏性的重新采集；
  无硬件指纹时（浏览器部署）保留原破坏性重置，但必须弹窗确认。

### 未完成 / 遗留

| # | 事项 | 说明 |
|---|---|---|
| 1 | P3 全部 | `app://` 协议、userData 改无中文路径、`navigator.storage.persist()`。回归面大，需单独发版。 |
| 2 | 5.3 剩余时长展示 | `usage`（已用/总运行时长）已从 hook 导出，但尚未接到主界面状态栏。运行时长语义下这个展示基本是必需的。 |
| 3 | `vite.config.js:60` `drop_console: true` | 会把生产包里所有 `console.warn/error` 剥掉，本次新增的落盘失败告警在打包后不可见。关键错误已通过 `storage_error` 页面呈现给用户。建议改为 `pure_funcs: ['console.log','console.debug']`，待定。 |
| 4 | `bin/nip.db` 存量迁移 | 运行过 MAC 绑定版本的机器，授权存在 `nip.db` 的 `_id:5/6`。新的 `licenseStore` 不读该文件（只在清除时删它），这些机器升级后需重新授权一次。是否加只读迁移兜底待定。 |
| 6 | 真机回归 | 本次仅完成构建验证与 Windows 因子采集实测。第 7 节验收表中的多账户 / 提权 / 断电 / 换盘等场景需在真机与国产化系统上跑一遍。 |

### 仓库状态告警（与本次改动无关，但阻碍评审）

`git` 仓库根在 `D:\bw\project\007`，其中：

- 追踪着 **4970 个 `frontend/**` 文件，但该目录已不存在于磁盘**；
- 实际代码位于 `bw-frontend/`，而它**整体处于未追踪状态**（`?? bw-frontend/`）。

即前端工程被移动到 `bw-frontend/` 时没有告知 git。后果：

- `git diff` 会显示约 46.7 万行删除，本次改动无法通过 diff 评审；
- 任何人执行 `git add -A` 都会产生一次巨型重命名提交。

本次未对 git 做任何操作。建议先用 `git mv` 或一次显式的移动提交把历史接上，再评审本次改动。

---

## 6. 待确认事项

| # | 问题 | 状态 | 影响 |
|---|---|---|---|
| 1 | 发号器对输入设备码是否有格式校验？ | **已确认：无。** 该外形可正常出码 | P2 前提成立，已实施 |
| 2 | 发号器出的是 2 段还是 3 段（有无天数字段）？ | **已确认：3 段，带天数。** 现场统一发 30 天 | 30 天 = 2,592,000 秒 = **720 小时运行时长**，≥ 30 个日历天，**运行时长到期不可能提前触发**，该方向排除 |
| 3 | 现场有无虚拟机 / 云桌面 / 还原卡部署？ | 待确认 | 该类环境硬件因子可能每次开机变化，需单独处理 |
| 4 | 故障机安装包对应的代码版本用的是 `VerifyLicense.js` 还是 `VerifyLicenseDB.js`？ | **已在开发机上确认 MAC 绑定版本运行过**；现场故障机版本仍待核对 | 见 2.5。核对方法：查 `<安装目录>\bin\nip.db` 是否含 `{"_id":7,"MAC":...}` |

---

## 7. 验收标准

| 场景 | 期望 | 依赖阶段 |
|---|---|---|
| 手删 IndexedDB `WisdomJ233` → 重启 | 仍授权 | P1 |
| 普通用户授权后改用"以管理员身份运行" | 仍授权 | P1 |
| Linux 下 sudo 与普通用户交替启动 | 仍授权 | P1 |
| 断电强杀 100 次 | 授权不丢 | P0 + P1 |
| 磁盘写满时启动 | 仍授权（不再因写失败判未授权） | P0 |
| IndexedDB 打不开 | 显示错误页而非授权页，**且不销毁数据** | P0 |
| 删光三副本 → 输入**原授权码** | 通过 | P2（需该机已按硬件码换发） |
| 换数据盘 / 重装系统 | 仍授权 | P2 |
| 拔 USB 网卡 / 关 WiFi / 装虚拟网卡 | 仍授权 | P0（废弃 MAC 逻辑） |
| 合盖休眠 8 小时后唤醒 | 运行时长不暴增 | 5.1 |
| 授权后自动登录信息与特效设置 | 不被清除 | P0-4 |

---

## 8. 现场取证步骤（无需改代码，用于确认个案）

1. 复现时先**不要**点重新授权。用 `--remote-debugging-port=9222` 启动打包版，
   Chrome 连上后看 Application → IndexedDB：
   - `WisdomJ233` 库是否还在？`keyvaluepairs` 中 `"1"` / `"2"` 是否还在？值是什么？
   - 库整个消失 → 印证驱逐 / 损坏 / 删库重建；`"2"` 在而 `"1"` 没了 → 分支 B。
2. 对比故障前后 `%APPDATA%\多功能报务综合训练系统\IndexedDB\` 下的目录与文件时间戳，
   以及 LevelDB 的 `LOG` 文件（其中会明确记录 corruption / repair / destroy）。
3. 让用户分别以普通方式与"以管理员身份运行"各启动一次，确认是否为两套授权状态；
   国产化系统上确认启动脚本是否带 `sudo`。
4. 记录故障机系统盘剩余空间，以及是否安装国产安全 / 清理软件。
5. 核对故障机安装包版本（对应待确认事项 #4）。

## 9. 手动清除授权信息（Windows）

优先用 5.6 的界面入口。需要脚本时：

```powershell
# 1) 必须先完全退出软件，运行中的心跳会把内存记录回写
Get-Process electron -ErrorAction SilentlyContinue | Stop-Process -Force

# 2) 新版三副本
$u = Join-Path $env:APPDATA "多功能报务综合训练系统"
Remove-Item -LiteralPath (Join-Path $env:ProgramData "NipTrafficSystem\license.json") -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $u "license.json") -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $u "IndexedDB") -Recurse -Force -ErrorAction SilentlyContinue

# 3) 旧版 bin\nip.db：只删 _id 5/6/7，保留 1/2/3（http 配置、后端地址、串口号）
$db = "<安装目录>\bin\nip.db"
Copy-Item -LiteralPath $db -Destination "$db.bak" -Force
$keep = (Get-Content -LiteralPath $db -Encoding UTF8) | Where-Object { $_ -notmatch '"_id":\s*[567]\b' }
[System.IO.File]::WriteAllLines($db, $keep, (New-Object System.Text.UTF8Encoding $false))
```

注意事项：

- **不能整个删 `nip.db`**：`_id:2` 存着后端地址，删了会退回 `localhost`。
- **不能用 `Set-Content -Encoding UTF8`**：PowerShell 5.1 会写 BOM，导致 NeDB 解析失败。
- **用户级副本是每个 OS 账户一份**。用过多个账户或「以管理员身份运行」的，每个账户的 `%APPDATA%` 都要清。机器级副本全机共用，清一次即可。
- **`/var/lib/nip-traffic-system/hwid` 不要删**（Linux）：那是硬件指纹快照，不是授权数据。删了不重置授权，只会让非 root 运行时可比对因子从 4 个降到 2 个。
