// Update the local development address before starting the backend.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');

function candidates(interfaces) {
  const addresses = [];
  for (const [name, entries] of Object.entries(interfaces)) {
    for (const entry of entries || []) {
      if ((entry.family !== 'IPv4' && entry.family !== 4) || entry.internal ||
          entry.address.startsWith('169.254.') || net.isIP(entry.address) !== 4) continue;
      const virtual = /virtual|vethernet|vmware|vbox|hyper-v|docker|wsl|\btun\b|\btap\b|loopback/i.test(name);
      const wireless = /wi-?fi|wlan|wireless|无线|airport/i.test(name);
      const privateIp = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(entry.address);
      addresses.push({ name, address: entry.address, score: (virtual ? -100 : 0) + (privateIp ? 20 : 0) + (wireless ? 10 : 0) });
    }
  }
  return addresses.sort((a, b) => b.score - a.score || a.name.localeCompare(b.name));
}

function updateHost(source, address, port) {
  const pattern = /^const SERVER_HOST = ['"]http:\/\/[^'"]+['"];[ \t]*\r?$/m;
  if (!pattern.test(source)) throw new Error('无法找到 utils/config.js 中的 SERVER_HOST，配置未修改');
  return source.replace(pattern, `const SERVER_HOST = 'http://${address}:${port}';`);
}

function configure({ interfaces = os.networkInterfaces(), env = process.env, configPath = path.join(__dirname, '..', 'miniprogram-student', 'utils', 'config.js') } = {}) {
  const available = candidates(interfaces);
  const override = (env.SMART_DORM_IP || '').trim();
  const port = (env.SERVER_PORT || '7000').trim();
  if (!/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535) throw new Error('SERVER_PORT 必须是 1 到 65535 的端口号');
  if (override && (net.isIP(override) !== 4 || !available.some(item => item.address === override))) {
    throw new Error('SMART_DORM_IP 必须是本机已连接网卡的 IPv4 地址');
  }
  const selected = override ? available.find(item => item.address === override) : available[0];
  const address = selected ? selected.address : '127.0.0.1';
  const source = fs.readFileSync(configPath, 'utf8');
  const updated = updateHost(source, address, port);
  if (updated !== source) fs.writeFileSync(configPath, updated, 'utf8');
  if (selected) {
    console.log(`小程序后端地址: http://${address}:${port}（${selected.name}）`);
    if (!override && selected.score < 0) console.warn('当前选中的是可能无法被手机访问的虚拟网卡，请检查网络或指定 SMART_DORM_IP。');
    if (!override && available.filter(item => item.score >= 0).length > 1) {
      console.warn('检测到多个网卡；如手机无法访问，可通过 SMART_DORM_IP 指定与手机同网段的本机 IPv4。');
    }
  } else {
    console.warn('未检测到可用的局域网 IPv4，小程序暂使用 127.0.0.1；联网后请重新启动后端。');
  }
  return address;
}

if (require.main === module) {
  try { configure(); } catch (error) { console.error(`小程序地址配置失败: ${error.message}`); process.exitCode = 1; }
}

module.exports = { candidates, updateHost, configure };
