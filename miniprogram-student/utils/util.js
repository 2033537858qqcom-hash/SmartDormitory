const formatTime = date => {
  if (!date) return '';
  const d = new Date(date);
  if (isNaN(d.getTime())) return String(date);
  const year = d.getFullYear();
  const month = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  const hour = d.getHours().toString().padStart(2, '0');
  const minute = d.getMinutes().toString().padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${minute}`;
};

const formatDate = date => {
  if (!date) return '';
  const d = new Date(date);
  if (isNaN(d.getTime())) return String(date);
  const year = d.getFullYear();
  const month = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const formatMoney = val => {
  if (val === null || val === undefined) return '¥ 0.00';
  const num = Number(val);
  return isNaN(num) ? '¥ 0.00' : `¥ ${num.toFixed(2)}`;
};

const HABIT_CONFIG = {
  schedule: { title: '作息时间', labels: ['早睡早起', '偏早作息', '规律作息', '偏晚作息', '夜猫子'] },
  sleep: { title: '睡眠浅度', labels: ['睡眠很深', '睡眠较深', '睡眠适中', '睡眠较浅', '容易惊醒'] },
  ac: { title: '空调偏好', labels: ['喜欢较凉', '偏凉', '适中', '偏暖', '比较怕冷'] },
  hygiene: { title: '卫生标准', labels: ['随性', '基本整洁', '定期打扫', '注重整洁', '非常整洁'] },
  atmosphere: { title: '寝室氛围', labels: ['安静自习', '偏安静', '劳逸结合', '偏活跃', '热闹社交'] },
  smell: { title: '异味敏感', labels: ['不太敏感', '轻微敏感', '适中', '比较敏感', '非常敏感'] }
};
Object.keys(HABIT_CONFIG).forEach(key => {
  HABIT_CONFIG[key].options = HABIT_CONFIG[key].labels.map((label, index) => ({ value: index + 1, label }));
});

const getHabitOptionLabel = (dimension, value) => {
  const item = HABIT_CONFIG[dimension];
  if (!item) return '';
  const opt = item.options.find(o => o.value === Number(value));
  return opt ? opt.label : '';
};

module.exports = {
  formatTime,
  formatDate,
  formatMoney,
  HABIT_CONFIG,
  getHabitOptionLabel
};
