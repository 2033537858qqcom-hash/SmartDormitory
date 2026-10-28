# 《基于Java的智慧宿舍管理系统设计》本科毕业设计工程

## 一、 项目架构概览（一后端 + 双前端）

| 模块目录 | 工程职责 | 核心技术栈 | 运行端口 |
| :--- | :--- | :--- | :--- |
| [`sql/smart_dormitory.sql`](file:///d:/SmartDormitory/sql/smart_dormitory.sql) | MySQL 8.0 数据库脚本 | 13 张核心表 + 仿真业务测试数据 | `3306` |
| [`backend/`](file:///d:/SmartDormitory/backend) | Java 后端核心服务 | Spring Boot 3.2 + MyBatis-Plus + JDK 17 | `7000` |
| [`frontend-admin/`](file:///d:/SmartDormitory/frontend-admin) | PC Web 综合管理端 | Vue 3 + Vite + Element Plus + ECharts 5 | `5173` |
| [`frontend-student/`](file:///d:/SmartDormitory/frontend-student) | 微信小程序 / H5 学生端 | Uni-app (Vue 3) + 微信胶囊真机交互壳 | `5174` |

---

## 二、 核心“智慧”创新与全链路闭环特性

### 1. 基于六维生活习惯画像的智能分寝与调宿推荐引擎（[`SmartAllocationService.java`](file:///d:/SmartDormitory/backend/src/main/java/com/dormitory/service/SmartAllocationService.java)）
- **六维特征向量**：采集作息时间、睡眠浅度、空调偏好、卫生标准、寝室氛围、异味敏感度 6 个维度。
- **自定义权重沙盘与同班聚拢约束**：支持在线调节六维权重占比、切换“纯习惯优先 (`HABIT_FIRST`)”与“同班级优先聚拢 + 习惯聚类 (`CLASS_MIX`)”策略，并与“传统按学号分寝”进行契合度对比实验及六维雷达图可视化；支持**一键重置 2026 级演示新生**供答辩反复演示。
- **智能调宿推荐与退宿闭环**：为拟调宿学生自动计算全楼空余床位寝室的**预期契合度 (`predictedHarmony`)** 并按 Top 3 推荐；审批通过或手动分床后自动重算原寝室与目标寝室的和谐度评分。

### 2. 智能报修优先级识别与最小负载派单引擎（[`SmartRepairService.java`](file:///d:/SmartDormitory/backend/src/main/java/com/dormitory/service/SmartRepairService.java)）
- 自动扫描报修文本中的高危词库（`跳闸/短路/火花/爆管/漏水`），自动提权为 `HIGH（高危紧急）` 置顶，并按工种匹配当前待办工单数最少的维修师傅。
- 支持学生端结单后进行 **1~5 星维修服务回访评价**。

### 3. 水电阶梯计价核算、账户余额联动与能耗突增预警（[`SmartUtilityService.java`](file:///d:/SmartDormitory/backend/src/main/java/com/dormitory/service/SmartUtilityService.java)）
- 自动扣除基础免额并按第一、第二阶梯单价核算水电费及人均分摊金额；超出楼栋均值 1.5 倍自动触发“违规大功率电器或漏水排查”预警。
- **电费/水费双账户充值与缴费联动抵扣**：账单缴纳自动优先抵扣寝室预存水电余额，余额低于 ¥20 自动高亮欠费预警。

### 4. 多维量化“文明寝室”动态评比、申诉核销与激励发放（[`SmartEvaluationService.java`](file:///d:/SmartDormitory/backend/src/main/java/com/dormitory/service/SmartEvaluationService.java)）
- 按 `50% 卫生均分 + 30% 纪律晚归表现 + 20% 水电节能表现` 自动核算各寝室星级与文明示范称号。
- **违纪/晚归在线申诉与扣分回滚**：学生可针对晚归或违纪录入发起申诉（如实验室科研、校医院急诊），宿管审核通过后自动将状态置为 `2-申诉撤销` 并实时恢复文明寝室纪律得分。
- **五星文明寝室电费补贴一键发放**：支持为当期 `5★ 文明标杆寝室` 一键派发 ¥30（约合 50 度电）电费奖励并自动发布全校表彰红榜公告。

### 5. 23:00 夜间门禁智能稽核、访客电子绿码与 RBAC 差异化工作台
- **23:00 门禁考勤模拟稽核**：自动比对在寝人数与提前提交的 `[学生报备]` 晚归名单，支持一键触发夜间门禁扫描并仅对“未报备晚归”学生自动生成警示记录。
- **访客动态电子通行绿码**：学生端登记亲友来访后自动生成带 `VP-xxxxxx` 核验码的动态 SVG 绿码凭证（支持全屏亮码），管理端实时监测在楼时长与超时滞留预警。
- **多角色差异化工作台**：针对后勤主任 (`ADMIN`)、楼栋宿管 (`DORM_MANAGER`)、维修工程师 (`REPAIRMAN`) 动态裁剪导航菜单与专属工作台。服务端已限制部分角色入口，但楼栋和学生数据范围授权尚未完成，当前仅供演示使用。

---

## 三、 快速启动步骤

### 1. 导入数据库
1. 确保本地 MySQL 8.0 已启动，执行 [`sql/smart_dormitory.sql`](file:///d:/SmartDormitory/sql/smart_dormitory.sql)（会自动创建 `smart_dormitory` 库及全部初始数据）。
2. 检查 [`backend/src/main/resources/application.yml`](file:///d:/SmartDormitory/backend/src/main/resources/application.yml) 中的数据库账号密码（默认为 `root` / `123456`）。

### 2. 启动全部服务
直接双击运行根目录下的 [`start-all.bat`](file:///d:/SmartDormitory/start-all.bat)，或分别在终端执行：

```powershell
# 1. 启动后端 (7000)
cd d:\SmartDormitory\backend
mvn spring-boot:run

# 2. 启动 Web 管理端 (5173)
cd d:\SmartDormitory\frontend-admin
npm install
npm run dev

# 3. 启动微信小程序/H5学生端 (5174)
cd d:\SmartDormitory\frontend-student
npm install
npm run dev
```

---

## 四、 演示测试账号一览（密码统一为 `123456`）

- **Web 管理端 (`http://localhost:5173`)**：
  - 系统管理员（后勤主任）：`admin` / `123456`
  - 楼栋宿管员：`dm01` / `123456`
  - 维修工程师（电工）：`worker01` / `123456`
- **学生 H5 端 (`http://localhost:5174`)**：使用学号登录，例如 `20230101`（陈嘉豪）、`20230105`（刘子墨）、`20260101`（陆景行）或 `20230201`（苏婉清）。

登录后使用服务端内存会话，有效期 8 小时；后端重启会使所有会话失效。旧的明文或 MD5 密码在成功登录时升级为 BCrypt。演示数据库仍使用默认密码；部分学生业务及宿管楼栋数据范围隔离尚未实现，请勿用于真实数据或公开部署。
