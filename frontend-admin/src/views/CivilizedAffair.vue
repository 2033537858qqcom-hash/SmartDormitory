<template>
  <div v-loading="loading" class="page-stack">
    <div v-if="loadError" class="error-banner" role="alert">
      <span>宿务数据加载失败，请重试。</span>
      <el-button type="danger" plain size="small" @click="loadAll">重试</el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="surface-card">
      <!-- Tab 0: 宿管待办首页 -->
      <el-tab-pane :label="`待办 (${todoCount})`" name="todo">
        <div class="affair-intro">
          <h3>今日宿务待办</h3>
          <p>优先处理待审调宿、待审申诉与超时访客，再进入各专项台账。</p>
        </div>
        <div class="todo-grid">
          <button type="button" class="todo-card" @click="activeTab = 'adjust'">
            <strong>{{ pendingAdjustCount }}</strong>
            <span>待审调宿/退宿</span>
          </button>
          <button type="button" class="todo-card" @click="activeTab = 'visitor'">
            <strong>{{ pendingAppealCount }}</strong>
            <span>待审违纪申诉</span>
          </button>
          <button type="button" class="todo-card warn" @click="activeTab = 'visitor'">
            <strong>{{ overstayVisitorCount }}</strong>
            <span>超时未签离访客</span>
          </button>
          <button type="button" class="todo-card" @click="goGate">
            <strong>门岗</strong>
            <span>输码核验通行</span>
          </button>
        </div>
      </el-tab-pane>

      <!-- Tab 1: 文明寝室评比与奖励发放 -->
      <el-tab-pane label="文明寝室评比" name="eval">
        <div class="section-header">
          <div class="section-title-group">
            <span class="section-title">文明寝室评分榜单</span>
            <el-popover placement="right" :width="340" trigger="click">
              <template #reference>
                <el-button size="small" text type="primary">评分与补贴规则</el-button>
              </template>
              <div class="rule-popover-content">
                <h4>文明寝室综合评分与奖励规则</h4>
                <ul>
                  <li><b>卫生得分 (50%)</b>：每周内务检查平均分</li>
                  <li><b>纪律得分 (30%)</b>：100分减去生效中的晚归或违纪扣分（申诉核销后自动恢复）</li>
                  <li><b>节能得分 (20%)</b>：水电无异常得满分</li>
                  <li><b>五星奖励</b>：获评五星文明示范寝室可一键发放 50 度免费电费额度（折合 ¥30.00）</li>
                </ul>
              </div>
            </el-popover>
          </div>
          <div class="toolbar-actions">
            <el-button v-if="isAdmin" type="warning" plain size="small" icon="Present" :loading="rewarding" @click="handleRewardFiveStar">
              发放五星寝室电费补贴
            </el-button>
            <el-button v-if="isAdmin" type="success" plain size="small" icon="Download" @click="exportCivilizedCsv">导出榜单</el-button>
            <el-button type="primary" size="small" icon="Refresh" @click="loadEval">刷新评分</el-button>
          </div>
        </div>
        <p v-if="!loading && !loadError && !evalList.length" class="list-empty">暂无评分记录</p>
        <ol v-else-if="evalList.length" class="score-list" aria-label="文明寝室评分榜单">
          <li v-for="(row, index) in evalList" :key="row.roomId || `${row.buildingName}-${row.roomNo}`" class="score-row">
            <div class="score-identity">
              <span class="score-rank tabular-nums" :aria-label="`第 ${index + 1} 名`">{{ String(index + 1).padStart(2, '0') }}</span>
              <div><strong>{{ row.buildingName }} · {{ row.roomNo }}室</strong><small>电费余额 {{ row.electricBalance == null ? '暂无数据' : `¥${Number(row.electricBalance).toFixed(2)}` }}</small></div>
            </div>
            <dl class="score-breakdown tabular-nums">
              <div><dt>卫生 · 50%</dt><dd>{{ row.hygieneAvg ?? '—' }}</dd></div>
              <div><dt>纪律 · 30%</dt><dd>{{ row.disciplineScore ?? '—' }}</dd></div>
              <div><dt>节能 · 20%</dt><dd>{{ row.energyScore ?? '—' }}</dd></div>
            </dl>
            <div class="score-result"><span>综合评分</span><strong class="tabular-nums">{{ row.civilizedScore ?? '—' }} <small>分</small></strong></div>
            <div class="score-honor"><span v-if="row.starLevel != null" class="star-text" :aria-label="`${row.starLevel} 星`">{{ '★'.repeat(Math.max(0, Math.min(5, Number(row.starLevel) || 0))) }}</span><span>{{ row.honorTitle || '暂无称号' }}</span></div>
          </li>
        </ol>
      </el-tab-pane>

      <!-- Tab 2: 卫生检查台账 -->
      <el-tab-pane label="卫生检查" name="hygiene">
        <div class="section-header">
          <span class="section-subtitle">四项内务评分（地面25 + 床铺25 + 书桌25 + 安全25 = 100分）</span>
          <el-button type="primary" size="small" icon="Plus" @click="hygieneVisible = true">录入卫生检查</el-button>
        </div>
        <p v-if="!loading && !loadError && !hygieneList.length" class="list-empty">暂无卫生检查记录</p>
        <ul v-else-if="hygieneList.length" class="score-list" aria-label="卫生检查记录">
          <li v-for="row in hygieneList" :key="row.id" class="hygiene-row">
            <div class="hygiene-identity"><strong>{{ row.buildingName }} · {{ row.roomNo }}室</strong><small>{{ row.checkWeek || '未标注周次' }} · {{ row.checkDate || '日期未记录' }}</small></div>
            <dl class="hygiene-breakdown tabular-nums">
              <div><dt>地面</dt><dd>{{ row.floorScore ?? '—' }}</dd></div>
              <div><dt>床铺</dt><dd>{{ row.bedScore ?? '—' }}</dd></div>
              <div><dt>书桌</dt><dd>{{ row.deskScore ?? '—' }}</dd></div>
              <div><dt>安全</dt><dd>{{ row.safetyScore ?? '—' }}</dd></div>
            </dl>
            <div class="score-result"><span>总分 · {{ row.grade || '未评级' }}</span><strong class="tabular-nums">{{ row.totalScore ?? '—' }} <small>分</small></strong></div>
            <p class="hygiene-remark">{{ row.remark || '暂无检查评语' }}</p>
          </li>
        </ul>
      </el-tab-pane>

      <!-- Tab 3: 调宿与退宿审批（含六维契合度预评估） -->
      <el-tab-pane :label="`调宿与退宿审批 (${pendingAdjustCount})`" name="adjust">
        <div class="affair-intro">
          <h3>调宿与退宿申请审批</h3>
          <p>审批通过后自动完成新旧床位划转，并实时重算原寝室与目标寝室的六维生活习惯契合度。</p>
        </div>
        <p v-if="!adjustList.length" class="list-empty">暂无调宿或退宿申请</p>
        <ul v-else class="affair-list" aria-label="调宿申请列表">
          <li v-for="row in adjustList" :key="row.id" class="adjust-row">
            <div class="adjust-main">
              <div class="row-heading">
                <strong>{{ row.studentName }}</strong>
                <el-tag size="small" :type="row.applyType === 'CHECKOUT' ? 'danger' : 'primary'" effect="plain">
                  {{ row.applyType === 'CHECKOUT' ? '退宿离校' : '调换宿舍' }}
                </el-tag>
                <el-tag v-if="row.predictedHarmony != null" size="small" type="success" class="tabular-nums">
                  调入后预计契合度 {{ row.predictedHarmony }}%
                </el-tag>
                <span class="record-status" :class="row.status">
                  {{ row.status === 'PENDING' ? '待审批' : (row.status === 'APPROVED' ? '已批准' : '已驳回') }}
                </span>
              </div>
              <p class="row-meta">学号 {{ row.studentNo || '-' }} · 提交于 {{ formatDateTime(row.createTime) }}</p>
              <p class="room-route">{{ row.oldRoomName || '-' }} <span aria-label="调入">→</span> {{ row.targetRoomName || '-' }}</p>
              <p class="row-note">申请原因：{{ row.reason || '未填写' }}</p>
              <p v-if="row.status !== 'PENDING'" class="row-note">审批意见：{{ row.reviewComment || '已归档' }}</p>
            </div>
            <div v-if="row.status === 'PENDING'" class="review-actions">
              <el-button type="primary" size="small" @click="reviewAdjust(row, 'APPROVED')">批准并同步床位</el-button>
              <el-button size="small" @click="confirmRejectAdjust(row)">驳回</el-button>
            </div>
          </li>
        </ul>
      </el-tab-pane>

      <!-- Tab 4: 23:00 门禁考勤稽核、违纪申诉与访客核验 -->
      <el-tab-pane :label="`门禁考勤与访客 (${pendingAppealCount ? `${pendingAppealCount}待审` : '正常'})`" name="visitor">
        <!-- 顶部 23:00 门禁考勤实时统计与模拟稽核栏 -->
        <div class="curfew-bar tabular-nums">
          <div class="curfew-metrics">
            <div class="cm-item">
              <span>在住总人数</span>
              <b>{{ curfewStats.totalCheckedIn || 0 }} 人</b>
            </div>
            <div class="cm-item">
              <span>准点在寝</span>
              <b class="text-ok">{{ curfewStats.normalInDormCount || 0 }} 人</b>
            </div>
            <div class="cm-item">
              <span>已报备合规晚归</span>
              <b>{{ curfewStats.reportedLateCount || 0 }} 人次</b>
            </div>
            <div class="cm-item">
              <span>待审学生申诉</span>
              <b :class="pendingAppealCount > 0 ? 'text-warn' : ''">{{ pendingAppealCount }} 项</b>
            </div>
          </div>
          <div class="toolbar-actions">
            <el-select v-model="curfewBuildingId" clearable placeholder="全部管辖楼栋" size="small" style="width: 160px">
              <el-option v-for="b in buildings" :key="b.id" :label="b.buildingName" :value="b.id" />
            </el-select>
            <el-button type="primary" plain size="small" icon="Aim" :loading="auditingCurfew" @click="handleCurfewAudit">
              按楼栋批量门禁稽核
            </el-button>
            <el-button size="small" icon="Plus" @click="disciplineVisible = true">
              录入违纪/晚归
            </el-button>
            <el-button size="small" icon="User" @click="visitorDialogVisible = true">
              登记外来访客
            </el-button>
          </div>
        </div>

        <div class="gate-verify-panel">
          <div class="gate-heading">
            <div>
              <h3>门岗通行码核验</h3>
              <p>输入学生亮码页或访客绿码上的 VP-xxxxxx，核验通过显示绿通，并可一键签离。</p>
            </div>
          </div>
          <div class="gate-form">
            <el-input v-model="gateCode" size="large" clearable placeholder="输入通行码 VP-xxxxxx" class="gate-input" @keyup.enter="verifyGate(false)">
              <template #prepend>门岗</template>
            </el-input>
            <el-button type="primary" size="large" :loading="gateLoading" @click="verifyGate(false)">核验</el-button>
            <el-button size="large" :disabled="!gateResult || !gateResult.passOk" :loading="gateLoading" @click="verifyGate(true)">核验并签离</el-button>
          </div>
          <div v-if="gateResult" class="gate-pass-card" :class="{ ok: gateResult.passOk, warn: gateResult.overstayFlag === 1, done: gateResult.status === 1 }">
            <div class="gate-pass-badge">{{ gateResult.passOk ? '通行通过' : (gateResult.status === 1 ? '已签离' : '需人工复核') }}</div>
            <div class="gate-pass-code tabular-nums">{{ gateResult.passCode }}</div>
            <dl>
              <div><dt>访客</dt><dd>{{ gateResult.visitorName }}（{{ gateResult.relation || '亲友' }}）</dd></div>
              <div><dt>被访学生</dt><dd>{{ gateResult.visitStudentName || '-' }}</dd></div>
              <div><dt>楼栋房间</dt><dd>{{ gateResult.buildingName }} {{ gateResult.roomNo }}室</dd></div>
              <div><dt>在访时长</dt><dd>{{ gateResult.stayMinutes }} 分钟<span v-if="gateResult.overstayFlag === 1" class="text-warn"> · 已超时</span></dd></div>
            </dl>
          </div>
        </div>

        <div v-if="curfewAuditResult" class="curfew-result surface-card" style="margin: 12px 0 16px; padding: 12px 14px;">
          <div class="section-header" style="margin-bottom: 8px;">
            <span class="section-title">本轮稽核结果</span>
            <span class="unit-note tabular-nums">
              扫描 {{ curfewAuditResult.scannedCount || 0 }} · 豁免 {{ curfewAuditResult.exemptedCount || 0 }} · 预警 {{ curfewAuditResult.warnedCount || 0 }}
            </span>
          </div>
          <el-table :data="curfewResultRows" size="small" max-height="220">
            <el-table-column prop="studentName" label="学生" min-width="100" />
            <el-table-column label="寝室" min-width="140">
              <template #default="{ row }">{{ row.buildingName }} {{ row.roomNo }}室</template>
            </el-table-column>
            <el-table-column label="结果" width="110">
              <template #default="{ row }">
                <el-tag :type="row.auditResult === 'EXEMPTED' ? 'success' : 'warning'" size="small">
                  {{ row.auditResult === 'EXEMPTED' ? '豁免' : '预警' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-row :gutter="20">
          <el-col :xs="24" :sm="24" :md="12" :lg="12" class="col-gap">
            <h4 class="sub-heading">晚归考勤与违纪申诉台账</h4>
            <p v-if="!disciplineList.length" class="list-empty">暂无晚归或违纪记录</p>
            <ul v-else class="affair-list" aria-label="晚归与违纪记录">
              <li v-for="row in disciplineList" :key="row.id" class="compact-row">
                <div class="row-heading">
                  <strong>{{ row.studentName }} ({{ row.roomName || '-' }})</strong>
                  <span v-if="row.status === 2" class="record-status APPROVED">✓ 申诉已核销 · 免扣分</span>
                  <span v-else-if="row.status === 3" class="record-status PENDING">● 学生申诉待审 · 原扣 {{ row.deductScore }} 分</span>
                  <span v-else class="record-status warning">
                    {{ recordTypeName(row.recordType) }} · 扣 {{ row.deductScore }} 分
                  </span>
                </div>
                <p class="row-meta tabular-nums">{{ formatDateTime(row.occurTime) }}</p>
                <p class="row-note">{{ row.description || '暂无说明' }}</p>
                <div v-if="row.status === 3 && (row.appealProofType || row.appealWitness || row.appealAttachments)" class="appeal-materials">
                  <div v-if="row.appealProofType">证明类型：{{ row.appealProofType }}</div>
                  <div v-if="row.appealWitness">证明人：{{ row.appealWitness }}</div>
                  <div v-if="row.appealAttachments">附件占位：{{ formatAttachments(row.appealAttachments) }}</div>
                </div>
                <div v-if="row.status === 3" class="appeal-actions">
                  <el-button type="success" plain size="small" @click="reviewDiscipline(row, true)">
                    准予核销 (恢复文明分)
                  </el-button>
                  <el-button type="danger" plain size="small" @click="reviewDiscipline(row, false)">
                    驳回申诉
                  </el-button>
                </div>
              </li>
            </ul>
          </el-col>
          <el-col :xs="24" :sm="24" :md="12" :lg="12" class="col-gap">
            <h4 class="sub-heading">外来访客电子绿码核验与签离</h4>
            <p v-if="!visitorList.length" class="list-empty">暂无访客登记</p>
            <ul v-else class="affair-list" aria-label="外来访客登记">
              <li v-for="row in visitorList" :key="row.id" class="compact-row visitor-row-card">
                <div class="visitor-main">
                  <div class="row-heading">
                    <strong>
                      {{ row.visitorName }}
                      <span class="relation">{{ row.relation }}</span>
                      <button type="button" class="pass-code-btn tabular-nums" @click="openAdminQrModal(row)">
                        {{ row.passCode }} · 查看绿码
                      </button>
                    </strong>
                    <div class="toolbar-actions">
                      <el-tag v-if="row.overstayFlag === 1" type="danger" size="small">超时在访</el-tag>
                      <el-button v-if="row.status === 0" type="primary" link @click="leaveVisitor(row.id)">核验签离</el-button>
                      <span v-else class="record-status">已签离</span>
                    </div>
                  </div>
                  <p class="row-meta tabular-nums">
                    被访学生：{{ row.visitStudentName || '-' }} ({{ row.buildingName }} {{ row.roomNo }}室) · 在访 {{ row.stayMinutes || 15 }} 分钟
                  </p>
                  <p class="row-note">来访事由：{{ row.visitReason || '-' }}</p>
                </div>
                <button type="button" class="admin-qr-thumb-btn" title="点击放大真实通行二维码" @click="openAdminQrModal(row)">
                  <svg
                    class="admin-mini-qr"
                    :class="{ expired: row.status !== 0 }"
                    :viewBox="getRealQrSpec(row).viewBox"
                    shape-rendering="crispEdges"
                    aria-label="真实访客通行二维码"
                  >
                    <rect width="100%" height="100%" fill="#ffffff" />
                    <path :d="getRealQrSpec(row).path" />
                  </svg>
                </button>
              </li>
            </ul>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 卫生检查录入弹窗 -->
    <el-dialog v-model="hygieneVisible" title="录入宿舍卫生检查" width="440px">
      <el-form :model="hygieneForm" label-width="105px">
        <el-form-item label="检查周次">
          <el-input v-model="hygieneForm.checkWeek" placeholder="如 第8周" />
        </el-form-item>
        <el-form-item label="选择寝室">
          <el-select v-model="hygieneForm.roomId" class="full-width">
            <el-option
              v-for="r in rooms"
              :key="r.id"
              :label="`${r.buildingName} - ${r.roomNo}室`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="地面整洁(25)">
          <el-input-number v-model="hygieneForm.floorScore" :min="0" :max="25" />
        </el-form-item>
        <el-form-item label="床铺内务(25)">
          <el-input-number v-model="hygieneForm.bedScore" :min="0" :max="25" />
        </el-form-item>
        <el-form-item label="书桌摆放(25)">
          <el-input-number v-model="hygieneForm.deskScore" :min="0" :max="25" />
        </el-form-item>
        <el-form-item label="用电安全(25)">
          <el-input-number v-model="hygieneForm.safetyScore" :min="0" :max="25" />
        </el-form-item>
        <el-form-item label="检查评语">
          <el-input v-model="hygieneForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hygieneVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHygiene">保存评分</el-button>
      </template>
    </el-dialog>

    <!-- 录入违纪/晚归弹窗 -->
    <el-dialog v-model="disciplineVisible" title="登记晚归或违纪记录" width="460px">
      <el-form :model="disciplineForm" label-width="95px">
        <el-form-item label="违纪学生">
          <el-select v-model="disciplineForm.studentId" class="full-width" filterable @change="onDisciplineStudentChange">
            <el-option
              v-for="s in allocatedStudents"
              :key="s.studentId"
              :label="`${s.realName} (${s.roomInfo})`"
              :value="s.studentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="记录类型">
          <el-select v-model="disciplineForm.recordType" class="full-width">
            <el-option label="超时晚归 (LATE_RETURN)" value="LATE_RETURN" />
            <el-option label="违规大功率电器 (ILLEGAL_APPLIANCE)" value="ILLEGAL_APPLIANCE" />
            <el-option label="夜不归宿 (NO_RETURN)" value="NO_RETURN" />
            <el-option label="喧哗扰民 (NOISE)" value="NOISE" />
          </el-select>
        </el-form-item>
        <el-form-item label="扣除文明分">
          <el-input-number v-model="disciplineForm.deductScore" :min="1" :max="25" />
        </el-form-item>
        <el-form-item label="事件说明">
          <el-input v-model="disciplineForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disciplineVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDiscipline">确认登记</el-button>
      </template>
    </el-dialog>

    <!-- 后台登记访客弹窗 -->
    <el-dialog v-model="visitorDialogVisible" title="前台登记外来访客" width="460px">
      <el-form :model="visitorForm" label-width="95px">
        <el-form-item label="访客姓名">
          <el-input v-model="visitorForm.visitorName" placeholder="请输入访客姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="visitorForm.visitorPhone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="被访学生">
          <el-select v-model="visitorForm.visitStudentId" class="full-width" filterable>
            <el-option
              v-for="s in allocatedStudents"
              :key="s.studentId"
              :label="`${s.realName} (${s.roomInfo})`"
              :value="s.studentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="与学生关系">
          <el-input v-model="visitorForm.relation" placeholder="如：家长 / 同学 / 快递运维" />
        </el-form-item>
        <el-form-item label="来访事由">
          <el-input v-model="visitorForm.visitReason" placeholder="请输入来访原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visitorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitVisitor">生成通行码并登记</el-button>
      </template>
    </el-dialog>

    <!-- 访客真实二维码门岗核验弹窗 -->
    <el-dialog v-model="adminQrVisible" title="门岗电子通行绿码核验" width="400px">
      <div v-if="activeVisitorQr" class="admin-qr-modal">
        <el-tag :type="activeVisitorQr.status === 0 ? 'success' : 'info'" effect="dark" round>
          {{ activeVisitorQr.status === 0 ? '● 绿码有效 · 准予通行' : '已签离失效' }}
        </el-tag>
        <div class="admin-qr-box" :class="{ expired: activeVisitorQr.status !== 0 }">
          <svg
            class="admin-large-qr"
            :viewBox="getRealQrSpec(activeVisitorQr).viewBox"
            shape-rendering="crispEdges"
            role="img"
            aria-label="真实访客通行二维码"
          >
            <rect width="100%" height="100%" fill="#ffffff" />
            <path :d="getRealQrSpec(activeVisitorQr).path" />
          </svg>
        </div>
        <p class="qr-hint">支持使用手机相机或微信「扫一扫」直接识别核验</p>
        <div class="qr-pass-num tabular-nums">{{ activeVisitorQr.passCode }}</div>
        <div class="admin-qr-meta tabular-nums">
          <div><span>访客姓名：</span><b>{{ activeVisitorQr.visitorName }} ({{ activeVisitorQr.relation }})</b></div>
          <div><span>受访寝室：</span><b>{{ activeVisitorQr.buildingName }} {{ activeVisitorQr.roomNo }}室 ({{ activeVisitorQr.visitStudentName }})</b></div>
          <div><span>来访事由：</span><b>{{ activeVisitorQr.visitReason }}</b></div>
          <div><span>入楼时间：</span><b>{{ formatDateTime(activeVisitorQr.enterTime) }}</b></div>
        </div>
      </div>
      <template #footer>
        <el-button @click="adminQrVisible = false">关闭</el-button>
        <el-button
          v-if="activeVisitorQr && activeVisitorQr.status === 0"
          type="primary"
          @click="leaveVisitor(activeVisitorQr.id); adminQrVisible = false"
        >
          核验并办理签离
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import request, { downloadExport } from '@/api/request'
const isAdmin = JSON.parse(localStorage.getItem('dorm_admin_user') || '{}').role === 'ADMIN'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const submitting = ref(false)
const rewarding = ref(false)
const auditingCurfew = ref(false)

const sectionMap = { todo: 'todo', eval: 'eval', hygiene: 'hygiene', adjust: 'adjust', visitor: 'visitor', gate: 'visitor' }
const activeTab = ref(sectionMap[route.params.section] || route.query.tab || 'todo')
watch(activeTab, (tab) => {
  const sec = tab === 'visitor' ? 'visitor' : tab
  if (route.params.section !== sec) {
    router.replace({ name: 'CivilizedAffair', params: { section: sec } })
  }
})
watch(() => route.params.section, (sec) => {
  const mapped = sectionMap[sec] || 'todo'
  if (activeTab.value !== mapped) activeTab.value = mapped
})
const evalList = ref([])
const hygieneList = ref([])
const adjustList = ref([])
const disciplineList = ref([])
const visitorList = ref([])
const gateCode = ref('')
const gateLoading = ref(false)
const gateResult = ref(null)
const rooms = ref([])
const allocatedStudents = ref([])
const curfewStats = ref({})
const buildings = ref([])
const curfewBuildingId = ref(null)
const curfewAuditResult = ref(null)
const curfewResultRows = computed(() => {
  const r = curfewAuditResult.value
  if (!r) return []
  return [...(r.warned || []), ...(r.exempted || []), ...(r.normal || [])]
})

const adminQrVisible = ref(false)
const activeVisitorQr = ref(null)

const pendingAdjustCount = computed(() =>
  adjustList.value.filter(a => a.status === 'PENDING').length
)

const pendingAppealCount = computed(() =>
  disciplineList.value.filter(d => d.status === 3).length
)

const formatDateTime = (val) => {
  if (!val) return '-'
  return String(val).replace('T', ' ').substring(0, 16)
}

const recordTypeName = (t) => {
  const map = {
    LATE_RETURN: '晚归记录',
    ILLEGAL_APPLIANCE: '违规电器',
    NO_RETURN: '夜不归宿',
    NOISE: '喧哗扰民'
  }
  return map[t] || t
}

// 生成真实可扫描的 ISO/IEC 18004 二维码矢量矩阵
const qrCache = new Map()
const getRealQrSpec = (v) => {
  if (!v) return { viewBox: '0 0 29 29', path: '' }
  const code = v.passCode || `VP-80${v.id || 1}`
  const statusStr = v.status === 0 ? '绿码有效(准予通行)' : '已签离失效'
  const payload = [
    '【智慧寓管·电子通行证】',
    `通行码:${code}`,
    `状态:${statusStr}`,
    `访客:${v.visitorName || '-'}(${v.relation || '亲友'})`,
    `受访:${v.buildingName || '公寓'}${v.roomNo || ''}室 ${v.visitStudentName || ''}`,
    `事由:${v.visitReason || '-'}`,
    `登记:${formatDateTime(v.enterTime)}`
  ].join('\n')

  if (qrCache.has(payload)) {
    return qrCache.get(payload)
  }

  const qr = QRCode.create(payload, { errorCorrectionLevel: 'M' })
  const size = qr.modules.size
  const data = qr.modules.data
  const margin = 2
  const totalSize = size + margin * 2
  let path = ''
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (data[y * size + x]) {
        path += `M${x + margin},${y + margin}h1v1h-1z`
      }
    }
  }
  const spec = {
    viewBox: `0 0 ${totalSize} ${totalSize}`,
    path
  }
  qrCache.set(payload, spec)
  return spec
}

const openAdminQrModal = (row) => {
  activeVisitorQr.value = row
  adminQrVisible.value = true
}

const hygieneVisible = ref(false)
const hygieneForm = reactive({
  checkWeek: '第8周',
  roomId: 1,
  floorScore: 25,
  bedScore: 25,
  deskScore: 24,
  safetyScore: 25,
  remark: '内务整洁，予以表扬'
})

const disciplineVisible = ref(false)
const disciplineForm = reactive({
  studentId: 14,
  roomId: 2,
  recordType: 'LATE_RETURN',
  deductScore: 5,
  description: '23:35 门禁通道晚归登记'
})

const visitorDialogVisible = ref(false)
const visitorForm = reactive({
  visitorName: '张建军',
  visitorPhone: '13900112233',
  visitStudentId: 10,
  buildingId: 1,
  roomNo: '301',
  relation: '家长',
  visitReason: '探望学生并送生活物资'
})

const onDisciplineStudentChange = (stuId) => {
  const found = allocatedStudents.value.find(s => s.studentId === stuId)
  if (found && found.roomId) {
    disciplineForm.roomId = found.roomId
  }
}

const loadEval = async () => {
  const res = await request.get('/affair/evaluation')
  evalList.value = res.data || []
}

const loadAll = async () => {
  loading.value = true
  loadError.value = false
  try {
    const [eRes, hRes, aRes, dRes, vRes, rRes, cRes, bRes, stuRes] = await Promise.all([
      request.get('/affair/evaluation'),
      request.get('/affair/hygiene'),
      request.get('/affair/adjust-list'),
      request.get('/affair/discipline'),
      request.get('/affair/visitors'),
      request.get('/dorm/rooms'),
      request.get('/affair/curfew-stats', { params: curfewBuildingId.value ? { buildingId: curfewBuildingId.value } : {} }),
      request.get('/dorm/buildings'),
      request.get('/smart-alloc/candidates')
    ])
    evalList.value = eRes.data || []
    hygieneList.value = hRes.data || []
    adjustList.value = aRes.data || []
    disciplineList.value = dRes.data || []
    visitorList.value = vRes.data || []
    rooms.value = rRes.data || []
    curfewStats.value = cRes.data || {}
    buildings.value = bRes.data || []
    allocatedStudents.value = (stuRes.data || []).filter(s => s.allocated)
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const handleRewardFiveStar = async () => {
  await ElMessageBox.confirm(
    '即将为所有获评【五星文明示范寝室】的在住房间发放 ¥30.00（折合50度免费电费额度）文明补贴，并同步在校园通知发布红榜表彰，是否确认？',
    '发放五星文明寝室电费补贴',
    {
      confirmButtonText: '确认发放',
      cancelButtonText: '取消',
      type: 'success'
    }
  )
  rewarding.value = true
  try {
    const res = await request.post('/affair/reward-civilized', { amount: 30 })
    ElMessage.success(res.message || '补贴发放成功')
    await loadAll()
  } finally {
    rewarding.value = false
  }
}

const formatAttachments = (raw) => {
  if (!raw) return '-'
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (Array.isArray(arr)) {
      return arr.map(x => String(x).startsWith('data:') ? '（已上传图片占位）' : x).join('、')
    }
  } catch (_) { /* ignore */ }
  return String(raw).slice(0, 80)
}

const handleCurfewAudit = async () => {
  auditingCurfew.value = true
  try {
    const body = {}
    if (curfewBuildingId.value) body.buildingId = curfewBuildingId.value
    const res = await request.post('/affair/curfew-check', body)
    curfewAuditResult.value = res.data || null
    ElMessage.success(res.message || '楼栋批量门禁稽核已完成')
    await loadAll()
  } finally {
    auditingCurfew.value = false
  }
}

const reviewDiscipline = async (row, approved) => {
  const res = await request.post('/affair/discipline-review', {
    id: row.id,
    approved,
    reviewNote: approved ? '经核实科研/请假证明属实，予以核销免扣分' : '证明材料不充分，维持原扣分记录'
  })
  ElMessage.success(res.message || '审核完成')
  await loadAll()
}

const submitDiscipline = async () => {
  submitting.value = true
  try {
    await request.post('/affair/discipline', disciplineForm)
    ElMessage.success('违纪/晚归记录已登记并同步更新文明寝室评分')
    disciplineVisible.value = false
    await loadAll()
  } finally {
    submitting.value = false
  }
}

const submitVisitor = async () => {
  if (!visitorForm.visitorName.trim()) {
    ElMessage.warning('请填写访客姓名')
    return
  }
  const stu = allocatedStudents.value.find(s => s.studentId === visitorForm.visitStudentId)
  if (stu && stu.roomId) {
    const rm = rooms.value.find(r => r.id === stu.roomId)
    if (rm) {
      visitorForm.buildingId = rm.buildingId
      visitorForm.roomNo = rm.roomNo
    }
  }
  submitting.value = true
  try {
    await request.post('/affair/visitor', visitorForm)
    ElMessage.success('访客登记成功，已生成电子通行绿码')
    visitorDialogVisible.value = false
    await loadAll()
  } finally {
    submitting.value = false
  }
}

const submitHygiene = async () => {
  submitting.value = true
  try {
    await request.post('/affair/hygiene', hygieneForm)
    ElMessage.success('卫生评分已保存')
    hygieneVisible.value = false
    await loadAll()
  } finally {
    submitting.value = false
  }
}

const confirmRejectAdjust = async (row) => {
  await ElMessageBox.confirm(`确认驳回学生【${row.studentName}】的申请吗？`, '驳回确认', {
    confirmButtonText: '确认驳回',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await reviewAdjust(row, 'REJECTED')
}

const reviewAdjust = async (row, status) => {
  await request.post('/affair/adjust-review', {
    id: row.id,
    status,
    reviewComment: status === 'APPROVED' ? '同意申请，系统已自动划转床位并重算寝室契合度' : '目标床位暂不可用，予以驳回'
  })
  ElMessage.success('审批完成，床位与寝室契合度已同步更新')
  loadAll()
}

const goGate = () => {
  activeTab.value = 'visitor'
  nextTick(() => {
    const el = document.querySelector('.gate-verify-panel')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

const verifyGate = async (checkOut = false) => {
  if (!gateCode.value.trim()) {
    ElMessage.warning('请输入通行码')
    return
  }
  gateLoading.value = true
  try {
    const res = await request.post('/affair/visitor-verify', { passCode: gateCode.value.trim(), checkOut })
    gateResult.value = res.data || null
    ElMessage.success(res.message || '核验完成')
    if (checkOut) await loadAll()
  } catch (e) {
    gateResult.value = null
  } finally {
    gateLoading.value = false
  }
}

const leaveVisitor = async (id) => {
  await request.post(`/affair/visitor-leave/${id}`)
  ElMessage.success('已核验签离')
  loadAll()
}

const exportCivilizedCsv = () => {
  downloadExport('civilized-rooms', '文明寝室.csv').catch(() => ElMessage.error('导出失败，请检查登录状态'))
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.tab-table {
  margin-top: 12px;
}
.accent-score {
  color: var(--accent-primary);
  font-size: 15px;
}
.star-text {
  color: var(--status-warning);
  margin-right: 8px;
}
.curfew-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px 20px;
  background: var(--bg-canvas);
  border-radius: var(--radius-md);
  margin-bottom: 18px;
}
.curfew-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
}
.cm-item span {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
}
.cm-item b {
  display: block;
  font-size: 18px;
  margin-top: 3px;
  color: var(--text-primary);
}
.cm-item b.text-ok {
  color: var(--status-success);
}
.cm-item b.text-warn {
  color: var(--status-warning);
}
.appeal-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.visitor-row-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}
.visitor-main {
  flex: 1;
  min-width: 0;
}
.pass-code-btn {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  color: #15803d;
  background: var(--status-success-bg);
  border: 1px solid #bbf7d0;
  border-radius: 5px;
  cursor: pointer;
}
.admin-qr-thumb-btn {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  flex-shrink: 0;
}
.admin-mini-qr {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  border: 1.5px solid #bbf7d0;
  fill: #15803d;
  display: block;
}
.admin-mini-qr.expired {
  border-color: var(--border-strong);
  fill: #64748b;
}
.admin-qr-modal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.admin-qr-box {
  padding: 12px;
  border-radius: 16px;
  background: #ffffff;
  border: 3px solid #22c55e;
  box-shadow: 0 8px 24px rgba(21, 128, 61, 0.12);
}
.admin-qr-box.expired {
  border-color: var(--border-strong);
  box-shadow: none;
}
.admin-large-qr {
  width: 196px;
  height: 196px;
  fill: #15803d;
  display: block;
}
.admin-qr-box.expired .admin-large-qr {
  fill: #64748b;
}
.qr-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}
.qr-pass-num {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--text-primary);
}
.admin-qr-meta {
  width: 100%;
  background: var(--bg-canvas);
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.admin-qr-meta div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.admin-qr-meta span {
  color: var(--text-muted);
}
.sub-heading {
  font-size: 14px;
  color: var(--text-primary);
  margin: 15px 0 6px;
}
.col-gap {
  margin-bottom: 12px;
}
.full-width {
  width: 100%;
}
.affair-intro { margin-bottom: 12px; }
.affair-intro h3 { margin: 0; font-size: 16px; font-weight: 600; color: var(--text-primary); }
.affair-intro p { margin: 7px 0 0; font-size: 13px; color: var(--text-muted); }
.affair-list { list-style: none; padding: 0; margin: 0; }
.adjust-row, .compact-row { padding: 18px 2px; border-bottom: 1px solid var(--border-subtle); }
.adjust-row:last-child, .compact-row:last-child { border-bottom: 0; }
.adjust-row { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.adjust-main { min-width: 0; }
.row-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.row-heading strong { font-size: 14px; color: var(--text-primary); }
.adjust-row .row-heading { justify-content: flex-start; }
.row-meta, .row-note { margin: 6px 0 0; color: var(--text-muted); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.row-note { color: var(--text-secondary); font-size: 13px; }
.room-route { margin: 12px 0 0; font-size: 14px; line-height: 1.6; color: var(--text-primary); overflow-wrap: anywhere; }
.room-route span { margin: 0 6px; color: var(--text-muted); }
.record-status { flex: 0 0 auto; color: var(--text-muted); font-size: 12px; font-weight: 500; }
.record-status.PENDING, .record-status.warning { color: #a96616; }
.record-status.APPROVED { color: #32805b; }
.record-status.REJECTED { color: #ba4e4a; }
.relation { color: var(--text-muted); font-size: 12px; font-weight: 400; margin-left: 4px; }
.review-actions { display: flex; flex: 0 0 auto; }
@media (max-width: 600px) { .adjust-row { align-items: flex-start; flex-direction: column; gap: 12px; } }

.score-list { list-style: none; padding: 0; margin: 12px 0 0; }
.score-row, .hygiene-row { display: grid; align-items: center; gap: 18px; padding: 18px 2px; border-bottom: 1px solid var(--border-subtle); }
.score-row:last-child, .hygiene-row:last-child { border-bottom: 0; }
.score-row { grid-template-columns: minmax(190px, 1.2fr) minmax(245px, 1.25fr) 95px minmax(110px, .75fr); }
.score-identity { display: flex; align-items: center; gap: 12px; min-width: 0; }
.score-rank { display: grid; place-items: center; flex: 0 0 38px; height: 38px; border-radius: 12px; background: var(--bg-canvas); color: var(--text-secondary); font-size: 13px; font-weight: 600; }
.score-identity > div, .hygiene-identity, .score-result, .score-honor { display: grid; gap: 5px; min-width: 0; }
.score-identity strong, .hygiene-identity strong { font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
.score-identity small, .hygiene-identity small, .score-result > span, .score-breakdown dt, .hygiene-breakdown dt { color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.score-breakdown, .hygiene-breakdown { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 0; }
.score-breakdown dd, .hygiene-breakdown dd { margin: 4px 0 0; font-size: 14px; color: var(--text-primary); }
.score-result strong { color: var(--accent-primary); font-size: 17px; line-height: 1.3; }
.score-result strong small { font-size: 12px; font-weight: 400; }
.score-honor { font-size: 12px; color: var(--text-secondary); overflow-wrap: anywhere; }
.score-honor .star-text { white-space: nowrap; margin: 0; letter-spacing: 1px; }
.hygiene-row { grid-template-columns: minmax(160px, 1fr) minmax(230px, 1.25fr) 90px minmax(150px, .9fr); }
.hygiene-breakdown { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.hygiene-remark { color: var(--text-secondary); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
@media (max-width: 1100px) {
  .score-row, .hygiene-row { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px 18px; }
}
@media (max-width: 600px) {
  .score-row, .hygiene-row { grid-template-columns: minmax(0, 1fr) auto; gap: 14px 12px; }
  .score-identity, .hygiene-identity, .score-breakdown, .hygiene-breakdown, .hygiene-remark { grid-column: 1 / -1; }
  .score-result, .score-honor { align-self: start; }
}

.todo-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 8px; }
.todo-card { border: 1px solid var(--border-subtle); background: var(--bg-canvas); border-radius: 14px; padding: 18px 16px; text-align: left; cursor: pointer; }
.todo-card strong { display: block; font-size: 28px; letter-spacing: -.6px; }
.todo-card span { display: block; margin-top: 6px; color: var(--text-muted); font-size: 12px; }
.todo-card.warn strong { color: var(--status-danger); }
.gate-verify-panel { margin: 8px 0 18px; padding: 16px 16px 14px; border-radius: 14px; background: linear-gradient(180deg, #f3fbf5, #f7f7f8); border: 1px solid #d7efdd; }
.gate-heading h3 { font-size: 16px; font-weight: 600; }
.gate-heading p { margin-top: 4px; color: var(--text-muted); font-size: 12px; }
.gate-form { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; align-items: center; }
.gate-input { flex: 1; min-width: 220px; }
.gate-pass-card { margin-top: 14px; padding: 16px; border-radius: 16px; background: #fff; border: 2px solid #cfead6; }
.gate-pass-card.ok { border-color: #34c759; box-shadow: 0 0 0 4px rgb(52 199 89 / 12%); }
.gate-pass-card.warn { border-color: #ff9f0a; }
.gate-pass-card.done { border-color: #c7c7cc; opacity: .92; }
.gate-pass-badge { display: inline-flex; padding: 4px 10px; border-radius: 999px; background: #34c759; color: #fff; font-size: 12px; font-weight: 600; }
.gate-pass-card.warn .gate-pass-badge { background: #ff9f0a; }
.gate-pass-card.done .gate-pass-badge { background: #8e8e93; }
.gate-pass-code { margin: 10px 0 12px; font-size: 28px; font-weight: 700; letter-spacing: 1px; }
.gate-pass-card dl { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; margin: 0; }
.gate-pass-card dt { font-size: 11px; color: var(--text-muted); }
.gate-pass-card dd { margin: 2px 0 0; font-size: 13px; }
.text-warn { color: var(--status-warning); }
@media (max-width: 900px) { .todo-grid { grid-template-columns: 1fr 1fr; } .gate-pass-card dl { grid-template-columns: 1fr; } }
</style>
