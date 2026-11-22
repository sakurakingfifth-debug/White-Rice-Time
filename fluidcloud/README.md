# 流体云卡片（ColorOS）

本目录是按 OPPO 流体云模板规范写好的卡片源码，供**申请通过泛在卡片服务后**直接使用。

规范来源：

- 流体云卡片接入：https://open.oppomobile.com/documentation/page/info?id=12965
- 流体云模板：https://open.oppomobile.com/documentation/page/info?id=12658
- 流体云组件：https://open.oppomobile.com/documentation/page/info?id=12703

---

## 一、两条接入路径

| 路径 | 能力 | 前置条件 | 本应用 |
|---|---|---|---|
| **泛在服务卡片**（UPK + `.oml` + `SeedlingSupportSDK`） | 气泡、状态栏胶囊、大胶囊、展开面板、锁屏、AOD、外屏、手表、耳机播报 | 向 OPPO 申请泛在卡片服务 → 拿授权码 → UPK 经 OPPO 云端下发 → 接 SDK | 源码已备好（本目录），**未接入** |
| **Android 16 Live Updates** | ColorOS 16 上渲染为流体云胶囊 / 面板 | 无，标准通知即可 | **已接入**（`notify/BalanceNotifications.kt`） |

App 目前走第二条：`ProgressStyle` + `EXTRA_REQUEST_PROMOTED_ONGOING` + `setShortCriticalText`，
ColorOS 16 在系统侧把它显示成流体云。第一条需要 OPPO 侧授权与云端下发，个人应用无法自测，
因此只把卡片源码留在这里，等申请通过后直接复用。

---

## 二、卡片工程配置（文档原文要点）

`config.json`：

```jsonc
// 流体云卡片必须选 API-2.0
"platform-verison": 2000000,

// live=实时状态（余额监控属于这一类），immediate=即时提醒
"interactive": { "seedling-type": "live" },

// 使用 compact 胶囊容器时，入口必须配置 statusbar
"pages": ["pages/index"]
```

`card-config.json`：

```jsonc
"card": {
  // 胶囊（含气泡）| 大胶囊 | 展开面板
  "support": "notification_sm|notification_md|notification_lg"
}
```

> 字段以 Pantanal DevStudio 新建「流体云」工程时生成的模板为准；上表只列出本文档明确要求的项。

---

## 三、模板结构

`pages/index.oml` 使用**组合模板**（`category="modular"`，API 3.0.0+）：

| 区域 | level | 内容 | 对应 App 内字段 |
|---|---|---|---|
| 胶囊左侧 | `A*` + `B*` | 服务图标 + 余额 | `FluidCloud.capsuleText()`（≤5 字） |
| 胶囊右侧 | `B0` | 更新时间 | `Fmt.relative(fetchedAt)` |
| 面板顶部 | `D8` + `C10` | 同步图标 + 更新时间 | `Fmt.updated(fetchedAt)` |
| 面板核心 | `D1` + `B1*` + `C1` | 图标 + 总余额 + 构成 | `Fmt.money(total)` / `Fmt.money(granted)` / `Fmt.money(toppedUp)` |
| 面板底部 | `D13*` | 构成条 | `granted / total` 百分比 |
| 面板按钮 | `E9*` / `E10*` | 刷新 / 打开 | `NotificationActionReceiver` |

规范里几条硬约束（已在模板中遵守）：

- 胶囊文本**最多 5 个字符**，超长会被系统拉长后以「…」截断；
- 胶囊左右**各至少一个元素**，单侧不可出现相同元素；
- 胶囊背景固定黑色，**不可自定义**；
- 同一实时活动内，信息变化时**胶囊样式不允许变化**；
- 服务图标 `A1*` **必填**（AOD / 手表 / 气泡兜底），可以不显示；
- 模板内**不能用 CSS** 改布局和样式，只能绑定变量与事件；
- `card-template` 上的 `onclick` **只允许 deeplink**，不允许 message 事件。

---

## 四、素材清单

放到工程的 `assets/images/`，并在 `config.json` 的 `images` 里登记后用 `$r('images.xxx')` 引用：

| 名称 | 用途 | 说明 |
|---|---|---|
| `icon_balance` | `A*` / `D1` | 余额图标，透明背景，非等边图形 |
| `icon_sync` | `D8` | 同步状态图标 |
| `icon_gift` | `D12` | 构成条左侧图标（赠送） |
| `app_icon` | `A1*` | 应用图标，兜底入口用，建议方形 |

---

## 五、落地点

卡片点击走 deeplink：`puibalance://balance`。`MainActivity` 已注册对应 `intent-filter`，
点击胶囊或面板会直接打开应用余额页。

---

## 六、状态

- [x] 模板源码（`pages/index.oml`）
- [x] App 侧字段与文案规则（`FluidCloud.kt`）
- [x] deeplink 落地
- [ ] 泛在卡片服务授权（需向 OPPO 申请）
- [ ] UPK 打包与真机调试（Pantanal DevStudio）
- [ ] `SeedlingCardWidgetProvider` 数据下发

后三项依赖 OPPO 侧授权与云端下发，本仓库不包含。
