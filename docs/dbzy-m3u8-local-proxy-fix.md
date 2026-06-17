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

## 根因

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

## 修复后的数据流

```text
DBZY 原始 m3u8
  -> PlayFragment 下载 m3u8
  -> M3U8.purify(baseUrl, content) 去广告
  -> M3U8.resolveAll(baseUrl, purifiedContent) 相对路径转绝对路径
  -> RemoteServer.m3u8Content
  -> 播放 http://127.0.0.1:<port>/m3u8
  -> 播放器请求远程绝对分片 URL
```

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
