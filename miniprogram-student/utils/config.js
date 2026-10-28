// 智慧宿管学生端后端服务地址配置
// 在微信开发者工具中：详情 -> 本地设置 -> 勾选【不校验合法域名、web-view(业务域名)、TLS版本以及HTTPS证书】
// 本地启动脚本会自动将 SERVER_HOST 更新为当前局域网地址。

const SERVER_HOST = 'http://10.82.24.237:7000';

const config = {
  host: SERVER_HOST,
  baseUrl: `${SERVER_HOST}/api`,
  uploadUrl: `${SERVER_HOST}/api/upload/image`,
  fileBaseUrl: SERVER_HOST
};

module.exports = config;
