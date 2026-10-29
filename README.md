# 宿里 · 智慧宿舍管理系统

这是一个供毕业设计和本地演示使用的宿舍管理项目，包含管理端网页、学生端网页、学生微信小程序和一个共同的后端。

如果你是第一次接触编程，请从下面的 **“Windows 第一次运行”** 开始，按顺序完成。第一次安装需要联网下载软件和项目依赖，可能需要一些时间。本文以 Windows 10/11 为主要示例，macOS/Linux 命令见后文。

> 本项目包含演示账号、模拟充值和演示数据，只适合本地学习与答辩。不要直接用于真实学生数据、真实支付或公开部署。

## 一、先了解要运行什么

| 部分 | 用途 | 如何打开 |
| --- | --- | --- |
| 后端 | 提供登录、数据库和业务接口；其他页面需要它 | 在终端启动，默认端口 `7000` |
| 管理端 | 管理员、宿管、维修员、辅导员使用 | 浏览器打开 [http://localhost:5173](http://localhost:5173) |
| 学生端 H5 | 学生使用的网页，可以在电脑浏览器体验 | 浏览器打开 [http://localhost:5174](http://localhost:5174) |
| 微信小程序 | 学生和宿管在微信开发者工具中体验 | 导入 `miniprogram-student` 文件夹 |
| MySQL | 保存账号、床位、报修等数据 | 安装并启动 MySQL 8.0，默认端口 `3306` |

**后端、管理端和学生端是三个独立服务。** 使用时需要让它们对应的终端窗口保持打开；只打开网页而没有启动后端，登录不会成功。微信小程序是可选的，不使用小程序时也能运行两个网页。

## 二、Windows 第一次运行

### 第 1 步：准备项目文件

1. 将项目完整解压到电脑上的一个文件夹，例如 `D:\SmartDormitory`。建议路径中不要包含中文、空格或特殊字符，以便初学者复制命令。
2. 打开该文件夹，确认里面能看到 `backend`、`frontend-admin`、`frontend-student`、`miniprogram-student`、`sql` 和 `start-backend.bat`。
3. 后面的 `D:\SmartDormitory` 只是示例。如果你的项目在其他位置，请使用自己的路径。

**如何在项目文件夹打开 PowerShell：** 在文件资源管理器中打开项目文件夹，点击顶部的地址栏，输入 `powershell` 并按回车。会弹出一个命令窗口；后面每次说“打开新的 PowerShell 窗口”时，都可以重复这个操作。复制代码块中的命令后，粘贴到窗口并按回车。

### 第 2 步：安装运行环境

下面的软件需要安装在同一台电脑上。下载安装包时选择与你的系统匹配的版本；普通 Windows 电脑一般选择 Windows x64。

| 软件 | 推荐版本和作用 | 下载与安装提示 |
| --- | --- | --- |
| Java JDK | **JDK 17**；运行后端 | [Eclipse Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17)，选择 JDK 和 Windows 安装包，安装时允许设置 `JAVA_HOME`、添加到 `PATH` |
| Maven | **Maven 3.9**；下载后端依赖并启动 | [Apache Maven 下载页](https://maven.apache.org/download.cgi)，下载 **Binary zip archive**，解压后把其 `bin` 文件夹加入系统 `Path`；参见[官方安装说明](https://maven.apache.org/install.html) |
| Node.js | **Node.js 22 LTS**；运行两个网页和小程序地址配置脚本 | [Node.js 下载页](https://nodejs.org/en/download)，安装时保留 npm 和添加到 `PATH` 的选项 |
| MySQL Server | **MySQL 8.0**；保存数据 | [MySQL Installer 8.0](https://dev.mysql.com/downloads/installer/)，安装 **MySQL Server**；推荐同时安装 **MySQL Workbench**，便于图形化导入数据库 |
| 微信开发者工具 | 只有需要运行小程序时才安装 | [微信开发者工具下载页](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html) |

**Windows 环境变量怎么设置：** 点击 Windows 开始菜单，搜索并打开“编辑系统环境变量”，点击“环境变量”。在上半部分“用户变量”中，先检查是否有 `JAVA_HOME`；没有就点击“新建”，变量名填 `JAVA_HOME`，变量值填 JDK 17 的安装目录。然后选中用户变量中的 `Path`，点击“编辑 → 新建”，分别加入 `%JAVA_HOME%\bin` 和 Maven 解压目录下的 `bin` 完整路径，例如 `D:\Tools\apache-maven-3.9.x\bin`。示例路径要换成自己电脑上的真实路径，Maven 路径应能找到 `mvn.cmd`。逐个点击“确定”保存；如果安装程序已经正确配置，则不必重复添加。

安装 MySQL 时请记住设置的 `root` 密码，端口保持 `3306`，并选择将 MySQL 配置为 Windows 服务。项目默认使用 MySQL 用户名 `root`、密码 `123456`；如果你安装时设置了不同密码，不需要修改 MySQL 密码，后面会教你告诉后端正确密码。

安装完毕后，**关闭之前的 PowerShell 窗口，重新打开**，依次执行：

```powershell
java -version
mvn -v
node -v
npm.cmd -v
```

预期：能看到 Java 17、Maven 3.9、Node.js 版本和 npm 版本；其中 `mvn -v` 显示的 Java 版本也必须是 17。如果提示“无法识别命令”，通常是软件未安装或没有加入 `Path`；重新打开终端，仍无效时检查该软件的安装说明。这里使用 `npm.cmd` 是为了避免部分 Windows PowerShell 的脚本执行策略拦截 `npm.ps1`。

如果 `java -version` 是 17，但 `mvn -v` 仍显示其他版本，检查 Windows 环境变量 `JAVA_HOME` 是否指向 JDK 17 的**安装目录**，例如 `C:\Program Files\Eclipse Adoptium\jdk-17...`，而不是它里面的 `bin`；修改后重新打开 PowerShell 再检查。具体路径以你的电脑为准。

### 第 3 步：启动 MySQL 并准备演示数据库

1. 按 `Win + R`，输入 `services.msc` 并回车，找到 MySQL 服务（名称可能是 `MySQL80` 或 `MySQL`），确认状态为“正在运行”。如果没有运行，右键选择“启动”。
2. 打开 **MySQL Workbench**，连接本机 MySQL：主机 `localhost`、端口 `3306`、用户 `root`，输入你安装 MySQL 时设置的密码。
3. 先在 Workbench 中检查是否已经有名为 `smart_dormitory` 的数据库。**初始化脚本会删除并重建同名数据库，里面的原有数据会丢失。**
   - 如果是第一次安装，且没有需要保留的 `smart_dormitory` 数据库，继续下一步。
   - 如果数据库已经存在并有需要保留的数据，**不要运行初始化脚本**。先备份数据库，再根据 [已有数据库升级说明](sql/MIGRATION.md) 执行所需增量脚本；包括 [会话表增量脚本](sql/20261029-auth-session.sql)。不确定时先停止，避免覆盖数据。
4. 首次安装时，在 Workbench 菜单中选择 **File → Open SQL Script**，打开项目里的 `sql/smart_dormitory.sql`。
5. 确保没有只选中脚本中的一小段文字，选择 **Query → Execute (All or Selection)** 执行完整脚本。等待执行结束，检查下方输出是否有错误。不要只执行当前光标所在的单条语句。
6. 在新的 SQL 标签页执行下面的检查语句：

```sql
USE smart_dormitory;
SELECT COUNT(*) AS user_count FROM sys_user;
SHOW TABLES LIKE 'auth_session';
```

第一条计数应大于 0，第二条应显示 `auth_session`。这表示演示账号已导入，会话表也已创建。执行失败时，请先处理 Workbench 的报错，再继续启动后端。

### 第 4 步：安装两个网页的依赖

依赖就是项目运行需要下载的程序包。打开位于**项目根目录**的 PowerShell，依次执行：

```powershell
cd .\frontend-admin
npm.cmd ci
cd ..\frontend-student
npm.cmd ci
cd ..
```

两次 `npm.cmd ci` 都需要完成且不能出现安装失败的 `ERR!`。第一次可能需要几分钟，期间请保持联网。以后没有更新依赖时，通常不需要每次重新执行；换电脑或依赖发生变化时再执行。

### 第 5 步：启动后端

打开位于**项目根目录**的新 PowerShell 窗口：

1. 如果 MySQL 用户名不是 `root` 或密码不是 `123456`，先输入下面两行，把引号中的内容改为你自己的 MySQL 用户名和密码；这只对当前窗口及其启动的进程生效。若使用默认值，可以跳过这两行。

   ```powershell
   $env:DB_USER = 'root'
   $env:DB_PWD = '这里改成你的MySQL密码'
   ```

2. 如果需要演示**模拟水电充值、余额核销和重置演示新生**，并且使用的是独立演示数据库，在同一个窗口输入：

   ```powershell
   $env:DEMO_MODE = 'true'
   ```

   这一步可选；不设置时上述演示操作默认关闭。模拟充值不会产生真实扣款，重置演示新生会释放 `2026级` 学生床位，只能对隔离演示数据使用。

3. 在**同一个窗口**启动后端：

   ```powershell
   .\start-backend.bat
   ```

4. 首次启动 Maven 会下载依赖。等待日志出现类似 `Started SmartDormitoryApplication` 的字样，说明后端已启动。保持这个窗口打开，不要按 `Ctrl+C`。

如果日志显示数据库连接失败，先检查 MySQL 服务、端口、用户名密码以及第 3 步是否成功。`backend/.env.example` 是配置示例，**复制或修改它不会自动设置环境变量**；请按上面的 PowerShell 步骤设置。

### 第 6 步：启动管理端和学生端网页

**另开一个**位于项目根目录的 PowerShell，启动管理端：

```powershell
cd .\frontend-admin
npm.cmd run dev
```

等待看到 Vite 的 `Local: http://localhost:5173/`，保持窗口打开。

再**另开一个**位于项目根目录的 PowerShell，启动学生端网页：

```powershell
cd .\frontend-student
npm.cmd run dev
```

等待看到 `Local: http://localhost:5174/`，保持窗口打开。此时应有三个服务窗口：后端、管理端和学生端。

如果 Vite 提示原端口被占用并自动换成其他端口，请先关闭占用 `5173` 或 `5174` 的旧程序，再重新启动；两个登录页面中的切换链接使用的是默认端口。

### 第 7 步：打开网页并登录

在同一台电脑的浏览器打开：

- 管理端：[http://localhost:5173](http://localhost:5173)
- 学生端 H5：[http://localhost:5174](http://localhost:5174)

首次导入演示数据库后，可使用以下账号，**初始密码均为 `123456`**：

| 入口 | 身份 | 账号 |
| --- | --- | --- |
| 管理端 | 管理员 | `admin` |
| 管理端 | 楼栋宿管 | `dm01` |
| 管理端 | 维修员 | `worker01` |
| 管理端 | 辅导员 | `counselor01` |
| 学生端或小程序 | 已入住学生 | `20230101` |
| 学生端或小程序 | 待分寝新生 | `20260101` |
| 小程序 | 楼栋宿管 | `dm01` |

如果你使用的是已有数据库或已经修改过密码，实际账号和密码以该数据库为准。

**检查是否真正运行成功：** 管理员能登录并看到管理工作台；学生账号能登录学生端并看到首页数据。只有登录页出现还不能证明数据库和后端已经连通。

## 三、运行微信小程序（可选）

1. 先按上文启动 MySQL 和后端，并保持后端窗口打开。管理端和学生 H5 是否启动不影响小程序访问后端。
2. 安装并打开微信开发者工具，按提示登录，选择**导入项目**。
3. 项目目录选择项目中的 `miniprogram-student` 文件夹：该文件夹内应能直接看到 `app.json` 和 `project.config.json`。不要选项目根目录，也不要选 `frontend-student`。
4. AppID 使用你有权限的 AppID；没有正式 AppID 时，可按开发者工具提示使用测试号。仓库里的 AppID 不代表你有对应账号权限。
5. 在开发者工具的 **详情 → 本地设置** 中，按工具当前版本的提示勾选“不校验合法域名、TLS 版本及 HTTPS 证书”等本地调试选项，然后点击**编译**。
6. 在模拟器中使用上面的学生或宿管账号登录。小程序的水电充值仅为演示模拟，需要后端开启 `DEMO_MODE=true`；它不是微信支付。

本地后端启动时会尝试自动更新 `miniprogram-student/utils/config.js` 的后端地址。若模拟器请求失败，可检查该文件中的 `SERVER_HOST`：本机模拟器通常可使用 `http://127.0.0.1:7000`。修改后重新编译。

**手机真机调试：** 手机和电脑需要处于能互相访问的同一局域网；手机上的 `127.0.0.1` 指向手机自己，不能指向电脑。使用电脑的局域网 IPv4，例如 `http://192.168.1.100:7000`，并确保防火墙允许访问后端端口。切换网络后可以在项目根目录运行 `node scripts/configure-miniprogram.js`，或在启动后端前设置 `$env:SMART_DORM_IP = '电脑的局域网IPv4'`，然后重新编译并按微信工具提示进行真机调试。普通预览和正式发布仍受微信合法域名、HTTPS 和账号权限等要求限制；电脑模拟器成功不代表扫码或真机功能已验证。

## 四、以后如何启动与关闭

首次安装依赖并导入数据库后，通常不需要重复第 3、4 步。每次使用时：

1. 确认 MySQL 服务已运行。
2. 打开后端窗口，按需设置 `DB_USER`、`DB_PWD`、`DEMO_MODE`，运行 `.\start-backend.bat`。这些临时环境变量不会自动保存到新窗口。
3. 分别启动管理端和学生端的 `npm.cmd run dev`，然后打开网页。需要小程序时再打开微信开发者工具。
4. 结束使用时，分别在三个服务窗口按 `Ctrl+C`；提示是否终止批处理时，按提示确认。MySQL 服务可以继续运行，也可在 Windows 服务管理器中停止。

熟悉以上步骤后，也可以在已安装环境、已导入数据库、已安装前端依赖的情况下双击 `start-all.bat`，它会打开三个服务窗口。它本身不会安装依赖或导入数据库；如需设置自定义数据库密码或开启演示模式，应从**已经设置这些环境变量的 PowerShell** 中运行 `.\start-all.bat`。请分别检查三个新窗口中的启动结果。

## 五、macOS / Linux

先安装 JDK 17、Maven 3.9、Node.js 22、MySQL 8.0，启动 MySQL；数据库导入时同样要先阅读第 3 步的数据覆盖提示。可以用 MySQL Workbench 打开并执行 `sql/smart_dormitory.sql`，已有数据则按 [增量升级说明](sql/MIGRATION.md) 处理。

在终端执行以下命令，把第一行替换成你的项目路径：

```bash
cd /你的路径/SmartDormitory
java -version
mvn -v
node -v
npm -v
(cd frontend-admin && npm ci)
(cd frontend-student && npm ci)
```

然后在项目根目录启动后端；仅在示例值不同时修改数据库账号和密码，演示模式只在需要且使用隔离演示库时开启：

```bash
export DB_USER='root'
export DB_PWD='你的MySQL密码'
export DEMO_MODE='true'   # 可选；模拟充值和演示重置
bash ./start-backend.sh
```

另外打开两个终端，在项目根目录分别执行：

```bash
cd frontend-admin && npm run dev
```

```bash
cd frontend-student && npm run dev
```

等三个服务启动后，使用上面的浏览器地址和演示账号登录。也可在完成安装后从项目根目录运行 `bash ./start-all.sh`；在该脚本所在终端按 `Ctrl+C` 会尝试停止三个子进程。

## 六、常见问题

| 现象 | 建议检查 |
| --- | --- |
| `java`、`mvn`、`node` 或 `npm` 找不到 | 确认软件已安装并加入 `Path`，关闭并重新打开终端；`mvn -v` 必须显示 Java 17。 |
| `Access denied for user` | MySQL 用户名或密码不匹配。在启动后端的同一个窗口设置 `DB_USER` 和 `DB_PWD`，然后重启后端。 |
| `Communications link failure`、连接 3306 失败 | 确认 MySQL 服务正在运行，端口为 3306，本机防火墙或其他程序没有阻止连接。 |
| `Table ... doesn't exist`，特别是 `auth_session` | 首次安装检查 SQL 是否完整执行。已有库先备份，再按 [升级说明](sql/MIGRATION.md) 执行缺失的增量脚本；不要重新导入会删库的初始化脚本。 |
| 后端提示 `Address already in use` | 默认 `7000` 被占用，关闭已有后端或其他占用该端口的程序后再启动。不要关闭不认识的重要系统进程。 |
| 网页显示连接失败或登录失败 | 检查三个服务窗口是否仍在运行、后端是否成功启动、地址是否是正确的 `5173`/`5174`。检查浏览器和后端窗口的具体错误。 |
| 水电模拟充值或演示重置不可用 | 后端默认关闭演示模式；在启动后端的窗口设置 `DEMO_MODE=true` 并重启。只对隔离演示库开启。 |
| 支付宝沙箱不可用 | 默认关闭。需要自己的沙箱配置与可从支付宝访问的异步回调地址；没有配置时不会自动模拟到账。小程序不提供支付宝付款。 |
| 小程序请求失败 | 先确认后端已启动；检查开发者工具本地调试选项、`SERVER_HOST`、电脑与手机的网络和防火墙。手机不能把 `127.0.0.1` 当成电脑地址。 |
| `npm.cmd ci` 或 Maven 下载依赖失败 | 检查网络、代理和错误信息，然后重试；不要把中途失败当成安装完成。 |

## 七、功能与边界

项目包含床位与智能分寝、生活习惯画像、报修派单与评价、水电账单、访客登记与核验、调宿退宿申请、文明寝室评比以及站内通知。管理端根据角色限制入口和操作；学生 H5 与微信小程序的部分能力有所不同。

晚归页面目前只提供报备情况预览，不代表真实门禁记录，不会自动生成违纪。支付宝沙箱仅用于测试；配置方式见 [`backend/.env.example`](backend/.env.example)，需要在**启动后端的环境**中设置 `ALIPAY_ENABLED`、App ID、私钥、公钥和可访问的 `ALIPAY_NOTIFY_URL`。旧的、未记录订单金额的待处理订单需要人工核对；曾出现在仓库历史中的密钥若仍有效，应在支付平台撤销并轮换。项目尚未完成真实门禁、微信支付、生产支付对账、正式部署和运维验证。

登录会话的令牌哈希保存在数据库中，有效期为 8 小时；后端重启后会话仍可用。旧版本升级前的内存会话不能迁移，升级后需要重新登录。

## 八、项目目录与开发验证

```text
SmartDormitory/
├── backend/                 Java / Spring Boot 后端
├── frontend-admin/          管理端网页
├── frontend-student/        学生端 H5 网页，不是微信小程序
├── miniprogram-student/     微信小程序
├── sql/                     首次初始化和已有库增量脚本
├── scripts/                 小程序地址配置脚本及测试
├── start-backend.bat/.sh    单独启动后端
└── start-all.bat/.sh        启动后端与两个网页
```

开发者可在对应目录执行测试和构建；后端测试请使用 JDK 17：

```powershell
# backend 目录
mvn -q test

# frontend-admin 和 frontend-student 目录，分别执行
node --test src/*.test.js
npm.cmd run build

# miniprogram-student 目录
node --test tests/*.test.js
```

项目的 GitHub Actions 还会在独立 MySQL 测试库中验证部分并发业务。普通后端测试默认跳过这部分数据库集成测试；本地运行时请使用独立测试库，不能对现有业务库执行会修改数据的集成测试。
