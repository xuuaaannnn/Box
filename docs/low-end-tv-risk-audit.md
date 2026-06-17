# 低配置电视资源风险代码排查

## 范围

本记录排查当前项目中对低配置电视（低内存、低存储、弱 CPU，例如 1GB/512MB 内存设备）可能不友好的代码点。

结论不是说这些代码一定会出问题，而是这些位置在低端设备上更容易导致：

- 启动或播放卡顿；
- 播放器切换慢；
- 存储被缓存占满；
- WebView / XWalk 占用过高；
- OOM 或后台被系统杀进程；
- 搜索 / 解析阶段线程过多导致 CPU 抢占。

## 高风险项

### 1. ExoPlayer 视频缓存上限 512MB

文件：`app/src/main/java/xyz/doikki/videoplayer/exo/ExoMediaSourceHelper.java`

位置：`newCache()`

```java
return new SimpleCache(
        new File(FileUtils.getExternalCachePath(), "exo-video-cache"),
        new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),
        new StandaloneDatabaseProvider(mAppContext));
```

风险：

- 512MB 对低端电视盒子来说偏大；
- 如果外部缓存目录在小容量内部存储上，可能快速挤占应用缓存空间；
- SimpleCache 会维护索引，缓存越大，磁盘 I/O 和初始化成本也越高。

建议：

- 默认降到 64MB 或 128MB；
- 根据设备内存 / 存储动态决定；
- 增加设置项：关闭 / 64MB / 128MB / 512MB；
- 对低端模式默认关闭 Exo 磁盘缓存。

优先级：高。

---

### 2. IJK 本地播放缓存上限 60MB

文件：`app/src/main/java/com/github/tvbox/osc/player/IjkmPlayer.java`

位置：`setDataSource(...)`

```java
if (Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)) {
    ...
    mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_max_capacity", 60 * 1024 * 1024);
    path = "ijkio:cache:ffio:" + path;
}
```

风险：

- 虽然默认看起来由 `IJK_CACHE_PLAY` 控制，但开启后每路播放可使用较大的本地缓存；
- 和 Exo 512MB 缓存、WebView 缓存同时存在时，低存储设备压力明显；
- 文件缓存路径在 `ijkcaches/` 下，需要关注清理策略。

建议：

- 低端模式下强制关闭；
- 开启时默认改为 16MB 或 32MB；
- 增加缓存目录大小统计与主动清理。

优先级：高。

---

### 3. WebView / XWalk 嗅探解析常驻成本高

文件：

- `app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java`
- `app/src/main/java/com/github/tvbox/osc/ui/activity/PlayActivity.java`

典型位置：

```java
loadWebView(pb.getUrl() + webUrl);
```

以及：

```java
private XWalkView mXwalkWebView;
private WebView mSysWebView;
...
mSysWebView = new MyWebView(mContext);
mXwalkWebView = new MyXWalkView(mContext);
```

风险：

- WebView 本身内存占用较大；
- XWalk runtime 更重，对低端电视尤其不友好；
- 嗅探解析期间还会加载网页 JS、图片、广告脚本等；
- 当前 `clearCache(true)` 多处被注释，缓存和内存压力可能延续到后续播放。

建议：

- 低端模式默认禁用 XWalk，仅使用系统 WebView；
- 优先使用 JSON 解析 / 直链，最后才 WebView 嗅探；
- 嗅探完成后更积极地 `destroy` WebView；
- 增加“禁用网页嗅探解析”设置；
- WebView 设置中关闭图片加载、DOM storage 等非必要能力，视兼容性决定。

优先级：高。

---

### 4. 多处固定 5 线程并发搜索 / 解析

文件与位置：

- `app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java`

```java
searchExecutorService = Executors.newFixedThreadPool(5);
```

- `app/src/main/java/com/github/tvbox/osc/ui/activity/FastSearchActivity.java`

```java
searchExecutorService = Executors.newFixedThreadPool(5);
```

- `app/src/main/java/com/github/tvbox/osc/ui/activity/DetailActivity.java`

```java
searchExecutorService = Executors.newFixedThreadPool(5);
```

- `app/src/main/java/com/github/tvbox/osc/util/parser/JsonParallel.java`

```java
executorService = Executors.newFixedThreadPool(5);
```

风险：

- 低端电视 CPU 核心少，5 个网络 / 解析线程容易抢占 UI 和播放器线程；
- 同时搜索多个源时，线程池、OkHttp 回调、JSON 解析、图片加载可能叠加；
- 多处固定线程池没有统一全局限流。

建议：

- 低端模式改为 2 线程；
- 搜索和解析共用一个受控全局线程池；
- 根据设备核心数动态计算：`max(2, min(4, availableProcessors / 2))`；
- JSON 聚合解析可用“先快后慢”的分批策略，而不是一次 5 路并发。

优先级：高。

---

## 中风险项

### 5. 播放时 m3u8 整体读入内存并 split / purify

文件：`app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java`

位置：`playUrl(...)`

```java
return response.body().string();
...
String content = response.body();
String[] lines = content.split("\n", 10);
RemoteServer.m3u8Content = M3U8.purify(baseUrl, content);
```

相关文件：`app/src/main/java/com/github/tvbox/osc/util/M3U8.java`

```java
String[] lines = m3u8content.split(linesplit);
```

风险：

- 大型 m3u8 会被完整读入字符串；
- `split` 会复制出大量字符串对象；
- `purify` 又构造新的字符串；
- 低内存设备在播放长视频 / 超长直播回放时有 OOM 风险。

建议：

- 对 m3u8 内容大小设置上限；
- 小文件才执行净化，大文件直接交给播放器；
- 使用流式扫描替代全量 `split`；
- 低端模式默认关闭 `VIDEO_PURIFY`。

优先级：中高。

---

### 6. 大量接口响应使用 `response.body().string()` 全量读入

典型文件：

- `app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java`
- `app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java`
- `app/src/main/java/com/github/tvbox/osc/util/parser/JsonParallel.java`
- `app/src/main/java/com/github/tvbox/osc/ui/activity/SearchActivity.java`
- `app/src/main/java/com/github/tvbox/osc/ui/activity/FastSearchActivity.java`
- `app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java`

示例：

```java
return response.body().string();
```

风险：

- CMS 列表、搜索结果、配置文件、解析返回均会一次性进入内存；
- 大配置 / 大搜索结果可能造成瞬时内存峰值；
- 后续 Gson / JSONObject 解析还会再次创建对象。

建议：

- 配置文件、CMS 列表加响应大小上限；
- 搜索分页限制更严格；
- 大 JSON 尽量流式解析；
- 对解析接口返回加最大长度保护。

优先级：中。

---

### 7. `extendCache` 静态缓存无上限

文件：`app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java`

位置：

```java
private static final ConcurrentHashMap<String, String> extendCache = new ConcurrentHashMap<>();
...
extendCache.putIfAbsent(key, result);
```

风险：

- 缓存的是字符串内容，可能是较大的扩展配置；
- 无容量上限、无过期策略；
- App 长时间运行、切换多个源后会持续增长。

建议：

- 改为 LRU，限制 3-5 条；
- 限制单条大小；
- 切换配置时清理。

优先级：中。

---

### 8. 预览播放时通过 Java 序列化深拷贝 `VodInfo`

文件：`app/src/main/java/com/github/tvbox/osc/ui/activity/DetailActivity.java`

位置：`jumpToPlay()`

```java
ByteArrayOutputStream bos = new ByteArrayOutputStream();
ObjectOutputStream oos = new ObjectOutputStream(bos);
oos.writeObject(vodInfo);
...
ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
previewVodInfo = (VodInfo) ois.readObject();
```

风险：

- `VodInfo` 包含剧集列表、播放线路、描述等数据；
- 序列化会产生额外 byte[] 拷贝和对象重建；
- 剧集很多时会造成明显瞬时内存和 CPU 开销。

建议：

- 使用已有 `VodInfo.clone()` 的 Gson 方案也未必最优；
- 更推荐手写轻量 copy，只复制播放必要字段；
- 或预览和详情共用不可变列表，仅复制播放状态。

优先级：中。

---

### 9. 全局壁纸 Bitmap 常驻静态变量

文件：`app/src/main/java/com/github/tvbox/osc/base/BaseActivity.java`

位置：`changeWallpaper(...)`

```java
protected static BitmapDrawable globalWp = null;
...
globalWp = new BitmapDrawable(BitmapFactory.decodeFile(wp.getAbsolutePath(), opts));
```

风险：

- 背景图常驻静态变量，生命周期接近进程；
- 即使采样到 1080x720，ARGB_8888 也可能占用约 3MB；
- 对 512MB 或更低可用内存设备，常驻大图会增加 OOM 风险。

建议：

- 使用 RGB_565；
- 低端模式禁用自定义壁纸；
- 使用弱引用或按 Activity 生命周期释放；
- 限制壁纸文件尺寸。

优先级：中。

---

### 10. Room CacheManager 使用 Java 序列化存储任意对象

文件：`app/src/main/java/com/github/tvbox/osc/cache/CacheManager.java`

位置：

```java
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ObjectOutputStream oos = new ObjectOutputStream(baos);
oos.writeObject(body);
return baos.toByteArray();
```

以及：

```java
return toObject(cache.data);
```

风险：

- 保存和读取时均会产生完整 byte[]；
- 数据库 BLOB 体积不可控；
- 反序列化成本高，不适合频繁调用；
- 当前用于播放进度 / 字幕路径等小对象还可以，但如果未来写入大对象会很危险。

建议：

- 限定只存小型标量：long/string/int；
- 拆成明确 DAO 字段，不用 Java 序列化；
- 增加单条缓存大小保护。

优先级：中。

---

### 11. `RawDataSourceProvider` 会把媒体完整读入内存

文件：`app/src/main/java/xyz/doikki/videoplayer/ijk/RawDataSourceProvider.java`

位置：

```java
private byte[] mMediaBytes;
...
mMediaBytes = readBytes(inputStream);
...
return byteBuffer.toByteArray();
```

风险：

- 如果传入的是较大本地媒体，会一次性进入内存；
- 低内存设备非常容易 OOM。

建议：

- 仅允许小型 raw 资源使用；
- 对长度加上限；
- 改为按需 seek/read，不整文件读入。

优先级：中。

---

## 低到中风险项

### 12. DoH 缓存 10MB

文件：`app/src/main/java/com/github/tvbox/osc/util/OkGoHelper.java`

位置：

```java
builder.cache(new Cache(new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache"), 10 * 1024 * 1024));
```

风险：

- 10MB 本身不大，但对极小存储设备仍是常驻缓存；
- 与播放器缓存、图片缓存、WebView 缓存叠加后需要统一预算。

建议：

- 低端模式降到 1-2MB；
- DoH 关闭时不初始化该缓存；
- 定期清理。

优先级：低中。

---

### 13. `PlayActivity` 与 `PlayFragment` 播放逻辑重复

文件：

- `app/src/main/java/com/github/tvbox/osc/ui/activity/PlayActivity.java`
- `app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java`

风险：

- 两套播放、解析、WebView、m3u8 净化逻辑并存；
- 修复或优化一处后，另一处可能遗漏；
- 低端优化策略不容易统一。

建议：

- 抽出公共播放服务 / 播放策略类；
- 低端模式开关只在一处生效；
- 删除不再使用的旧路径，或明确标记为兼容路径。

优先级：低中。

---

## 建议的低端模式开关

可以考虑增加一个统一配置，例如：

```text
LOW_END_MODE = true / false
```

开启后默认策略：

| 项目 | 普通模式 | 低端模式 |
|---|---:|---:|
| Exo 磁盘缓存 | 512MB | 0/64MB |
| IJK 文件缓存 | 用户设置 | 默认关闭 / 16MB |
| 搜索并发 | 5 | 2 |
| JSON 聚合解析并发 | 5 | 2 |
| WebView 嗅探 | 可用 | 最后兜底 / 可禁用 |
| XWalk | 可用 | 默认禁用 |
| m3u8 去广告 | 默认开启 | 默认关闭或限制大小 |
| 自定义壁纸 | 可用 | 默认禁用或 RGB_565 |
| DoH 缓存 | 10MB | 1-2MB |

## 优先整改顺序

1. 降低 / 可配置 Exo 512MB 缓存。
2. 统一搜索和解析并发上限，低端模式改为 2。
3. WebView / XWalk 嗅探加低端模式限制。
4. m3u8 净化加大小上限，避免大字符串和 split 峰值。
5. `extendCache` 改 LRU。
6. `VodInfo` 预览深拷贝改轻量 copy。
7. 壁纸 Bitmap 改 RGB_565 或低端禁用。
8. `CacheManager` 限定存储小对象。
