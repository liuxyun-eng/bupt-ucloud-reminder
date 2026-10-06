# 云邮提醒 0.1.4 · Android 测试版

适用于 Android 8.0 及以上。这是为北京邮电大学教学云制作的非官方辅助应用，包含可安装 APK 和 Android Studio 源码项目。

[下载 0.1.4 测试版 APK](https://github.com/liuxyun-eng/bupt-ucloud-reminder/releases/download/v0.1.4/yunyou-reminder-0.1.4-test.apk) · [查看发布说明和源码压缩包](https://github.com/liuxyun-eng/bupt-ucloud-reminder/releases/tag/v0.1.4)

## 开始使用

1. 把 `yunyou-reminder-0.1.4-test.apk` 传到安卓手机，点击安装。如果系统提示，允许这次用于安装的文件管理器安装应用。
2. 打开应用，点击「连接学校账号」。App 会自动清除本应用内的旧网页登录缓存并打开学校教学云主入口，再进入统一认证网页。自行完成登录，使用学生身份。
3. 返回教学云主入口后，等待顶部「连接提醒」亮起并点击。无需先进入课程页面。允许系统通知，等待首次同步。
4. 在「待办」查看作业、测验等待办及截止时间；在「通知」查看最近的平台通知。
5. 到「设置」发送一条测试提醒。如果看不到通知，检查系统通知权限、通知类别，以及手机厂商的后台活动设置。

无需把密码发给开发者。首次同步只建立通知记录，不推送历史通知。后续发现新通知会提醒；一次发现多条时合并为一条系统通知。

## 0.1.4 身份数据兼容与具体诊断

用户反馈 0.1.3 打开作业时提示「学校身份或权限数据异常」，诊断只有「学生页面权限初始化 / 未完成，详情页尚未打开」。这表明失败发生在进入详情页前，但旧版没有保留具体失败项。

本版修正已核对到的兼容差异：

- 学校网页使用 `roleAliase + ':' + dept` 生成身份；部门值为 `null` 或缺省时，身份分别包含 `null` 或 `undefined`。本版保留该行为和角色数据中的空值，避免把这些正常身份误判为不匹配。
- 门户身份、权限和账号查询按官方主入口的请求头执行，不额外携带课程页的身份请求头；通知与待办读取保持原有身份请求头。
- 门户响应使用成功的数据结构时，兼容缺省的可选 `code` 字段；明确失败的响应仍被拒绝。角色编号缓存过期时，仅在当前身份唯一匹配学校返回的角色时更新。
- 弹窗与「登录诊断」显示具体步骤和错误编号，区分身份未匹配、数据格式异常、账号不一致、权限缺失、接口或网络错误、Cookies 写入失败和网页存储失败。诊断不复制接口正文或异常原文。
- 网页存储写入后读取核对，只有确认完成后才进入学校详情页；学校返回的真实作业权限检查继续保留。

**升级使用：**直接安装 `yunyou-reminder-0.1.4-test.apk` 覆盖旧版，无需卸载或清空数据。在设置页确认版本为 0.1.4，然后打开原来失败的同一个作业。若仍失败，请复制新的「登录诊断」文字反馈；可以使用底部「在手机浏览器中打开」继续访问作业。

已通过 49 项回归测试、学校实际课程路由与网页存储的 6 个复现场景，以及 Android Lint 检查（无错误）。本次未在用户 Android 16 / WebView 151 手机上完成带账号的页面联调；上述差异是否覆盖该手机的全部原因，仍需安装后验证。

## 0.1.3 作业页面权限初始化

针对 App 内打开作业显示「没有访问权限」、同一手机浏览器中正常的情况，修正打开学校页面前遗漏权限初始化的问题。

学校课程页的路由守卫会读取 `user-role-permission`，作业详情需要其中存在 `stuAssignmentInfo`。教学云主入口登录时，角色权限请求是异步启动的；仅有登录 Cookies 并不能说明这份列表已加载。本版打开作业前直接读取学校的当前身份、该身份的权限列表及必要账号信息，完成网页存储和 Cookies 初始化后才进入详情页。

- 选择与当前加密登录状态一致的学生身份和部门，保存实际角色编号；兼容 0.1.2 没有保存角色编号的登录状态。身份不唯一或账号信息不一致时提示重新连接。
- 权限来自学校 `role-grant` 接口；作业页需要的权限若未返回，显示具体提示。学校页面仍运行原有访问检查。
- 网页存储使用学校前端的 JSON 编码格式；先完成 Cookies 写入，再打开学校作业页。后台刷新后的登录状态也会传入网页。
- 初始化在同一学校 HTTPS 来源的空白准备页中进行；准备页不加载远程脚本、不包含登录表单。作业内容随后从学校真实页面加载。
- 原有通知和待办同步继续保留。只读取初始化数据，不会自动提交作业、修改课程或标记通知已读。
- 同时增加「在手机浏览器中打开」。它直接打开原作业链接，浏览器使用自己已有的学校登录，不转交 App 的 ticket 或 token。
- 403 权限不足与 401 登录过期分开处理；页面初始化和后台同步并发时，令牌刷新会复用已经完成的刷新结果。

**升级使用：**直接安装 `云邮提醒-0.1.3-test.apk` 覆盖旧版。在设置中确认 0.1.3，然后重新点击待办中的「打开作业」。已有登录通常可以继续使用；如出现登录过期或身份提示，再重新连接学校账号。如果 App 内仍打不开，可点击底部「在手机浏览器中打开」，并反馈页面错误或「登录诊断」文字。

已完成 40 项回归测试，并运行学校公开课程脚本中的实际权限守卫，验证缺少权限列表时被拒绝、学校返回作业权限后可通过、真实权限缺失时仍拒绝，以及其他来源的页面无法写入这份权限存储。尚未在用户安卓手机上完成带账号的作业页面联调，仍需安装后验证真实作业是否可打开。

## 0.1.2 登录调整与升级

0.1.1 在用户手机上仍出现「ticket获取用户账号失败」。用户确认同一手机的普通浏览器能够登录。本版改用学校教学云主入口的认证流程。

- 当前主入口脚本使用 `service=https://ucloud.bupt.edu.cn` 和门户 OAuth 客户端；课程页面脚本使用另一组客户端和 `service=http://ucloud.bupt.edu.cn/uclass`。本版从学校主入口发起认证，不再以课程页面的旧入口发起登录。
- 保留门户 callback 的完整 ticket，补齐主入口脚本解析所需的 URL 片段；遇到旧入口的回调时获取新的门户凭证，不把旧 ticket 交给另一客户端重用。
- 连接学生账号后，加密保存其认证来源。后台读取与刷新令牌都使用同一客户端；旧版已保存的课程客户端会保持原值，直到重新连接。
- 重新登录自动清除本应用 WebView 内的 Cookies 与网页存储，避免旧缓存影响升级后的认证。手机 Chrome 等浏览器中的登录不受影响。本机已缓存的待办和提醒继续保留。
- 持续等待网页完成异步登录，并同时检查学生角色、身份和必要的登录 Cookies。登录完成后即可在主入口点击「连接提醒」，不再只根据网页地址判断成功。
- 支持学校主入口「进入云邮」按钮打开课程页面。门户客户端对应的网页存储标记在打开课程、通知和作业页面时保持一致。
- 新增「登录诊断」按钮，可复制 Android / WebView 版本、当前登录步骤、错误编号或凭证接口 HTTP 状态码。诊断不包含密码、学号、Cookies、ticket、token 或完整地址。

直接安装 `云邮提醒-0.1.2-test.apk` 覆盖旧版即可，包名与签名不变。在设置页确认版本为 0.1.2，然后点击「登录 / 重新连接」。如果仍失败，点击「登录诊断」→「复制诊断」，把文字反馈给开发者。

此版本依据学校当前公开前端重新适配。已经确认 HTTPS 主入口的认证地址返回正常登录表单，但尚未在用户安卓手机中完成带账号的登录联调，不能保证仅凭上述差异就已经解决全部兼容问题。

## 已实现

- 直接请求学校平台的通知列表和学生待办接口。
- 通知按平台编号去重；识别同一分钟新增的多条通知；避免把翻页后出现的历史消息误报为新通知。
- 通知分页读取，覆盖两次检查之间超过 10 条新增通知的情况。单次最多读取 50 页；若超时或未读完，不推进记录，保留上次结果。
- 获取接口返回的完整待办列表，按截止时间排序，不限于网页首页显示的 6 条。
- 截止前 24 小时、2 小时，以及截止时提醒；首次同步时已进入提醒时间范围的待办会按当前最紧急阶段提醒。
- 完成后可在本机停止提醒，支持恢复。**「标记完成」只改变本机提醒状态，不会向学校提交作业。**完整同步后，平台已从待办中移除的项目也会从本机列表移除。
- 后台检查间隔可选 15、30、60 分钟；支持手动同步。缓存的截止提醒可以离线触发。
- 登录令牌自动刷新；登录失效时显示重新登录提示，保留缓存。
- 系统重启、更新时间和时区后重建提醒；所有学校时间按北京时间解释。
- 通知权限未允许时保留待发送的新通知，在允许后补发。
- 用 Android Keystore 和 AES-GCM 加密保存供后台使用的登录令牌。学校 WebView 的浏览器数据位于本应用私有空间。应用不读取密码，不把数据上传到开发者服务器，禁用云备份与设备迁移。

## 时间与状态的实际限制

应用没有学校服务端推送权限，采用周期检查。Android 的 JobScheduler 周期任务最短间隔为 15 分钟，执行时间受网络、省电和后台限制影响。因此无法保证新通知发布后立即提醒。

截止提醒使用不需要特殊精确闹钟权限的系统闹钟，也可能延迟。手机强行停止应用后，安卓可能暂停后台检查与闹钟，重新打开应用后恢复。

作业状态来自上次成功同步的待办列表。网络断开或登录过期时，缓存提醒仍会运行；已经在网页提交但尚未再次同步的作业，可能继续提醒。页面会显示上次同步时间。

接口根据学校公开前端核对，并非学校公开承诺的开发者接口。平台改变接口或字段后，需要更新适配代码。接口异常、不完整数据或登录失效时，程序保留上次结果，不把失败响应当成空待办。

## 验证情况

- 当前 0.1.4 APK 构建成功；49 项核心逻辑、登录、身份匹配、页面权限、存储格式与诊断回归测试通过；学校实际路由与存储的 6 个复现场景通过。
- Android Lint 检查通过，无错误；有同步保存偏好设置、固定依赖版本、网页 JavaScript 等提示。
- 已通过已有网页登录页面核对通知页内容和首页待办、截止时间，并检查公开前端中的接口路径、请求头和字段。
- **未在安卓实机或模拟器中完成学校登录和后台提醒联调，也未使用你的浏览器登录令牌进行接口请求。**安装后仍需验证学校登录能否在手机 WebView 中完成，以及你的手机对后台任务的限制。

建议首次安装后核对待办数量和截止时间，再用「发送一条测试提醒」验证通知权限。等下一条真实课程通知发布时，核对自动检查是否正常工作。

## 源码构建

用 Android Studio 打开本目录，安装 Android SDK Platform 36 与 Build Tools 35.0.0，使用 JDK 17 或 21。Gradle Wrapper 固定为 8.13，Android Gradle Plugin 为 8.13.0。

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

生成 APK：`app/build/outputs/apk/debug/app-debug.apk`。随附 APK 为调试签名测试包，不是应用商店发布包。正式分发时请使用你自己保管的发行签名密钥；更换签名后无法直接覆盖已有安装。

主要实现：

| 文件 | 作用 |
| --- | --- |
| `MainActivity.java` | 待办、通知、设置界面 |
| `SchoolActivity.java` | 学校网页登录、详情页初始化、浏览器打开 |
| `SchoolPageContext.java` | 当前学生角色、学校权限响应与网页存储格式 |
| `LoginNavigation.java` | 门户 CAS 回调、ticket 完整性与导航限制 |
| `SchoolAuth.java` | 门户与课程两种公开认证客户端 |
| `SessionCookies.java` | 学生角色、完整登录状态与旧 Cookie 冲突检查 |
| `LoginDiagnostics.java` | 不含账号或凭证的登录诊断 |
| `PageInitStep.java` / `PageInitFailure.java` | 详情页初始化步骤和固定错误编号 |
| `SessionVault.java` | 加密登录令牌、账号切换与清除 |
| `ApiClient.java` | 固定学校域名的 HTTPS 请求、令牌刷新 |
| `ApiParser.java` | 通知和待办响应解析 |
| `SyncRunner.java` | 通知分页读取、完整同步与账号版本检查 |
| `SyncJobService.java` | 系统周期后台检查 |
| `ReminderScheduler.java` | 截止闹钟、完成后取消、去重 |
| `RulesTest.java` | 通知增量、提醒阶段、时间与接口字段测试 |
| `LoginNavigationTest.java` | 门户入口、旧 ticket 隔离、回调完整性与跳转回归测试 |
| `LoginSessionTest.java` | 学生身份、Cookie 冲突、客户端与诊断隐私回归测试 |
| `SchoolPageContextTest.java` | 身份匹配、真实权限、网页存储编码与账号一致性回归测试 |
| `PageInitFailureTest.java` | 具体失败项与异常原文不进入诊断的回归测试 |

使用的学校接口：

- 通知：`POST https://apiucloud.bupt.edu.cn/ykt-basics/api/inform/news/list?newsCopyPersonId=…&current=…&size=10`
- 待办：`GET https://apiucloud.bupt.edu.cn/ykt-site/site/student/undone?userId=…`
- 令牌刷新：`POST https://apiucloud.bupt.edu.cn/ykt-basics/oauth/token`
- 当前角色：`GET https://apiucloud.bupt.edu.cn/ykt-basics/userroledomaindept/listByUserId`
- 角色权限：`GET https://apiucloud.bupt.edu.cn/ykt-basics/menu/role-grant?roleId=…`
- 当前账号：`GET https://apiucloud.bupt.edu.cn/ykt-basics/info`

程序只调用通知/待办列表、账号与权限查询、令牌刷新接口，不调用删除通知、标记通知已读、提交作业或修改课程的接口。打开学校网页后的操作由使用者自行进行。

## 依据

- [学校教学云主入口](https://ucloud.bupt.edu.cn/)
- [主入口公开认证脚本](https://ucloud.bupt.edu.cn/js/index.678a8c21.js)
- [主入口公开课程跳转脚本](https://ucloud.bupt.edu.cn/js/chunk-285fc936.b49d70b6.js)
- [作业页公开路由与权限脚本](https://ucloud.bupt.edu.cn/uclass/static/js/course.3b66a1e759c394024404.js)
- [核对时的公开前端脚本](https://ucloud.bupt.edu.cn/uclass/static/js/app.d43c9ed612d8b052819d.js)
- [Android 周期任务](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long))
- [Android 系统闹钟与省电限制](https://developer.android.com/develop/background-work/services/alarms)
- [Android 13 及以上的通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)

登录回调处理参考：[Android WebViewClient 导航规则](https://developer.android.com/reference/android/webkit/WebViewClient)、[CAS 协议](https://apereo.github.io/cas/development/protocol/CAS-Protocol-Specification.html)。

详情页存储初始化参考：[Android WebView.loadDataWithBaseURL](https://developer.android.com/reference/android/webkit/WebView#loadDataWithBaseURL(java.lang.String,%20java.lang.String,%20java.lang.String,%20java.lang.String,%20java.lang.String))。
