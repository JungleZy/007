# 模拟器手键每100组自动分页

## 行为

手键每完成100组（十行）都发送完整翻页控制符，正好满页且没有后续正文时也必须发送。翻页符复用 `morse::CONTROL_TURN`，为三段独立的 `00`；前后以及三段之间均保留组间隔，使客户端能识别并处理分页。

加入改错后，页位置以逻辑组游标为准：每行10组、每页10行，第十行最后一组及其必要重发完成后才分页。改错符与重发不额外占据新组。每页组数现由控制台排版常量 `GROUPS_PER_LINE × 10` 定义，避免排版与页大小出现两套数字。

- 101组：第100组 → 翻页符 → 第101组。
- 201组：在第100组和第200组之后分别分页。
- 按组数计数，支持空格/换行分组，也支持连续文本按每4字符拆组。
- 正好满100/200组：`tail=none`仍发送满页分页符；`tail=end`先发送分页符再发结束符；`tail=turn`复用已经发送的分页符，不重复发送。
- 不满一页的最后一段仍按所选收尾方式处理。
- 开始符只发送一次；正文内容、顺序、组编号保持一致。

实现位于 `keysim/src/keying.rs`，网页预览、发送及CLI共用手键时间轴，因此各输出通道均生效。控制台预览新增 `pageBreaks` 计数，页面显示本次分页符次数。

## 线路通报拍发端自动翻页

此前线路通报 `lineNotify/js/Issue.js` 未识别控制符，只按固定间隔累计位置，导致无法随分页符正确切换报底。现新增 `handControl.js`，复用手键边沿解码器及实际时长校准，实时识别完整三截分页符与结束符；三截各自必须独立成组，末截之后即使没有下一条输入，也会通过静默定时器完成识别。

- 收到分页符且还有下一页时，切换下一页报底；末页收到分页符或收到结束符时执行发报完成，避免越界请求。
- 采集页码与当前显示页码分开维护。下一页请求未完成或失败时，仍转发并采集后续按键，记录归入下一页；旧页报底保留显示，失败后重试目标页。
- 已预取页复用缓存，相同请求合并，较早返回的旧页请求不得覆盖最新翻页目标。
- REST分页接口仍为UserApi.js中的 `/api/simulation/router/findPage`，roomId/pageNumber/userId参数及信封结构不变；WebSocket仍转发原来的play/type=1按下抬起消息。

## 验证

- `keysim/tests/hand_pagination.rs`现有5项回归，覆盖99/100/101/199/200/201组、非等长组、连续文本、收尾选择、实际按下/抬起帧、分页符前后及内部间隔、三种拍发风格与码率定标。
- `cargo test keying:: --lib`：11项通过。
- 本轮相关Rust测试50项通过（keying 11、sinks 5、hand_pagination 5、hand_corrections 7、drift 11、console_e2e 7、usbip_protocol 4）。
- `scripts/check-hand-corrections.mjs`共12种场景通过，使用个人岗位手键生产代码核对逐组历史，补充满100组先分页再结束的场景。
- `scripts/check-line-pagination.mjs`将真实模拟器帧送入线路通报生产composable：100组静默收尾（机械/真人）、101组结束、200组结束、201组真人双改错共5种场景通过，验证实际报底内容、页码、记录归属及结束次数。
- 前端 `npm test` 110项通过；6项新增用例覆盖完整/不完整分页符、普通字母I不误翻页、慢请求持续采集、失败重试、末页只结束一次。受影响文件ESLint无错误或警告。
- Edge控制台检查验证正好100组且收尾为none时显示“分页符1次”。
- `cargo build --release`成功。
- 全量 `cargo test` 被既有 `tests/install_replace.rs` 无条件引用Unix API阻断；单独运行全部lib测试为49通过、1失败，失败项为已有 `install::tests::manual_command_is_absolute_and_runnable` 对Windows安装提示的不兼容断言。未将这两项问题计作本次测试通过。

## 本机更新

确认18700端口模拟器未在发送后，停止旧进程，构建并以原启动参数重启 `keysim/target/release/keysim.exe`。恢复此前开启的虚拟串口，最终状态open=true、replay.running=false、injectClients=1。

重启后调用运行中服务 `/api/preview`，确认100组在none/end/turn三种收尾下均为一个完整分页符。最终状态open=true、replay.running=false、injectClients=1。实际帧已驱动页面生产逻辑验证，页面API在脚本中由夹具提供；未向用户正在连接的训练页面发送测试报文，真实设备和登录会话下的端到端联调仍待验收。
