# APK 提取

一个安装包，同时支持 **手机** 和 **电视**。打开即可列出本机已安装应用，勾选后一键把 APK 提取到本地。

[English](#english) · 简体中文

## 截图

| 手机 · 列表 | 手机 · 多选 | 电视 · 遥控器 |
| --- | --- | --- |
| ![phone-main](docs/screenshots/phone-main.png) | ![phone-selected](docs/screenshots/phone-selected.png) | ![tv](docs/screenshots/tv-remote.png) |

## 功能

- 列出本机全部已安装应用（可搜索应用名 / 包名）
- 整条列表任意位置点击即可勾选 / 取消，支持多选
- 一键提取 APK 到本地，支持 split APK（会拆成 base + split 多个文件）
- **一个 APK，手机桌面和电视桌面都能打开**（`LAUNCHER` + `LEANBACK_LAUNCHER`）
- 浅色主题，底部圆角卡片操作栏，电视遥控焦点清晰

## 保存位置

提取出的文件保存在：

```text
Download/APK提取/
```

完整路径一般是：

```text
/storage/emulated/0/Download/APK提取/
```

文件名形如：`com.tencent.mm-v8.0.49.apk`

## 安装

从 [Releases](../../releases) 下载 `app-release.apk`（或仓库根目录的 `tiqu-release-vX.X.X.apk`），安装到手机或电视即可。

| 设备 | 方法 |
| --- | --- |
| 手机 | 传输 APK 后安装（允许未知来源） |
| 电视 / 盒子 | U 盘、局域网传输后用文件管理器安装；或 `adb install -r xxx.apk` |

> 手机端与电视端是同一个安装包，无需分别下载。

## 电视遥控器操作

| 按键 | 作用 |
| --- | --- |
| ↑ ↓ | 移动焦点 |
| OK / 确认 | 勾选 / 取消当前应用 |
| ← → | 有勾选时跳到「提取选中」 |
| 菜单键 | 全选 |
| 返回 | 先清勾选 → 再清搜索 → 退出 |
| F1 / 绿色键（部分遥控） | 直接开始提取 |

## 权限说明

| 权限 | 用途 | 时机 |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | 列出全部已安装应用 | 安装时声明（Android 11+ 必需） |
| `WRITE_EXTERNAL_STORAGE` | 写入公共下载目录 | **仅 Android 9 及以下** 运行时申请 |

Android 10+ 通过 MediaStore 写入下载目录，**不需要**存储权限。

## 从源码构建

**环境**：JDK 17+、Android SDK（compileSdk 36）

```powershell
# 调试包
.\gradlew.bat :app:assembleDebug

# 生产签名包（需要 keystore.properties + signing/*.jks）
.\gradlew.bat :app:assembleRelease
```

产物在 `app/build/outputs/apk/`。

### 生产签名（维护者）

1. 用 `keytool` 生成密钥库，放到 `signing/tiqu-release.jks`
2. 创建 `keystore.properties`：

```properties
storeFile=signing/tiqu-release.jks
storePassword=你的密码
keyAlias=tiqu
keyPassword=你的密码
```

3. **务必备份密钥库与密码**，并保持 `.gitignore` 忽略它们，不要提交到仓库。

## 工程结构

```text
app/src/main/java/com/tiqu/extractor/
  MainActivity.java      # 主界面、遥控按键、加载刷新
  AppListAdapter.java    # 列表与整条点击勾选
  ApkExtractor.java      # 扫描应用并复制 APK
  AppEntry.java          # 列表数据模型
index.html               # 界面预览（手机模拟器 / 键盘模拟遥控）
docs/screenshots/        # README 截图
```

## 界面预览（无需安装）

用浏览器打开 `index.html`，即可预览界面；键盘可模拟遥控器：

↑ ↓ 移动 · Enter 勾选 · ← → 跳到提取 · F1 全选 · Esc 清空

---

## English

**APK Extractor** — one APK for both phone and Android TV. Lists installed apps, multi-select, and export APK files to `Download/APK提取/`.

- Phone + TV launcher support in a single install
- Search, multi-select, split-APK export
- D-pad remote friendly UI
- Light theme, rounded action card

Build: `./gradlew :app:assembleRelease`

## License

[MIT](LICENSE)
