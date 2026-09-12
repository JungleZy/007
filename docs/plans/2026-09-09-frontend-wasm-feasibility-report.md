# 前端 Worker / WASM 可行性研究报告

- 研究日期：2026-09-09
- 最后更新：2026-09-09（完成 Chromium 全候选对比基准）
- 结论状态：Worker 化可行；WASM 仅适合经过基线确认的纯计算内核
- 明确排除：OCR 不在本报告的 WASM 范围内。OCR 及其处理继续全部由服务端完成。

## 1. 执行摘要

当前前端具备引入 Web Worker 和 WASM 的技术条件，但真正值得 WASM 化的范围较窄。

推荐路线：

```text
先用 Web Worker 消除主线程阻塞
        ↓
优化现有 JavaScript 算法，改为线性扫描和 TypedArray
        ↓
使用代表性生产数据重新测量
        ↓
仅将仍然 CPU 密集的纯算法编译为 WASM
```

推荐优先级：

1. **Excel / DOCX 和训练结果批处理 Worker 化**：已实测可消除 150–480 ms 主线程阻塞。
2. **训练日志 JavaScript 线性扫描优化**：实测收益远大于直接迁移 WASM。
3. **复杂训练评分 WASM PoC**：只有优化后的 JS 仍然 CPU 密集时实施。
4. **未来 DSP 的 FIR / FFT / 批量 PCM 转换**：实测纯计算存在 WASM 收益。
5. **Morse 文本查表、当前正弦波播放、Vue、WebSocket、表格、图表、倒计时、普通 CRUD**：不建议 WASM 化。

当前没有生产级 WASM 模块、WASM 构建链或 Worker 抽象。`frontend/src/common/worker/One.js` 只是每两秒发送计数值的占位 Worker，不可直接作为业务基础设施。

## 2. 代码现状

### 2.1 构建和运行条件

- Vue 3 + Vite。
- Vite 目标浏览器为 `edge90`、`chrome106`、`firefox90`、`safari15`，均具备基础 WebAssembly 能力。
- 当前 `vite.config.js` 使用 `base: './'`，部署可能包含相对路径和 Electron 外壳场景。Worker 和 WASM 资源必须使用 Vite 的模块 URL 方式加载，不能假设绝对 `/assets/` 路径。
- `package.json` 没有 WASM 运行时、Rust/WASM 构建插件或业务 Worker 依赖。
- 当前构建成功，Vite 转换 8222 个模块；存在既有资源路径、CSS 旧语法和超大分块警告，但没有由本研究引入的新错误。

证据：

- `frontend/vite.config.js:8-80`
- `frontend/package.json:1-112`
- `frontend/src/common/worker/One.js:1-12`

### 2.2 现有候选代码规模

| 模块 | 源码规模 | 当前职责 |
|---|---:|---|
| 手键训练实时处理 | 33,497 bytes | 接收点划间隔、记录训练日志、转码 |
| 训练结果分析 | 11,070 bytes | 解析报文、匹配日志、统计错码 |
| Morse 音频播放 | 14,055 bytes | 文本转 Morse、控制 AudioWorklet |
| AudioWorklet | 7,227 bytes | 生成音频采样、处理播放状态 |
| Excel 导入页面 | 11,350 bytes | 读取 XLSX、转换行数据、上传 |
| 题库 DOCX 导入 | 25,363 bytes | Mammoth 提取文本、题目格式解析 |
| PCM 播放器 | 3,948 bytes | PCM 数组转换和音频缓冲 |

上述文件不是全部可迁移到 WASM；只有其中的纯计算部分具备迁移边界。

## 3. 排除 OCR

当前 OCR 流程为：

```text
摄像头 → Canvas 截图 → Cropper 裁剪 → HTTP 上传 → 服务端 OCR → 返回结果
```

代码证据：

- `frontend/src/common/utils/ocr/OcrComp.vue:268-315`：摄像头截图和裁剪。
- `frontend/src/common/utils/ocr/OcrComp.vue:367-391`：调用 OCR。
- `frontend/src/common/utils/ocr/OCR.js:5-28`：通过 `fetch(window.ocrUrl)` 调用服务端。

本项目不实施以下方案：

- 浏览器本地 OCR 推理；
- WASM 图像预处理；
- Tesseract / ONNX Runtime Web；
- 浏览器端二值化、去噪、倾斜校正。

前端 OCR 只负责采集、交互式裁剪、上传和展示结果。将图片处理移到 WASM 不符合当前架构目标。

## 4. 候选一：训练日志评分

### 4.1 当前实现

主要文件：

- `frontend/src/views/manage/organization/handkeyZuXun/train/student/js/handKeyTrain.js`
- `frontend/src/views/manage/organization/telexZuXun/train/student/js/trainScore.js`
- `frontend/src/views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js`

当前包含：

- 点、划、间隔时长计算；
- 电码序列转字码；
- 起始符、结束符、转页符识别；
- 少码、多码、错码判定；
- `patLogs` 和 `moresTime` 分组；
- 码率和训练统计。

典型热点：

```js
cacheArr = leafLogs.filter(
  (log, lo) => lo >= pushNumber && lo < (pushNumber + cod.length * 2)
)
```

位置：

- `frontend/src/views/manage/organization/telexZuXun/train/student/js/trainScore.js:197-224`
- 同类逻辑也出现在 `frontend/src/views/manage/organization/handkeyZuXun/train/student/js/trainScore.js`。

手键训练的实时入口在：

- `handKeyTrain.js:185-243`
- `handKeyTrain.js:450-538`

### 4.2 可迁移边界

不能把整个训练 Hook 或 Vue 状态迁移到 WASM。建议保留在 JavaScript：

- WebSerial / WebSocket；
- Vue `ref` 和响应式状态；
- PubSub；
- DOM 和图表；
- 实时按键事件接收；
- 错误提示和页面流程。

可以抽出的纯算法：

```text
输入：
  电码序列 Uint8Array
  点划时长 Float32Array
  间隔时长 Float32Array
  训练规则结构化参数

输出：
  码率、准确率、点划统计、间隔统计、错误分类、分页统计
```

推荐接口必须以批量数组为主，避免每个点划都跨越 JS/WASM 边界。

### 4.3 可行性结论

**可行性：高。**

理由：

- 计算逻辑具有明确的纯函数边界；
- 输入输出可以使用 TypedArray；
- 不依赖浏览器 DOM 和 Vue；
- 可以先保留 JavaScript 实现作为对照和失败回退；
- 训练结果页天然适合批量处理，不要求每个按键都同步调用 WASM。

但当前首先要修正算法复杂度。重复 `filter`、`join` 和对象数组分配可能比 WASM 语言差异更重要。

### 4.4 研究基准

使用 Bun/Node 内核对合成数据进行方向性微基准，不代表真实浏览器或生产数据：

- 100,000 条日志；
- 1,000 个分组；
- 当前风格的重复 `filter`：中位数约 **147.3 ms**；
- 等价的单次线性游标扫描：中位数约 **0.016 ms**。

该结果说明第一收益点是删除重复扫描和中间数组，而不是直接引入 WASM。实际项目应使用真实 `patLogs`、`moresTime` 数据重新测量。

### 4.5 推荐实施方式

```text
训练页面 JS
    ↓ 批量提取当前页日志
Web Worker
    ↓ TypedArray
优化后的 JS scorer / WASM scorer
    ↓ 紧凑统计结果
主线程更新 Vue 和图表
```

第一版建议同时保留：

```text
WASM scorer + optimized JS scorer
```

在开发环境或灰度环境比较两者结果，要求结果逐字段一致后再默认使用 WASM。

## 5. 候选二：Excel / DOCX 导入

### 5.1 Excel 导入

文件：

- `frontend/src/views/manage/preJob/ditto/militaryDeploy/Index.vue:127-159`

当前流程：

```js
FileReader.readAsBinaryString
xlsx.read
xlsx.utils.sheet_to_json
遍历所有 Sheet 和行
saveBatchData
```

### 5.2 DOCX 导入

文件：

- `frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:567-579`
- `frontend/src/views/manage/basicTheory/fixedMessage/details/js/index.js:184-198`
- `frontend/src/views/manage/postJob/hanzi/articleManage/Index.vue:147-159`

当前使用 `mammoth.extractRawText`，随后还会执行题目格式解析和批量保存。

### 5.3 实际构建体量

当前构建产物中：

| 分块 | 未压缩大小 | gzip 大小 |
|---|---:|---:|
| `xlsx` | 322.18 kB | 109.86 kB |
| `mammoth` | 124.05 kB | 34.94 kB |
| `docx` | 311.48 kB | 85.02 kB |
| `HandKeyTrain` 最大相关分块 | 42.59 kB | 12.06 kB |

这些依赖目前已经被 Vite 分块，直接换成 WASM 不一定能降低包体积。WASM 还会增加 `.wasm` 文件、加载和初始化成本。

### 5.4 推荐方案

第一阶段只做 Worker：

```text
File / ArrayBuffer
    ↓ transferable object
Import Worker
    ↓ xlsx / mammoth
规范化行数据或文本
    ↓
主线程执行校验、提示和上传
```

必须使用 `ArrayBuffer` 转移，不要先转成超大的 binary string 再复制。

### 5.5 WASM 适用条件

只有满足以下条件才建议重新评估 WASM：

- 真实导入文件达到数万行或几十 MB；
- Worker 迁移后仍有明显 CPU 长任务；
- 需要一次处理多 Sheet 或大量 DOCX；
- 具备可复用的 Rust/C++ 解析器；
- 能接受对 XLSX/DOCX 兼容性的重新验证。

**可行性：Worker 高，WASM 中。**

XLSX 和 DOCX 解析涉及 ZIP、XML、字符串、格式兼容和错误处理。直接重写会产生较大维护成本。当前没有生产样本文件，因此不能对真实兼容性做最终承诺。

## 6. 候选三：音频和 PCM

### 6.1 当前 Morse 播放不建议改

当前高性能 Morse 播放器已经使用 AudioWorklet：

- `frontend/src/common/utils/voice/MorseVoiceHighPerformance.js:270-310`
- `frontend/src/common/utils/processor.js:1-257`

`processor.js` 在 AudioWorklet 中按采样生成正弦波，并处理点划时序：

- `processor.js:92-153`
- `processor.js:156-219`

这已经避免了主线程实时音频计算。单纯将 `Math.sin` 和播放状态机改为 WASM，预计收益不足以覆盖：

- WASM 模块加载；
- AudioWorklet 内初始化；
- 线性内存管理；
- JS/WASM 数据交换；
- 跨浏览器和 Electron WebView 调试成本。

### 6.2 未来可用 WASM 的场景

以下需求出现后，WASM 才有明确价值：

- 麦克风原始音频识别 Morse；
- FFT 频率检测；
- 噪声过滤；
- PCM 重采样；
- 多路音频混合；
- 批量音频质量分析。

`frontend/src/common/utils/pcm.js:55-113` 存在 PCM 数组转换和音频缓冲处理，可作为后续纯数组算法的候选。

**可行性：针对新增 DSP 功能高，针对当前播放功能低。**

## 7. 不建议 WASM 化的模块

以下模块没有足够的计算密度，WASM 边界成本通常会抵消收益：

- `frontend/src/common/mixin/useMorse.js`：小型编码查表；
- Morse 文本转码：字符串遍历和少量数组创建；
- WebSocket JSON 编解码；
- `frontend/src/common/utils/WebSerial.js`：串口包拆分和事件派发；
- Vue 响应式逻辑；
- Ant Design 表单；
- ECharts 配置和数据组装；
- 表格分页、筛选和排序；
- 倒计时；
- 登录、权限、菜单和 API 请求。

Babylon.js / Three.js 当前负责 WebGL 渲染。WASM 不能替代 WebGL；只有复杂物理、路径规划、网格生成等 CPU 计算才值得单独评估。

## 8. 技术架构建议

### 8.1 Worker 目录

将现有占位文件替换为按能力划分的 Worker，例如：

```text
frontend/src/common/worker/
├── import.worker.js
└── training-score.worker.js
```

Worker 只处理纯数据，不直接导入 Vue、Pinia、Ant Design 或路由。

### 8.2 WASM 模块加载

推荐按页面能力懒加载：

```text
进入训练结果页 → 加载评分 Worker
进入大文件导入流程 → 加载导入 Worker
```

不要在应用入口同步加载 WASM。

由于 Vite 使用 `base: './'`，资源引用必须遵循模块 URL 方式，避免：

```js
fetch('/assets/module.wasm')
```

这类依赖根路径的写法。

### 8.3 数据接口

推荐接口：

```text
ArrayBuffer / TypedArray → Worker → WASM → TypedArray / 小型 JSON
```

不推荐接口：

```text
大量嵌套 JS 对象 → WASM
每条记录一次 WASM 函数调用
WASM 直接操作 Vue 响应式对象
```

### 8.4 失败回退

WASM 加载失败、浏览器不支持或模块校验失败时，应回退到优化后的 JavaScript 实现。回退必须保证：

- 输出语义一致；
- 不改变现有 API 请求格式；
- 不改变评分规则；
- 不改变 UI 展示；
- 不吞掉原始解析错误。

## 9. 实施阶段与验收门槛

### 阶段 0：建立真实基线

记录以下数据：

- Excel 文件大小、行数、Sheet 数；
- DOCX 文件大小、文本长度和题目数量；
- 训练日志数量、`patLogs` 数量、分组数量；
- 主线程长任务时长；
- 页面交互期间 FPS、输入延迟；
- 当前端到端导入和评分耗时。

必须使用真实业务样本，不以合成基准替代生产结论。

### 阶段 1：Worker 化

先实现：

1. XLSX 解析 Worker；
2. DOCX 文本提取 Worker；
3. 训练日志批量分析 Worker；
4. 主线程和 Worker 的错误、取消、超时处理；
5. ArrayBuffer transferable 传输。

阶段 1 的目标是降低主线程阻塞，不改变业务结果。

### 阶段 2：JavaScript 算法优化

重点：

- 用游标替代重复 `filter`；
- 用索引和计数器替代重复 `join`；
- 批量解析 JSON；
- 降低中间对象和数组创建；
- 统一输入输出为 TypedArray 或紧凑结构。

### 阶段 3：训练评分 WASM PoC

仅实现一个纯评分函数，不迁移整个页面。

WASM PoC 必须满足：

- 与优化后的 JS 输出逐字段一致；
- 真实样本覆盖正常、少码、多码、错码、跨页、空日志；
- 批量执行而不是逐事件调用；
- Worker 中运行，不阻塞 UI；
- 模块失败时能够回退 JS。

### 阶段 4：按数据决定是否保留 WASM

建议保留 WASM 的门槛：

- 相对于优化后的 JS，核心 CPU 时间至少有约 2 倍收益；或
- 即使速度收益有限，也显著降低主线程长任务；并且
- WASM 模块大小、加载耗时和维护成本在可接受范围内。

如果阶段 2 的优化已经消除长任务，应取消 WASM 迁移，不为了使用 WASM 而使用 WASM。

## 10. 风险矩阵

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| JS/WASM 数据复制 | 性能收益被抵消 | 使用 TypedArray 和 transferable ArrayBuffer |
| WASM 加载失败 | 功能不可用 | 保留优化后的 JS 回退 |
| Worker 错误难调试 | 研发成本增加 | 统一 requestId、错误堆栈和超时消息 |
| 评分结果不一致 | 业务错误 | JS/WASM 双跑比对和固定样本校验 |
| XLSX/DOCX 兼容性下降 | 导入失败 | 先 Worker 化现有库，不直接重写解析器 |
| `base: './'` 路径错误 | Electron/部署环境加载失败 | 使用 `new URL(..., import.meta.url)` |
| WASM 包体积增加 | 首次加载变慢 | 页面级懒加载和压缩 |
| 过早迁移 | 维护成本高于收益 | 按阶段基线和收益门槛决策 |

## 11. 最终决策

### 建议立即做

- 建立真实数据性能基线；
- 把 XLSX、DOCX 解析迁移到 Web Worker；
- 把训练日志批量分析迁移到 Web Worker；
- 先优化训练日志算法，消除重复扫描；
- 统一 Worker 的错误、取消和 transferable 数据协议。

### 建议暂不做

- OCR WASM 化；
- 当前 Morse 正弦波播放 WASM 化；
- Morse 查表 WASM 化；
- Vue 和 WebSocket 业务整体 WASM 化；
- 直接重写 XLSX/DOCX 解析器。

### 建议在阶段 1、2 之后再做

- 训练日志评分 WASM PoC；
- PCM 重采样、FFT、降噪 WASM；
- 复杂 3D 物理或网格算法 WASM。

**最终结论：项目可以引入 WASM，但应把 WASM 限定为训练日志评分等批量纯算法的加速层。第一落地动作不是编写 WASM，而是 Worker 化和基线测量。**

## 12. 研究验证记录

| 验证项 | 结果 |
|---|---|
| 前端生产构建 | 通过，Vite 4.5.14，8222 个模块转换完成 |
| 当前 Worker 现状 | 只有 `src/common/worker/One.js` 占位实现 |
| WASM 业务模块 | 未发现 |
| Excel 依赖 | `xlsx` 0.18.5，构建分块约 322.18 kB |
| DOCX 文本依赖 | `mammoth` 1.12.2，构建分块约 124.05 kB |
| DOCX 生成依赖 | `docx`，构建分块约 311.48 kB |
| 合成 Excel 基准 | 20,000 行、约 3.03 MB，解析和转 JSON 中位数约 137.3 ms |
| 合成日志扫描基准 | 重复 `filter` 中位数约 147.3 ms；线性扫描约 0.016 ms |
| 基准限制 | Bun/Node 微基准，非真实浏览器、非生产样本，不替代阶段 0 基线 |
| Chromium 全候选基准 | 通过，覆盖 XLSX/DOCX 主线程与 Worker、训练聚合、PCM、FIR、FFT、Morse 和正弦波内核 |
| 全候选基准限制 | 使用合成数据和临时纯计算 WASM 内核；正式实施前需用真实生产样本复测 |


## 13. 全候选对比测试补充

### 13.1 测试方法和限制

本轮补充测试使用两种环境：

1. **Chromium 实际浏览器环境**：测试 XLSX、DOCX、Worker、训练评分内核、音频内核和 WASM 加载行为。
2. **Bun/Node 内核**：测试 WebSerial 纯分类、Worker 往返和部分初始化成本。

测试机器、浏览器和生产设备不同，结果用于候选排序，不作为所有用户设备的固定性能承诺。WASM 数值测试使用一个临时 Rust `wasm32-unknown-unknown` 纯计算内核，不是生产代码；其目的为测量语言边界、TypedArray 数据布局和算法类型的上限。该内核未提交到业务源码。

所有时间均为毫秒，除非另有说明。Chromium 计时器存在约 0.1 ms 量化，因此小于 0.1 ms 的结果只说明“低于当前计时分辨率”，不能解释为精确零耗时。

### 13.2 Excel 解析：主线程与 Worker

测试使用 `xlsx` 0.18.5，真实浏览器加载项目当前依赖的浏览器构建。每个样本使用单 Sheet，字段结构与军语导入一致：`一级目录`、`二级目录`、`详情`。Worker 使用持久 Worker，传输 `ArrayBuffer`，测量包括消息往返和解析。

| 行数 | 文件大小 | 主线程中位数 | 主线程 P95 | Worker 中位数 | Worker P95 | 结果 |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 200 kB | 11.7 | 20.6 | 12.2 | 123.3 | 1,000 行 |
| 5,000 | 959 kB | 43.3 | 54.3 | 40.2 | 48.7 | 5,000 行 |
| 20,000 | 3.86 MB | 147.1 | 181.7 | 157.6 | 159.8 | 20,000 行 |
| 50,000 | 9.70 MB | 425.2 | 483.3 | 418.1 | 428.1 | 50,000 行 |

结论：

- Worker **没有显著降低解析 CPU 时间**；它的价值是把 20,000 行以上的 150–480 ms 工作从主线程移走。
- 50,000 行时，主线程已经明显超过 50 ms 长任务阈值，Worker 化有实际收益。
- 当前没有证据证明需要用 WASM 重写 XLSX 解析器。WASM 版本必须先达到兼容性一致，并且不能只比较解析线程内部耗时。

### 13.3 DOCX 解析：主线程与 Worker

测试使用 `mammoth` 1.12.2 和生成的中文/英文混合段落，结果为提取文本长度。

| 段落数 | 文件大小 | 主线程中位数 | 主线程 P95 | Worker 中位数 | Worker P95 | 结果字符数 |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 13.1 kB | 144.4 | 148.6 | 121.7 | 144.3 | 40,780 |
| 5,000 | 34.8 kB | 173.4 | 180.9 | 151.5 | 154.1 | 212,780 |
| 20,000 | 119 kB | 313.7 | 318.6 | 301.5 | 305.8 | 877,780 |

结论：

- Worker 的端到端耗时没有稳定优势；不同轮次会受 JIT、缓存和 GC 影响。
- Worker 的确定性收益是主线程不被 DOCX 解压、XML 解析和文本生成阻塞。
- 1,000 段已经可能产生超过 100 ms 的主线程任务，题库和文章导入都应优先 Worker 化。
- 不建议为了这些样本直接重写 DOCX 解析器为 WASM。

### 13.4 训练日志分组：当前写法与线性写法

测试对比了当前 `leafLogs.filter(...)` 分组方式和语义等价的游标 + `slice` 方式，均保留相同的日志引用和分组长度。

| 日志数 | 分组数 | 重复 `filter` 中位数 | 游标 + `slice` 中位数 | 结果是否一致 |
|---:|---:|---:|---:|:---:|
| 1,000 | 100 | 0.5 | <0.1 | 是 |
| 10,000 | 400 | 20.4 | <0.1 | 是 |
| 100,000 | 1,000 | 523.2 | 约 0.1 | 是 |

上述代码对应：

- `frontend/src/views/manage/organization/telexZuXun/train/student/js/trainScore.js:197-224`
- `frontend/src/views/manage/organization/handkeyZuXun/train/student/js/trainScore.js:166-193`
- `frontend/src/views/manage/organization/electronKeyZuXun/train/student/js/trainScore.js:175-202`

结论：当前训练结果分析的第一瓶颈是算法和中间数组分配，不是 JavaScript 语言本身。**必须先做线性扫描；直接把当前 `filter` 代码搬进 WASM 会把错误的复杂度一起迁移。**

### 13.5 训练评分纯数值内核：JavaScript 与 WASM

测试模型：100,000 条 `Uint32Array` 日志、1,000 个分组，计算每组日志的累计值。该模型代表可迁移的数值聚合部分，不代表完整的 Vue 评分流程。

| 实现 | 中位数 | P95 | 结果 |
|---|---:|---:|---:|
| Chromium 优化 JS，含 JS 输出数组写入 | 0.017 | 0.024 | 39,180 |
| WASM，输入和输出已在 WASM 内存 | 0.006 | 0.011 | 39,180 |
| WASM，包含每轮输入 TypedArray 复制 | 0.014 | 0.019 | 39,180 |

JS 与 WASM 输出逐项一致，最大误差为 0。纯计算阶段 WASM 约为 JS 的 2.8 倍；包含输入复制后约为 1.2 倍。该结果说明：

- 批量、数值型、计算密集的评分仍然适合 WASM；
- 如果算法只是分组、计数和少量比较，WASM 收益很容易被数据复制抵消；
- 实时按键事件不适合逐事件调用 WASM；
- 真实生产评分必须先使用优化后的 JS 结果作为基准，再决定是否保留 WASM。

### 13.6 音频和 DSP 候选

以下测试使用 Chromium，输入输出均为预分配 TypedArray；WASM 计算阶段不包含模块首次加载。它们是 DSP 内核上限测试，不代表当前页面已经存在这些完整功能。

| 内核 | 测试规模 | JS 中位数 | WASM 中位数 | WASM/JS | 结论 |
|---|---:|---:|---:|---:|---|
| PCM `Int16 → Float32` | 100,000 样本 | 0.21 | 0.04 | 5.25× | 可行，但需批量传输 |
| 线性重采样 | 480,000 → 160,000 | 1.47 | 1.77 | 0.83× | 当前 WASM 更慢，不迁移 |
| 双声道混音 | 240,000 帧 | 0.29 | 0.40 | 0.73× | JS 足够 |
| 31 阶 FIR 滤波 | 480,000 样本 | 16.93 | 7.73 | 2.19× | 具备 WASM 价值 |
| 16,384 点 FFT | 16,384 点 | 0.80 | 0.45 | 1.78× | 具备 WASM 价值 |
| 正弦波生成 | 480,000 样本 | 2.77 | 3.13 | 0.88× | 不迁移 |

数值一致性校验：

- PCM 最大误差：0；
- 线性重采样最大误差：`1.19e-7`；
- 混音最大误差：0；
- FIR 最大误差：`2.98e-7`；
- 正弦波最大误差：0；
- FFT 目标频点误差：小于 `1e-12`。

当前 `frontend/src/common/utils/processor.js:123-133` 每个 AudioWorklet 块只有通常 128 个采样，单块正弦波生成基准约 0.0008 ms；即使每秒持续生成 480,000 个采样，整体约 2.77 ms。因此当前正弦波播放器不应该改成 WASM。WASM 应保留给未来的 FFT、降噪、复杂滤波和批量音频分析。

### 13.7 Morse 文本编码

测试使用 108,000 个字母、数字和空格，输出 792,000 个 Morse 单元，并与当前 `MorseVoiceHighPerformance.convert()` 结果逐项校验。

| 实现 | 中位数 | P95 | 输出一致性 |
|---|---:|---:|:---:|
| 当前风格 JS 数组输出 | 5.42 | 6.25 | 基准 |
| WASM 预分配字节输出 | 0.33 | 0.41 | 一致 |

虽然该测试显示较大差距，但 WASM 使用了预分配线性内存，JS 测试包含对象/数组输出分配；当前页面的单次 Morse 文本远小于 108,000 字符，且 `convert()` 耗时不在已确认的实时瓶颈路径上。因此不建议仅为 Morse 查表引入 WASM。

### 13.8 WebSerial、JSON 和 Worker 边界

#### WebSerial

使用当前 `WebSerial.isElectronKey()` 对 100,000 个串口包做纯分类，Bun/Node 中位数约 **1.92 ms**，P95 约 **3.54 ms**。该逻辑主要是事件和包边界处理，不适合 WASM。

#### JSON

3.09 MB、100,000 条训练日志的 `JSON.parse` 中位数约 **6.38 ms**。除非后端协议改变为更大的批量二进制数据，否则把 JSON 解析改成 WASM 没有合理收益。

#### Worker 边界

Bun/Node 中：

- 1 kB transferable ArrayBuffer Worker 往返中位数约 **0.066 ms**，P95 约 **0.488 ms**；
- 100,000 条训练日志和 1,000 个分组通过 transferable 数组交给 Worker，中位数约 **0.231 ms**，不含业务计算。

Worker 边界成本小于 XLSX、DOCX 和大批量训练解析本身，因此 Worker 化是低风险的第一步。

### 13.9 Babylon / Three.js / 3D

当前源码实际使用 Babylon.js 做教学场景渲染，构建产物 `@babylonjs` 分块约 **4.63 MB**。仓库中的三维物理脚本大小为：

- `public/js/3d/ammo.js`：约 1.8 MB；
- `public/js/3d/cannon.js`：约 520 kB；
- `public/js/3d/Oimo.js`：约 308 kB。

本次源码搜索没有发现这些物理脚本被当前教学页面作为业务物理计算路径调用，也没有发现需要迁移的 CPU 物理热点。当前 Babylon/Three.js 的渲染由 WebGL 完成，WASM 不能替代渲染管线。

结论：3D 只在未来出现复杂碰撞、路径规划、网格生成或 CPU 粒子模拟后再测量；当前不具备 WASM 改造依据。

### 13.10 WASM 模块加载成本

本轮完整纯计算测试模块：

- 原始大小：**136,362 bytes**；
- Bun/Node 实例化中位数：**4.08 ms**；
- P95：**51.26 ms**；
- 最大值：**60.13 ms**。

该模块包含多个测试内核，生产单功能模块可能更小或更大。结论是 WASM 必须按页面能力懒加载，不能在应用入口同步加载。

## 14. 基于实测数据的结论修订

本轮实测后，候选优先级调整为：

1. **立即 Worker 化 XLSX/DOCX 和训练结果批处理**：主线程阻塞已实测达到 150–480 ms。
2. **先优化训练日志算法**：当前 `filter` 到 `slice` 的收益远大于语言迁移收益。
3. **仅在复杂评分持续 CPU 密集时实施训练评分 WASM**：简单聚合包含数据复制后只有约 1.2 倍收益。
4. **未来 DSP 优先评估 FIR/FFT**：本轮纯计算分别约 2.19 倍和 1.78 倍；PCM 转换也有约 5.25 倍潜力。
5. **不迁移当前正弦波播放、Morse 查表、混音、线性重采样、WebSerial 和 JSON**。
6. **3D、OCR 暂不实施**：3D 当前没有确认的 CPU 热点；OCR 已按要求全部由服务端完成。

最终决策门槛保持不变：只有当生产数据上的 WASM 端到端收益（包含复制、加载和 Worker 通信）相对优化后的 JS 达到约 2 倍，或能显著消除主线程长任务时，才保留 WASM 实现。

