# 播放器与 App 链路性能优化方案

## 结论

不引入 wasm 做播放器优化。

`ref/NativeWasmTv` 里的 wasm/native 主要用于特定源的解密、签名和 native QuickJS 脚本执行。播放核心仍是 IJK/native codec。当前项目应借鉴它的工程体系：播放链路可观测、可恢复、可诊断、可回归。

## 目标

- 缩短点击播放到首帧时间。
- 修复暂停、返回详情页、重新播放后卡住的问题。
- 修复 IJK 硬解崩溃，而不是屏蔽硬解。
- 稳定 DBZY/m3u8 本地代理播放。
- 降低低端电视上的内存、Surface、decoder 压力。
- 每次优化都有日志、指标和回归测试支撑。

## 参考项目可借鉴点

`NativeWasmTv` 中值得借鉴的是播放工程能力，不是直接移植实现：

- `DirectVideoView`：明确管理 `SurfaceView` / `TextureView`，硬解优先走零拷贝 Surface。
- `HlsProxyServer`：HLS 代理负责分片重试、缓存、预取、403 识别、码率统计。
- `scheduleVideoRenderWatchdog`：prepared 后无首帧时自动诊断和恢复。
- `recoverStalledPlayback`：播放卡死后保留进度重启播放器。
- `NetworkClient`：统一 OkHttp、DNS、连接池、超时。
- `PerformanceRegressionInstrumentation`：把性能问题固化成回归测试。
- `HardwareStreamDiagnostic`：独立验证硬解、软解和不同流类型。
- `CrashReporting`：native crash 附带播放上下文。

## 一期：播放稳定性

### 1. PlaybackTrace

每次播放生成 `playSessionId`，贯穿播放链路。

记录节点：

```text
DETAIL_CLICK
PLAY_INIT
SOURCE_PLAY_START
SOURCE_PLAY_RESULT
SHARE_RESOLVE_START
SHARE_RESOLVE_RESULT
M3U8_FETCH_START
M3U8_FETCH_DONE
M3U8_PROXY_READY
PLAYER_SET_SOURCE
PLAYER_PREPARED
PLAYER_FIRST_FRAME
BUFFERING_START
BUFFERING_END
PAUSE
RESUME
RELEASE
ERROR
```

日志必须包含：

```text
sessionId, sourceKey, playFlag, playerType, ijkCodec, renderType,
urlType, isLocalProxy, elapsedMs, errorCode, errorMessage
```

### 2. IJK 硬解修复

不屏蔽硬解。

修复方向：

- IJK 硬解强制绑定 `SurfaceView`。
- `ijk: []` 或缺失时回落内置默认软/硬解配置。
- IJK codec 列表为空时，设置页和播放页切换不崩。
- prepared 后 N 秒无首帧，触发首帧 watchdog。
- watchdog 记录设备、SDK、codec、render、URL 类型、m3u8 代理状态。
- 首次无首帧先重建硬解；仍失败再提示切软解，不静默吞错。

验收：

- 选择 `IJK + 硬解码` 不直接退出。
- prepared 后无首帧能被日志明确定位。
- native crash 前能看到播放上下文。

### 3. m3u8 代理 session 化

当前全局 `/m3u8` 容易被新旧播放互相污染。

目标路径：

```text
/play/{sessionId}/index.m3u8
```

要求：

- 每次播放独立 session。
- session 绑定自己的 m3u8 内容和 headers。
- 退出播放时清理 session。
- 响应 MIME 使用 `application/vnd.apple.mpegurl`。
- 相对 ts/key/map 路径转绝对 URL。
- 403、404、timeout 明确上报，不让播放器无限 buffering。

### 4. 卡死恢复

判断条件：

- 有网速。
- position 长时间不动。
- 长时间处于 buffering。
- 无 error 回调。

恢复策略：

1. 记录当前进度。
2. release 当前播放器。
3. 延迟重建同播放器。
4. 恢复进度。
5. 同源只自动恢复一次。
6. 失败后切备用播放器或提示明确错误。

## 二期：链路性能优化

### 1. 统一网络栈

- Source、m3u8、subtitle、parser 尽量共享 OkHttp。
- 统一 DNS、连接池、超时、重试策略。
- 保存设置不取消活跃播放请求。
- DNS/代理变更时才清连接池。

### 2. 降低首帧竞争

播放首帧前降低非关键任务优先级：

- 图片加载。
- 字幕搜索。
- 弹幕加载。
- 详情页额外刷新。
- WebView 嗅探残留任务。

### 3. 生命周期治理

- 退出播放时明确 release。
- 返回详情页时明确 pause/release 策略。
- 新播放开始前等待旧播放器释放关键资源。
- WebView 嗅探结束后及时销毁。
- 本地代理 session 不跨播放复用。

### 4. 低端电视策略

- 降低搜索和解析并发。
- Exo cache 限制容量。
- IJK cache 默认关闭。
- 限制 HLS 预取窗口。
- 记录 native heap 和 decoder 状态。

## 三期：诊断与回归测试

### PlaybackRegressionInstrumentation

覆盖：

- DBZY m3u8。
- DBZY `/share/`。
- 普通 mp4。
- 错误 m3u8。
- pause/resume。
- 返回详情页再播放。

### HardwareDecoderDiagnostic

覆盖：

- IJK 硬解。
- IJK 软解。
- Exo。
- SurfaceView / TextureView。
- prepared 后首帧检测。

### PerformanceRegression

覆盖：

- 连续播放 30 次无崩溃。
- 退出详情 30 次无 decoder 泄漏。
- m3u8 session 不串流。
- 卡死 watchdog 能触发恢复。

## 不做

- 不引入 wasm 播放器。
- 不重写播放器核心。
- 不直接移植 `NativeWasmTv`。
- 不屏蔽 IJK 硬解。
- 不把所有问题简单切到 Exo。

wasm/native 只在以后遇到特定源需求时再考虑：

- TS/NAL 解密。
- 接口签名。
- 低版本 Android 替代 WebView 执行 JS。

## 第一批交付顺序

1. `PlaybackTrace` 最小实现。
2. IJK 硬解 `SurfaceView` 和首帧 watchdog。
3. m3u8 代理 session 化。
4. 卡死恢复。
5. Debug 回归测试脚本。
