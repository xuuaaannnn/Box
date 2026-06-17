# DBZY 源播放“找不到 / 未找到 xxx”修复记录

## 背景

`TASK.md` 中的问题源：

```js
{
  key: 'cms_dbzy',
  name: 'CMS-DBZY',
  type: 1,
  api: 'https://caiji.dbzy5.com/api.php/provide/vod',
  searchable: 1,
  quickSearch: 1,
  filterable: 1,
}
```

现象：

- 站点可以搜索；
- 详情页可以打开；
- 程序内可选播放器均无法播放；
- 播放加载后提示“找不到 / 未找到 xxx”。

`reference/TVBox.apk` 是可用参考 APK，可用于反编译对比。

## 最终确认的根因

本次问题实际包含两个层次：

1. **最终导致“点播放后回到详情页/提示获取播放信息错误”的直接根因**：播放器初始化阶段读取 IJK 解码配置时，`ijkCodes` 列表为空，但 `ApiConfig.getIJKCodec()` 无兜底地执行了 `ijkCodes.get(0)`，触发 `IndexOutOfBoundsException`。
2. **DBZY m3u8 后续播放兼容问题**：DBZY m3u8 内部包含相对路径，当前项目又会把净化后的 m3u8 挂到本地代理 `/m3u8`，如果不转绝对 URL，分片/key 会被错误解析到 `127.0.0.1`。

### 根因 1：IJK 配置为空导致播放器初始化异常

通过安装带临时 `DBZYFIX` 日志的 Debug 包，并让用户在电视上点击 DBZY 播放后抓取 `logcat`，最终捕获到关键异常：

```text
java.lang.IndexOutOfBoundsException: Invalid index 0, size is 0
at com.github.tvbox.osc.api.ApiConfig.getIJKCodec(ApiConfig.java:841)
at com.github.tvbox.osc.util.PlayerHelper.updateCfg(PlayerHelper.java:45)
```

对应原始代码逻辑：

```java
public IJKCode getIJKCodec(String name) {
    for (IJKCode code : ijkCodes) {
        if (code.getName().equals(name))
            return code;
    }
    return ijkCodes.get(0);
}
```

当配置文件没有提供 `ijk` 节点，或配置加载后 `ijkCodes` 为空时，播放页在执行 `initPlayerCfg()` / `PlayerHelper.updateCfg()` 阶段直接抛异常。表现上就是：

```text
DetailActivity -> PlayActivity -> 初始化播放器配置异常 -> errorWithRetry("获取播放信息错误") -> finish 回详情页
```

因此用户看到的是“播放不了 / 未找到 / 找不到 xxx”，但第一现场并不是 DBZY 接口返回错误，而是播放前的本地播放器配置崩溃。

修复方式：`ApiConfig.getIJKCodec(String name)` 增加空列表兜底，返回一个空 option 的默认 `IJKCode`，避免播放器配置缺失时崩溃。

修改文件：

```text
app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java
```

修复后逻辑：

```java
public IJKCode getIJKCodec(String name) {
    if (ijkCodes != null) {
        for (IJKCode code : ijkCodes) {
            if (code != null && code.getName() != null && code.getName().equals(name))
                return code;
        }
        if (!ijkCodes.isEmpty()) return ijkCodes.get(0);
    }
    IJKCode fallback = new IJKCode();
    fallback.setName(TextUtils.isEmpty(name) ? "默认" : name);
    fallback.setOption(new LinkedHashMap<>());
    return fallback;
}
```

### 根因 2：DBZY m3u8 相对路径与本地代理冲突

DBZY 返回的直链 m3u8 内容中包含相对路径，例如：

```m3u8
#EXT-X-KEY:METHOD=AES-128,URI="/20250929/cSKqjtQ7/3616kb/hls/key.key"
#EXTINF:3,
/20250929/cSKqjtQ7/3616kb/hls/w8txnwP5.ts
```

当前项目播放 m3u8 时会先执行广告净化逻辑：

```java
RemoteServer.m3u8Content = M3U8.purify(baseUrl, content);
startPlayUrl("http://127.0.0.1:" + RemoteServer.serverPort + "/m3u8", headers);
```

也就是说，播放器最终播放的是本地代理地址：

```text
http://127.0.0.1:<port>/m3u8
```

如果净化后的 m3u8 仍保留相对路径，播放器会把分片地址解析成：

```text
http://127.0.0.1:<port>/20250929/cSKqjtQ7/3616kb/hls/w8txnwP5.ts
```

但本地 `RemoteServer` 并不存在这些分片文件，因此出现“找不到 / 未找到 xxx”。

## 修复方案

### 方案 1：播放器配置空列表兜底

见上文 `ApiConfig.getIJKCodec(String name)` 修复。该修复解决播放页初始化阶段因 `ijkCodes` 为空而直接异常的问题。

### 方案 2：m3u8 写入本地代理前转绝对 URL

在 m3u8 内容写入本地代理前，将其中的相对路径统一转换为远程绝对 URL。

### 修改文件 1

`app/src/main/java/com/github/tvbox/osc/util/M3U8.java`

新增方法：

```java
public static String resolveAll(String base, String m3u8Content)
```

作用：

- 保留原始换行风格；
- 将普通相对分片路径转换为绝对 URL；
- 将 `URI="..."` 形式的 key/map 相对地址转换为绝对 URL；
- 避免处理空行、已是 `http` 的地址、`data:` 地址。

核心逻辑：

```java
public static String resolveAll(String base, String m3u8Content) {
    if (m3u8Content == null || m3u8Content.length() == 0) return m3u8Content;
    String lineSplit = m3u8Content.contains("\r\n") ? "\r\n" : "\n";
    StringBuilder sb = new StringBuilder();
    String[] lines = m3u8Content.split(lineSplit);
    for (int i = 0; i < lines.length; i++) {
        if (i > 0) sb.append(lineSplit);
        sb.append(shouldResolve(lines[i]) ? resolve(base, lines[i]) : lines[i]);
    }
    return sb.toString();
}
```

### 修改文件 2

`app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java`

在 `M3U8.purify(...)` 成功后，启动本地 `/m3u8` 播放前增加：

```java
RemoteServer.m3u8Content = M3U8.resolveAll(baseUrl, RemoteServer.m3u8Content);
```

两个分支均已处理：

1. 原 m3u8 无二级跳转；
2. 原 m3u8 先跳转到二级 m3u8。

### 修改文件 3

`app/src/main/java/com/github/tvbox/osc/ui/activity/PlayActivity.java`

同样在 Activity 播放路径中执行：

```java
RemoteServer.m3u8Content = M3U8.resolveAll(baseUrl, RemoteServer.m3u8Content);
```

避免不同播放入口行为不一致。

### 修改文件 4

`app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java`

针对 DBZY `dbyun` 一类 `/share/` 地址增加已知分享页直链提取逻辑：

```text
https://vodcnd09.oag7h.com/share/MMrZAtjnjp
```

该页面 HTML 中包含类似：

```js
var playlist = '[{"url":"/20250929/cSKqjtQ7/3616kb/hls/index.m3u8"}]';
var main = "/20250929/cSKqjtQ7/index.m3u8";
```

如果直接交给 WebView 嗅探，结果依赖页面加载和播放器嗅探时机，较不稳定。因此在 `getPlay(...)` 中先尝试解析：

1. `playlist` 中的 m3u8；
2. `main` 中的 m3u8；
3. `mp4` 中的直链。

解析到相对路径后，基于分享页 URL 转为绝对 URL，再进入正常直链播放流程。

## 修复后的数据流

```text
用户在详情页选择 DBZY 剧集
  -> DetailActivity 启动 PlayActivity
  -> PlayActivity.initData()
  -> initPlayerCfg()
  -> ApiConfig.getIJKCodec() 即使 ijkCodes 为空也返回默认空配置，不再崩溃
  -> SourceViewModel.getPlay()
  -> 如果是 /share/ 页面，先提取真实 m3u8/mp4 直链
  -> PlayActivity/PlayFragment 下载 m3u8
  -> M3U8.purify(baseUrl, content) 去广告
  -> M3U8.resolveAll(baseUrl, purifiedContent) 相对路径转绝对路径
  -> RemoteServer.m3u8Content
  -> 播放 http://127.0.0.1:<port>/m3u8
  -> 播放器请求远程绝对分片 URL
```

## 排错与配合步骤记录

本次排错不是一次性定位完成，主要过程如下。

### 1. 明确用户现场现象

用户提供的问题现象：

- DBZY 源可搜索；
- 详情页可打开；
- 播放时所有内置可选播放器都失败；
- 加载后提示“找不到 / 未找到 xxx”；
- `reference/` 目录下有一个可播放的参考 APK，可反编译对比。

### 2. 静态分析播放链路

先阅读相关源码入口：

```text
DetailActivity
PlayActivity
PlayFragment
SourceViewModel.getPlay()
M3U8.purify()
RemoteServer /m3u8 本地代理
Exo/IJK 播放器封装
```

确认 CMS 源 `type: 1` 的播放路径大致为：

```text
详情页剧集 URL -> SourceViewModel.getPlay() -> PlayActivity 播放 -> m3u8 可能被下载净化并挂到本地代理
```

### 3. 反编译参考 APK 对比

使用 Jadx CLI 反编译可播放参考 APK，重点对比：

```text
SourceViewModel.java
AbsJson.java
ExoMediaSourceHelper.java
```

用于判断参考 APK 是否有特殊 DBZY 处理、header 处理、m3u8 处理或播放器选择差异。

### 4. 检查 DBZY 实际返回数据

确认 DBZY 详情中存在两类播放地址：

```text
dbm3u8: https://vodcnd09.oag7h.com/20250929/cSKqjtQ7/index.m3u8
dbyun : https://vodcnd09.oag7h.com/share/MMrZAtjnjp
```

并检查 `/share/` 页面 HTML，发现其中实际包含 m3u8：

```js
var playlist = '[{"url":"/20250929/cSKqjtQ7/3616kb/hls/index.m3u8"}]';
var main = "/20250929/cSKqjtQ7/index.m3u8";
```

因此增加 `/share/` 页面直链提取逻辑，避免依赖 WebView 嗅探。

### 5. 分析 m3u8 本地代理问题

继续检查 m3u8 内容，发现 key/ts 使用相对路径：

```m3u8
#EXT-X-KEY:METHOD=AES-128,URI="/20250929/cSKqjtQ7/3616kb/hls/key.key"
/20250929/cSKqjtQ7/3616kb/hls/w8txnwP5.ts
```

结合当前项目会将净化后的 m3u8 放到：

```text
http://127.0.0.1:<port>/m3u8
```

判断如果不转绝对路径，播放器会错误请求本地：

```text
http://127.0.0.1:<port>/20250929/cSKqjtQ7/3616kb/hls/w8txnwP5.ts
```

因此增加 `M3U8.resolveAll(...)`。

### 6. 构建 Debug 包加速现场验证

Release 构建带 R8/minify，单次耗时较长。排错期间改用 Debug 变体：

```bash
JAVA_HOME='C:/Users/xuan/.jdks/ms-17.0.15' PATH='C:/Users/xuan/.jdks/ms-17.0.15/bin':$PATH ./gradlew assembleArmeabiGenericNormalDebug
```

安装到电视：

```bash
adb install -r app/build/outputs/apk/armeabiGenericNormal/debug/TVBox_debug-armeabi-generic-java.apk
```

### 7. 加临时日志并请用户点击复现

由于单靠静态分析还不能解释为什么 PlayActivity 很快返回详情页，于是增加临时 `DBZYFIX` 日志，覆盖：

```text
PlayActivity.initData()
PlayActivity.play()
PlayActivity.mObserverPlayResult.onChanged()
PlayActivity.errorWithRetry()
SourceViewModel.getPlay()
SourceViewModel.resolveKnownShareUrl()
```

然后通过 ADB 抓取日志，让用户在电视上实际点击 DBZY 播放按钮：

```bash
adb logcat -c
adb logcat -v time > /tmp/tvbox_dbzyfix_click.log
```

用户点击完成后，过滤关键日志：

```bash
grep -i -E 'DBZYFIX|AndroidRuntime|FATAL|PlayActivity|SourceViewModel|getPlay|resolveKnownShareUrl|m3u8|Exception|获取播放|未找到|找不到' /tmp/tvbox_dbzyfix_click.log
```

### 8. 最终通过 logcat 定位 IJK 配置越界

现场日志最终显示：

```text
java.lang.IndexOutOfBoundsException: Invalid index 0, size is 0
at com.github.tvbox.osc.api.ApiConfig.getIJKCodec(ApiConfig.java:841)
at com.github.tvbox.osc.util.PlayerHelper.updateCfg(PlayerHelper.java:45)
```

据此确认播放失败首先卡在播放器初始化配置阶段，而不是 DBZY 接口、header、防盗链或 Exo m3u8 加载阶段。

### 9. 清理临时日志，只保留正式修复

定位完成后删除 `DBZYFIX` 临时日志，只保留：

- `ApiConfig.getIJKCodec()` 空列表兜底；
- `/share/` 页面直链解析；
- m3u8 相对路径转绝对路径；
- Gradle 编译加速配置；
- 文档记录。

## 验证

按项目文档 `docs/android-v7a-build-sign-adb.md` 使用 JDK 17 编译：

```bash
JAVA_HOME='C:/Users/xuan/.jdks/ms-17.0.15' PATH='C:/Users/xuan/.jdks/ms-17.0.15/bin':$PATH ./gradlew assembleArmeabiGenericNormalRelease
```

结果：

```text
BUILD SUCCESSFUL in 15m 59s
```

随后完成 zipalign、debug 签名和签名校验。

签名校验结果：

```text
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
```

产物路径：

```text
app/build/outputs/apk/armeabiGenericNormal/release/TVBox_release-armeabi-generic-java-signed.apk
```

## 注意事项

- 本次修复没有更改 DBZY 源配置；
- 没有修改 CMS JSON 详情解析逻辑；
- 没有改变 API / 数据库 / 配置格式；
- 只修复本地 m3u8 代理播放时相对路径被错误解析到 `127.0.0.1` 的问题。
