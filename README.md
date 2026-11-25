# DeepSeek 控制台 Android App

一个用于查看 DeepSeek 开放平台账户信息的 Android 客户端。通过官方 WebView 登录后，App 读取会话令牌调用开放平台的用量接口，本地展示余额、用量、Keys、账单等信息；支付与充值在官方页面完成。

## 功能

- **登录**：在官方页面完成手机号/验证码或微信扫码登录，App 自动读取 `userToken`（会话令牌用 Android Keystore 加密存储）
- **用量**：本月消费金额、API 请求次数、Token 统计（缓存命中/未命中、输出）、每日消费图表
- **Keys**：查看 API Key 列表（脱敏）、复制、删除
- **充值**：显示余额、常见档位、支付方式选择；跳转到官方充值页并自动选中金额与支付方式
- **账单**：按日明细，跳转到官网账单与发票页
- **账号**：账户信息

## 技术说明

- 原生 Android（Java）+ WebView， Gradle 构建，minSdk 24+
- API 请求只带 `Authorization: Bearer <userToken>`；接口为 DeepSeek 平台私有端点（`/api/v0/...`）
- 令牌有效期检测：接口返回 40002/40003 时自动清除本地令牌并跳转登录页
- 支付流程：拦截支付 scheme 与收银台域名，通过系统 Intent 唤起支付宝/微信 App

## 构建

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`（构建时生成）。

本仓库 `apk/` 目录附带了编译好的安装包 `deepseek-console-debug.apk`，可直接下载安装。

在 Android Studio 中打开目录即可运行；首次构建需在 `local.properties` 配置 `sdk.dir`（该文件已被 git 忽略）。

## 免责声明

本项目为个人学习与工具用途，非 DeepSeek 官方应用。使用时请遵守 DeepSeek 平台的服务条款。项目不存储任何支付信息，支付全部在官方页面/官方 App 内完成。
