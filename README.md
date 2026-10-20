# Peeler（削皮刀）

红果短剧 / 红果漫剧的 **LSPosed 中插广告拦截模块**，基于 libxposed API 102。

> **需要 root + LSPosed/Vector。** 本模块不提供免 root 方案。

> 仅用于个人学习与技术研究。绕过广告可能违反宿主用户协议，账号风险请自行评估。
> 本项目不修改服务端数据、不伪造 VIP 权益，只修改本地的广告配置。
> 模块不联网、不申请网络权限、不收集任何数据。

---

## 1. 功能

拦截**连续看剧、切下一集**时出现的中插广告：

- 竖屏中插：`short_series_ad`
- 横屏中插：`landscape_short_series_ad`

不处理：

- 首页推荐流 `recommend_video`
- 创作者广告 `creator_ad`
- 暂停广告 `series_pause_ad`
- 短剧 Banner `short_series_banner`
- 评论区广告 `series_comment_ad`

## 2. 兼容性

| 项目 | 说明 |
|---|---|
| 系统 | Android 10+（测试 Android 14 / arm64） |
| 框架 | LSPosed / Vector，libxposed API 102 |
| 红果短剧 | `com.phoenix.read` 7.3.9.32 |
| 红果漫剧 | `com.kylin.read` 7.4.0.32 |
| 模块 | 1.0（versionCode 1） |

其他宿主版本未验证。红果更新后，如果广告字段或 Gson 解析路径发生变化，模块可能失效。

## 3. 安装

1. 安装 APK；
2. 在 LSPosed/Vector 中启用「削皮刀」；
3. 作用域勾选 `com.phoenix.read`、`com.kylin.read`；
4. 强行停止红果短剧/漫剧，再重新打开。

模块没有桌面入口，装完在模块管理器里启用即可。

## 4. 原理

Gson 解析广告设置对象后，把三个开关置为 `false`：

| 字段 | 作用 |
|---|---|
| `shortVideoAdEnable` | 普通中插 |
| `landscapeInsertAdEnable` | 横屏中插 |
| `enableMultiSeriesFlowAd` | 短剧信息流总开关 |

- 不依赖 DexKit，不需要 `libdexkit.so`；
- 不 hook 混淆类名，不修改服务端数据；
- 按字段存在性识别配置对象，不按 `SettingsModel` 等类名匹配；
- 两个 Gson 重载都处理，宿主换解析方式时多一层兜底。

## 5. 日志

正常启动日志：

```
build=peeler-1.0, pkg=com.kylin.read, installed=2 skipped=0, hooks=sdk:1
sdk field shortVideoAdEnable -> false
sdk field landscapeInsertAdEnable -> false
sdk field enableMultiSeriesFlowAd -> false
```

抓日志：

```powershell
adb shell am force-stop com.kylin.read
adb logcat -c
adb logcat -v time -s VectorContext
```

如果完全没有 `sdk field` / `sdk settings`，先确认模块已启用、作用域正确，并强停宿主后重开。

## 6. 已知限制

- **只处理中插**：推荐流、创作者、暂停、Banner、评论区广告不在范围内。
- **依赖 Gson 解析路径和字段名**：宿主大版本更新后可能失效。
- **需要 root**：LSPosed/Vector 本身需要 root；免 root 的 LSPatch 会改签名，触发红果 Metasec 反篡改，实测闪退。
- **只覆盖主进程**：子进程中的广告不在范围内。
- **合规风险**：绕过广告可能违反宿主用户协议，请自行评估。

## 7. 构建（可选）

需要 JDK 17 和 Android SDK，并使用你自己的 release 签名；签名文件不要提交到仓库。

```powershell
.\gradlew.bat clean assembleRelease --console=plain
```

产物：

```
app\build\outputs\apk\release\app-release.apk
```

版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`。

## 8. 源码与反馈

- 源码：https://github.com/lnzx/Peeler
- 问题反馈：https://github.com/lnzx/Peeler/issues

请附上红果版本号、系统版本和相关日志；不要包含账号等隐私信息。

## 9. 许可证

本项目使用 [MIT License](LICENSE)。

启动图标来自 Tabler Icons 的 `slice` 图标（MIT License），
详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
