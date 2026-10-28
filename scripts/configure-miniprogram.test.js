const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { candidates, configure, updateHost } = require('./configure-miniprogram');

const entry = address => ({ address, family: 'IPv4', internal: false });
const interfaces = {
  'vEthernet (WSL)': [entry('172.20.0.1')],
  WLAN: [entry('192.168.1.12')],
  Ethernet: [entry('10.0.0.8')],
  loopback: [{ address: '127.0.0.1', family: 'IPv4', internal: true }]
};

test('selects a physical Wi-Fi address ahead of virtual adapters', () => {
  assert.equal(candidates(interfaces)[0].address, '192.168.1.12');
  assert.equal(candidates(interfaces).some(item => item.address === '127.0.0.1'), false);
});

test('updates the address without changing the rest of the config', () => {
  const source = "// comment\nconst SERVER_HOST = 'http://127.0.0.1:7000';\n\nmodule.exports = SERVER_HOST;\n";
  assert.equal(updateHost(source, '10.0.0.8', '8000'), source.replace('127.0.0.1:7000', '10.0.0.8:8000'));
  assert.throws(() => updateHost('module.exports = {};', '10.0.0.8', '7000'));
});

test('honors an assigned IP override and Spring Boot port, rejects invalid overrides', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'smart-dorm-config-test-'));
  const configPath = path.join(dir, 'config.js');
  t.after(() => { if (fs.existsSync(configPath)) fs.unlinkSync(configPath); fs.rmdirSync(dir); });
  fs.writeFileSync(configPath, "const SERVER_HOST = 'http://127.0.0.1:7000';\n");
  configure({ interfaces, env: { SMART_DORM_IP: '10.0.0.8', SERVER_PORT: '8080' }, configPath });
  assert.match(fs.readFileSync(configPath, 'utf8'), /http:\/\/10\.0\.0\.8:8080/);
  assert.throws(() => configure({ interfaces, env: { SMART_DORM_IP: '192.168.1.99' }, configPath }));
  assert.match(fs.readFileSync(configPath, 'utf8'), /http:\/\/10\.0\.0\.8:8080/);
});

test('clears a stale LAN address when no usable network is connected', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'smart-dorm-config-test-'));
  const configPath = path.join(dir, 'config.js');
  t.after(() => { if (fs.existsSync(configPath)) fs.unlinkSync(configPath); fs.rmdirSync(dir); });
  fs.writeFileSync(configPath, "const SERVER_HOST = 'http://192.168.1.12:7000';\n");
  configure({ interfaces: {}, env: {}, configPath });
  assert.match(fs.readFileSync(configPath, 'utf8'), /http:\/\/127\.0\.0\.1:7000/);
});
