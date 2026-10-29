# 《基于Java的智慧宿舍管理系统设计》本科毕业设计工程

## 一、 项目架构概览（一后端 + 一小程序 + 双前端）

| 模块目录 | 工程职责 | 核心技术栈 | 运行端口 |
| :--- | :--- | :--- | :--- |
| [`sql/smart_dormitory.sql`](./sql/smart_dormitory.sql) | MySQL 8.0 数据库脚本 | 宿舍业务表 + 仿真测试数据 | `3306` |
| [`backend/`](./backend) | Java 后端核心服务 | Spring Boot 3.2 + MyBatis-Plus + JDK 17 | `7000` |
| [`frontend-admin/`](./frontend-admin) | PC Web 综合管理端 | Vue 3 + Vite + Element Plus + ECharts 5 | `5173` |
| [`frontend-student/`](./frontend-student) | 学生端 **Vite H5**（仅小程序形态 UI） | Vue 3（**非**微信小程序 / **非** Uni-app） | `5174` |

---

## 二、 核心“智慧”创新与全链路闭环特性

### 1. 基于六维生活习惯画像的智能分寝与调宿推荐引擎（[`SmartAllocationService.java`](./backend/src/main/java/com/dormitory/service/SmartAllocationService.java)）
- **六维特征向量**：采集作息时间、睡眠浅度、空调偏好、卫生标准、寝室氛围、异味敏感度 6 个维度。
- **自定义权重沙盘与同班聚拢约束**：支持在线调节六维权重占比、切换“纯习惯优先 (`HABIT_FIRST`)”与“同班级优先聚拢 + 习惯聚类 (`CLASS_MIX`)”策略，并与“传统按学号分寝”进行契合度对比实验及六维雷达图可视化；演示模式下支持重置 2026 级演示新生；此操作会释放该年级床位，仅应在隔离演示库使用。
- **智能调宿推荐与退宿闭环**：为拟调宿学生自动计算全楼空余床位寝室的**预期契合度 (`predictedHarmony`)** 并按 Top 3 推荐；审批通过或手动分床后自动重算原寝室与目标寝室的和谐度评分。

### 2. 智能报修优先级识别与最小负载派单引擎（[`SmartRepairService.java`](./backend/src/main/java/com/dormitory/service/SmartRepairService.java)）
- 自动扫描报修文本中的高危词库（`跳闸/短路/火花/爆管/漏水`），自动提权为 `HIGH（高危紧急）` 置顶，并按工种匹配当前待办工单数最少的维修师傅。
- 支持学生端结单后进行 **1~5 星维修服务回访评价**。

### 3. 水电阶梯计价核算、账户余额联动与双通道支付网关（[`SmartUtilityService.java`](./backend/src/main/java/com/dormitory/service/SmartUtilityService.java)）
- 自动扣除基础免额并按第一、第二阶梯单价核算水电费及人均分摊金额；超出楼栋均值 1.5 倍自动触发“违规大功率电器或漏水排查”预警。
- **电费/水费双账户充值与缴费联动抵扣**：账单缴纳自动优先抵扣寝室预存水电余额，余额不足时拦截并提示充值。
- **支付通道**：学生 H5 支持支付宝沙箱收银台和验签回调；演示模拟通道须显式开启。沙箱不可用时不会自动模拟到账。微信小程序不支持支付宝支付。

### 4. 多维量化“文明寝室”动态评比、申诉核销与激励发放（[`SmartEvaluationService.java`](./backend/src/main/java/com/dormitory/service/SmartEvaluationService.java)）
- 按 `50% 卫生均分 + 30% 纪律晚归表现 + 20% 水电节能表现` 自动核算各寝室星级与文明示范称号。
- **违纪/晚归在线申诉与扣分回滚**：学生可针对晚归或违纪录入发起申诉（如实验室科研、校医院急诊），宿管审核通过后自动将状态置为 `2-申诉撤销` 并实时恢复文明寝室纪律得分。
- **五星文明寝室电费补贴一键发放**：支持为当期 `5★ 文明标杆寝室` 一键派发 ¥30（约合 50 度电）电费奖励并自动发布全校表彰红榜公告。

### 5. 23:00 夜间门禁智能稽核、访客电子绿码与 RBAC 差异化工作台
- **晚归报备预览**：列出报备与待人工核实人员，不代表真实门禁或在寝状态，也不会自动生成违纪记录。
- **访客动态电子通行绿码**：学生端登记亲友来访后自动生成带 `VP-xxxxxx` 核验码的动态 SVG 绿码凭证（支持全屏亮码），管理端实时监测在楼时长与超时滞留预警。
- **多角色差异化工作台**：针对后勤主任 (`ADMIN`)、楼栋宿管 (`DORM_MANAGER`)、维修工程师 (`REPAIRMAN`)、辅导员只读 (`COUNSELOR`) 动态裁剪导航菜单与专属工作台。服务端已限制角色入口，并已对学生写接口做会话身份绑定、对宿管按 `manager_id` 做楼栋数据范围过滤；仍仅供本地演示，请勿公开部署真实数据。
- **报修 SLA**：创建工单时按优先级写入截止时间（高危 4h / 普通 24h / 低优 48h），列表展示「已超时」徽章。
- **水电缴费通道**：学生 H5 仅展示配置中可用的演示模拟或支付宝沙箱；小程序仅提供演示模拟。未启用的通道不会执行。
- **业务处理与追踪**：调宿审批核验并锁定床位，退宿需记录钥匙、设施和清理交接；学生可提交入住登记、假期留宿申请并查看审核结果。水电模拟操作支持请求重试去重与流水查询，能耗异常可记录排查状态并关联唯一报修工单。
- **报修提醒与方案说明**：工单临近截止时向维修人员发送站内提醒，超时后通知维修人员及宿管；分寝预览展示组内习惯差异和未分配原因，支持导出报告及查看人工床位变更记录。

---

### 演示与支付配置

演示重置和模拟水电核销默认关闭。仅在独立演示数据库中，启动后端前显式设置环境变量 `DEMO_MODE=true`；模拟充值不会产生真实支付。支付宝沙箱默认关闭，需显式设置 `ALIPAY_ENABLED=true`、应用 ID、私钥、公钥和可访问的回调地址。沙箱付款以验签回调到账为准；历史未记录订单金额的待处理订单不能自动核销，应人工核对。曾出现在仓库历史中的密钥如仍有效，请在支付宝侧撤销并轮换。正式环境尚未接入真实门禁、微信支付或生产支付对账，不应据此用于真实资金或真实考勤。

## 三、 快速启动步骤

### 1. 导入数据库
1. 确保本地 MySQL 8.0 已启动，执行 [`sql/smart_dormitory.sql`](./sql/smart_dormitory.sql)（会自动创建 `smart_dormitory` 库及全部初始数据）。
2. 检查 [`backend/src/main/resources/application.yml`](./backend/src/main/resources/application.yml) 中的数据库账号密码（默认为 `root` / `123456`）。

已有数据的数据库不要重新导入初始化脚本：该脚本包含 `DROP DATABASE`。请先备份，再根据 [`sql/MIGRATION.md`](./sql/MIGRATION.md) 更新结构；启动新版后端前必须执行 [`sql/20261029-auth-session.sql`](./sql/20261029-auth-session.sql) 创建会话表。新增的 SLA、能耗异常、住宿申请与退宿交接字段也须先完成迁移。

### 2. 启动全部服务
- Windows：双击 [`start-all.bat`](./start-all.bat)（相对路径，后端端口 **7000**）
- macOS / Linux：`chmod +x start-all.sh && ./start-all.sh`
- 前端代理目标参考 [`frontend-admin/.env.example`](./frontend-admin/.env.example) / [`frontend-student/.env.example`](./frontend-student/.env.example)
- 数据库账号可用环境变量 `DB_USER` / `DB_PWD`（见 [`backend/.env.example`](./backend/.env.example)）；种子密码说明见 [`sql/MIGRATION.md`](./sql/MIGRATION.md)

或分别在终端执行：

```bash
# 1. 启动后端 (7000)，并自动配置小程序局域网地址
# Windows: start-backend.bat
bash start-backend.sh

# 2. 启动 Web 管理端 (5173)
cd frontend-admin && npm install && npm run dev

# 3. 启动学生端 Vite H5 (5174)
cd frontend-student && npm install && npm run dev
```

本地后端启动时（包括 IntelliJ IDEA 和 Maven 直接启动）会调用 Node.js 脚本，将当前网卡的 IPv4 和实际监听端口写入小程序 `utils/config.js`。手机和电脑需处于可互通的同一局域网；多网卡选错时，可在启动前设置 `SMART_DORM_IP` 为本机网卡地址（Windows PowerShell：`$env:SMART_DORM_IP="192.168.1.100"`；macOS/Linux：`export SMART_DORM_IP=192.168.1.100`）。后端运行期间切换网络后，需运行 `node scripts/configure-miniprogram.js` 或重启后端，再重新编译并启动真机调试。自动配置需要 Node.js；若脚本失败，后端仍会启动并打印提示。

---

## 四、 演示测试账号一览（密码统一为 `123456`）

- **Web 管理端 (`http://localhost:5173`)**：
  - 系统管理员（后勤主任）：`admin` / `123456`
  - 楼栋宿管员：`dm01` / `123456`
  - 维修工程师（电工）：`worker01` / `123456`
  - 辅导员（只读）：`counselor01` / `123456`
- **学生端 Vite H5 (`http://localhost:5174`)**：浏览器打开即可，**不是**微信小程序，勿用微信开发者工具打开。学号如 `20230101`（陈嘉豪）、`20230105`（刘子墨）、`20260101`（陆景行）或 `20230201`（苏婉清）。

登录会话的令牌哈希保存在数据库中，有效期 8 小时，后端重启后会话仍有效；升级前的内存会话需要重新登录。旧的明文或 MD5 密码在成功登录时升级为 BCrypt。演示库种子密码已存为 BCrypt（登录明文仍为 `123456`，见 `sql/MIGRATION.md`）。请勿用于真实数据或公开部署。

## 五、 五分钟答辩演示剧本

1. **admin** 登录管理端 → 工作台「演示剧本」或智能分寝页：对比策略并确认分寝；需要时可一键「重置 2026 级演示新生」（仅 ADMIN）。
2. **学生 H5**（如 `20260101`）：习惯问卷 → **报修**（标题含「跳闸」）→ **水电**查看余额 → **宿务**预约访客绿码 / 晚归报备。
3. **worker01**：报修派单接单结单；学生端评价闭环（待评价强提醒）。
4. **dm01**：文明宿务卫生打分、申诉材料审核、五星补贴；门禁栏选择楼栋后预览报备情况。
5. **admin**：房态「楼栋宿管绑定」；手动分床时故意选错性别可演示拦截。

学生端底部导航：**首页 / 习惯 / 报修 / 水电 / 宿务**（Vite H5，小程序形态 UI；非 Uni-app、非微信小程序）。

## 六、 持续集成
推送或 PR 时由 [`.github/workflows/verify.yml`](./.github/workflows/verify.yml) 运行：
- 后端：`mvn -q test`（JDK 17）
- MySQL：使用独立测试库检查并发调宿和缴费重试
- 学生端和管理端：Node 测试及生产构建

---

## 七、 支付宝沙箱支付与 0 成本演示指南（无需购买域名与云服务器）

系统采用阿里官方最新轻量级 SDK **`alipay-easysdk`**，完全适配 Spring Boot 3 与 JDK 17。

### 1. 0 成本环境说明
- **无需购买域名**：支付宝沙箱联调支持局域网 IP / 本地 `localhost`；同步回跳 (`returnUrl`) 走前端本地地址，不需要任何域名备案。
- **无需购买服务器**：所有服务直接在本机（笔记本或台式机）运行即可。
- **异步回调 (`notifyUrl`)**：如需演示支付宝公网回调通知入库，可使用免费内网穿透工具：
  
  ```bash
  # 下载免费版 cpolar (或 natapp)，一条命令穿透本地后端端口 7000
  cpolar http 7000
  # 将生成的公网 https 地址作为 ALIPAY_NOTIFY_URL 环境变量传给后端
  ```

### 2. 启用支付宝沙箱

1. 访问 [支付宝开放平台·沙箱应用](https://open.alipay.com/develop/sandbox/app) 获取 **AppID**、**应用私钥** 与 **支付宝公钥**。
2. 在启动后端的环境中设置以下变量（`backend/.env.example` 只是示例文件，不会被启动脚本自动加载）：
   ```properties
   ALIPAY_ENABLED=true
   ALIPAY_APP_ID=90210001xxxxxxxx
   ALIPAY_PRIVATE_KEY=MIIEvgIBADAN...
   ALIPAY_PUBLIC_KEY=MIIBIjAN...
   ALIPAY_RETURN_URL=http://localhost:5174
   ```
3. 确保异步回调地址从支付宝可访问，启动项目，进入学生端（`http://localhost:5174`）「水电缴费」→「水电充值」选择「🚀 支付宝沙箱支付」，点击后即可自动拉起支付宝沙箱网页收银台，使用沙箱买家账号（或沙箱钱包 APP）完成充值演示！
