package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.DormRoom;
import com.dormitory.entity.UtilityBill;
import com.dormitory.entity.UtilityPaymentLog;
import com.dormitory.mapper.DormRoomMapper;
import com.dormitory.mapper.UtilityBillMapper;
import com.dormitory.mapper.UtilityPaymentLogMapper;
import com.dormitory.common.AlipayConfig;
import com.alipay.easysdk.factory.Factory;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 核心亮点三：水电阶梯计价核算与能耗突增异常预警引擎
 */
@Service
@RequiredArgsConstructor
public class SmartUtilityService {

    private final UtilityBillMapper utilityBillMapper;
    private final DormRoomMapper roomMapper;
    private final UtilityPaymentLogMapper paymentLogMapper;
    private final AlipayConfig alipayConfig;

    @org.springframework.beans.factory.annotation.Value("${app.demo-enabled:false}")
    private boolean demoEnabled;

    public boolean isDemoEnabled() { return demoEnabled; }

    /**
     * 阶梯电价核算规则：
     * 每月每寝室赠送基础免额 20 度；
     * 第一阶梯 (20~100度部分)：0.60 元/度；
     * 第二阶梯 (超出100度部分)：0.85 元/度
     */
    public BigDecimal calculateTieredElectricCost(BigDecimal electricUsed) {
        double used = electricUsed != null ? electricUsed.doubleValue() : 0.0;
        if (used <= 20.0) {
            return BigDecimal.ZERO;
        }
        double cost;
        if (used <= 100.0) {
            cost = (used - 20.0) * 0.60;
        } else {
            cost = (100.0 - 20.0) * 0.60 + (used - 100.0) * 0.85;
        }
        return BigDecimal.valueOf(cost).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 阶梯水价核算规则：
     * 每月每寝室赠送基础免额 4 吨；
     * 第一阶梯 (4~15吨部分)：3.00 元/吨；
     * 第二阶梯 (超出15吨部分)：4.50 元/吨
     */
    public BigDecimal calculateTieredWaterCost(BigDecimal waterUsed) {
        double used = waterUsed != null ? waterUsed.doubleValue() : 0.0;
        if (used <= 4.0) {
            return BigDecimal.ZERO;
        }
        double cost;
        if (used <= 15.0) {
            cost = (used - 4.0) * 3.00;
        } else {
            cost = (15.0 - 4.0) * 3.00 + (used - 15.0) * 4.50;
        }
        return BigDecimal.valueOf(cost).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 生成/录入抄表账单并自动触发【阶梯计价 + 能耗突增异常检测】
     */
    @Transactional(rollbackFor = Exception.class)
    public UtilityBill createOrUpdateMeterBill(UtilityBill bill) {
        DormRoom room = roomMapper.selectOne(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getId, bill.getRoomId()).last("FOR UPDATE"));
        if (room == null) throw new IllegalArgumentException("寝室不存在");
        bill.setBuildingId(room.getBuildingId());

        BigDecimal elecCost = calculateTieredElectricCost(bill.getElectricUsed());
        BigDecimal waterCost = calculateTieredWaterCost(bill.getWaterUsed());
        BigDecimal total = elecCost.add(waterCost);

        bill.setElectricCost(elecCost);
        bill.setWaterCost(waterCost);
        bill.setTotalCost(total);

        // 智能能耗异常检测：对比同楼栋历史均值与安全阈值
        List<UtilityBill> buildingBills = utilityBillMapper.selectList(
                new LambdaQueryWrapper<UtilityBill>().eq(UtilityBill::getBuildingId, bill.getBuildingId())
        );
        double avgElec = buildingBills.stream()
                .mapToDouble(b -> b.getElectricUsed().doubleValue())
                .average().orElse(75.0);
        double avgWater = buildingBills.stream()
                .mapToDouble(b -> b.getWaterUsed().doubleValue())
                .average().orElse(11.0);

        double curElec = bill.getElectricUsed().doubleValue();
        double curWater = bill.getWaterUsed().doubleValue();

        StringBuilder warn = new StringBuilder();
        if (curElec > Math.max(145.0, avgElec * 1.5)) {
            warn.append(String.format("用电量(%.1fkWh)显著超出楼栋均值(%.1fkWh)，疑似违规使用大功率电器或空调长开；", curElec, avgElec));
        }
        if (curWater > Math.max(18.0, avgWater * 1.6)) {
            warn.append(String.format("用水量(%.1f吨)超出楼栋均值(%.1f吨)，请排查马桶水箱或水龙头是否暗漏！", curWater, avgWater));
        }

        if (warn.length() > 0) {
            bill.setAbnormalFlag(1);
            bill.setAbnormalReason(warn.toString());
        } else {
            bill.setAbnormalFlag(0);
            bill.setAbnormalReason(null);
        }

        bill.setPayStatus(0);
        bill.setPayTime(null);
        bill.setCreateTime(LocalDateTime.now());

        UtilityBill existing = utilityBillMapper.selectOne(
                new LambdaQueryWrapper<UtilityBill>()
                        .eq(UtilityBill::getBillMonth, bill.getBillMonth())
                        .eq(UtilityBill::getRoomId, bill.getRoomId())
        );
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getPayStatus())) throw new IllegalArgumentException("已缴账单不可重新抄表覆盖");
            bill.setId(existing.getId());
            utilityBillMapper.updateById(bill);
        } else {
            utilityBillMapper.insert(bill);
        }
        return bill;
    }

    /** 演示支付：同一请求号可安全重试，余额、账单和流水在同一事务内提交。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processPayment(Long roomId, Long billId, BigDecimal electricRecharge,
                                               BigDecimal waterRecharge, Long operatorId, String requestId) {
        if (!demoEnabled) throw new IllegalStateException("演示水电核销未启用");
        BigDecimal electric = electricRecharge == null ? BigDecimal.ZERO : electricRecharge;
        BigDecimal water = waterRecharge == null ? BigDecimal.ZERO : waterRecharge;
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{16,30}")) throw new IllegalArgumentException("请提供有效的请求编号");
        if (electric.signum() < 0 || water.signum() < 0 || electric.scale() > 2 || water.scale() > 2)
            throw new IllegalArgumentException("充值金额必须为非负数且最多两位小数");
        if (billId == null && electric.signum() == 0 && water.signum() == 0) throw new IllegalArgumentException("请输入充值金额");
        if (billId != null && (electric.signum() != 0 || water.signum() != 0)) throw new IllegalArgumentException("缴费与充值请分开提交");
        String txnNo = "DEMO-" + requestId;
        String fingerprint = DigestUtil.sha256Hex(roomId + ":" + billId + ":" + operatorId + ":"
                + electric.stripTrailingZeros().toPlainString() + ":" + water.stripTrailingZeros().toPlainString());
        DormRoom room = roomMapper.selectOne(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getId, roomId).last("FOR UPDATE"));
        if (room == null) throw new IllegalArgumentException("未找到对应寝室");
        UtilityPaymentLog previous = paymentLogMapper.selectOne(new LambdaQueryWrapper<UtilityPaymentLog>()
                .eq(UtilityPaymentLog::getTxnNo, txnNo));
        if (previous != null) {
            if (!Objects.equals(previous.getRemark(), fingerprint)) throw new IllegalArgumentException("请求编号已用于其他操作");
            return paymentResult(room, previous);
        }
        BigDecimal eBefore = room.getElectricBalance() == null ? BigDecimal.ZERO : room.getElectricBalance();
        BigDecimal wBefore = room.getWaterBalance() == null ? BigDecimal.ZERO : room.getWaterBalance();
        BigDecimal eAfter = eBefore.add(electric);
        BigDecimal wAfter = wBefore.add(water);
        if (billId != null) {
            UtilityBill bill = utilityBillMapper.selectById(billId);
            if (bill == null || !Objects.equals(roomId, bill.getRoomId())) throw new IllegalArgumentException("账单不属于当前寝室");
            if (!Integer.valueOf(0).equals(bill.getPayStatus())) throw new IllegalArgumentException("账单已缴纳或状态无效");
            BigDecimal elecCost = bill.getElectricCost() == null ? BigDecimal.ZERO : bill.getElectricCost();
            BigDecimal waterCost = bill.getWaterCost() == null ? BigDecimal.ZERO : bill.getWaterCost();
            if (eBefore.compareTo(elecCost) < 0 || wBefore.compareTo(waterCost) < 0) {
                throw new IllegalArgumentException(String.format("寝室预存余额不足（电费需 %.2f 元，当前 %.2f 元；水费需 %.2f 元，当前 %.2f 元），请先充值后再进行账单抵扣", elecCost, eBefore, waterCost, wBefore));
            }
            eAfter = eBefore.subtract(elecCost);
            wAfter = wBefore.subtract(waterCost);
            bill.setPayStatus(1);
            bill.setPayTime(LocalDateTime.now());
            if (utilityBillMapper.updateById(bill) != 1) throw new IllegalStateException("账单更新失败");
        }
        room.setElectricBalance(eAfter);
        room.setWaterBalance(wAfter);
        if (roomMapper.updateById(room) != 1) throw new IllegalStateException("账户更新失败");
        UtilityPaymentLog log = new UtilityPaymentLog();
        log.setTxnNo(txnNo); log.setRoomId(roomId); log.setBillId(billId); log.setOperatorId(operatorId);
        log.setActionType(billId == null ? "RECHARGE" : "PAY_BILL");
        log.setElectricBefore(eBefore); log.setWaterBefore(wBefore); log.setElectricAfter(eAfter); log.setWaterAfter(wAfter);
        log.setElectricDelta(eAfter.subtract(eBefore)); log.setWaterDelta(wAfter.subtract(wBefore));
        log.setChannel("DEMO"); log.setRemark(fingerprint); log.setCreateTime(LocalDateTime.now());
        paymentLogMapper.insert(log);
        return paymentResult(room, log);
    }

    private Map<String, Object> paymentResult(DormRoom room, UtilityPaymentLog log) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("room", room); result.put("txnNo", log.getTxnNo()); result.put("demoSimulated", true);
        result.put("channel", "DEMO"); result.put("actionType", log.getActionType());
        result.put("electricBefore", log.getElectricBefore()); result.put("waterBefore", log.getWaterBefore());
        result.put("electricAfter", log.getElectricAfter()); result.put("waterAfter", log.getWaterAfter());
        result.put("notice", "演示模拟：本流水仅本地核销，未对接微信支付或校园卡网关");
        return result;
    }

    /** 前端展示用的真实阈值规则（替代演示作弊提示） */
    public Map<String, Object> thresholdRules() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("electricFreeKwh", 20);
        m.put("electricTier1MaxKwh", 100);
        m.put("electricTier1Price", 0.60);
        m.put("electricTier2Price", 0.85);
        m.put("waterFreeTon", 4);
        m.put("waterTier1MaxTon", 15);
        m.put("waterTier1Price", 3.00);
        m.put("waterTier2Price", 4.50);
        m.put("electricAbnormalFloorKwh", 145);
        m.put("electricAbnormalAvgFactor", 1.5);
        m.put("waterAbnormalFloorTon", 18);
        m.put("waterAbnormalAvgFactor", 1.6);
        m.put("ruleText", "用电：超过 max(145kWh, 楼栋均值×1.5) 标记异常；用水：超过 max(18吨, 楼栋均值×1.6) 标记异常。");
        return m;
    }

    public boolean isAlipayEnabled() {
        return alipayConfig != null && alipayConfig.isEnabled();
    }

    /**
     * 支付宝沙箱统一下单（调用 Alipay EasySDK 插件）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createAlipayOrder(Long roomId, Long billId, BigDecimal electricRecharge,
                                                 BigDecimal waterRecharge, Long operatorId) {
        DormRoom room = roomMapper.selectById(roomId);
        if (room == null) throw new IllegalArgumentException("寝室不存在");

        BigDecimal totalAmount;
        String subject;
        String actionType;
        BigDecimal eDelta = electricRecharge != null ? electricRecharge : BigDecimal.ZERO;
        BigDecimal wDelta = waterRecharge != null ? waterRecharge : BigDecimal.ZERO;

        if (eDelta.signum() < 0 || wDelta.signum() < 0) throw new IllegalArgumentException("充值金额不能为负数");
        if (billId != null && (eDelta.signum() != 0 || wDelta.signum() != 0)) throw new IllegalArgumentException("缴费不能同时充值");

        if (billId != null) {
            UtilityBill bill = utilityBillMapper.selectById(billId);
            if (bill == null || !Objects.equals(roomId, bill.getRoomId())) throw new IllegalArgumentException("账单不属于当前寝室");
            if (!Integer.valueOf(0).equals(bill.getPayStatus())) throw new IllegalArgumentException("账单已缴纳或状态无效");
            totalAmount = bill.getTotalCost() != null ? bill.getTotalCost() : BigDecimal.ZERO;
            subject = "宿舍 " + room.getRoomNo() + " 室 " + bill.getBillMonth() + " 水电费缴纳";
            actionType = "PAY_BILL";
        } else {
            if (eDelta.signum() <= 0 && wDelta.signum() <= 0) throw new IllegalArgumentException("充值金额必须大于零");
            totalAmount = eDelta.add(wDelta);
            subject = "宿舍 " + room.getRoomNo() + " 室水电预存充值";
            actionType = "RECHARGE";
        }

        if (!isAlipayEnabled()) throw new IllegalStateException("支付宝沙箱未配置，请选择可用的支付方式");

        String outTradeNo = "ALI-" + System.currentTimeMillis() + "-" + RandomUtil.randomNumbers(4);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("outTradeNo", outTradeNo);
        res.put("totalAmount", totalAmount);
        res.put("subject", subject);
        res.put("actionType", actionType);
        res.put("alipayEnabled", alipayConfig != null && alipayConfig.isEnabled());

        if (alipayConfig != null && alipayConfig.isEnabled()) {
            try {
                String returnUrl = alipayConfig.getReturnUrl() != null ? alipayConfig.getReturnUrl() : "http://localhost:5174";
                var payResponse = Factory.Payment.Page().pay(subject, outTradeNo, totalAmount.setScale(2, RoundingMode.HALF_UP).toString(), returnUrl);
                res.put("formHtml", payResponse.getBody());
                res.put("mode", "SDK_PAGE");
            } catch (Exception e) {
                throw new IllegalStateException("支付宝沙箱统一下单失败：" + e.getMessage(), e);
            }
        } else {
            res.put("formHtml", null);
            res.put("mode", "DEMO_FALLBACK");
            res.put("tip", "支付宝沙箱插件已就绪（当前为演示模拟通道；配置沙箱密钥后即可无缝切换真实沙箱付款）");
        }

        UtilityPaymentLog log = new UtilityPaymentLog();
        log.setTxnNo(outTradeNo);
        log.setRoomId(roomId);
        log.setBillId(billId);
        log.setOperatorId(operatorId);
        log.setActionType(actionType);
        log.setElectricDelta(eDelta);
        log.setWaterDelta(wDelta);
        log.setElectricBefore(room.getElectricBalance());
        log.setWaterBefore(room.getWaterBalance());
        log.setChannel(alipayConfig != null && alipayConfig.isEnabled() ? "ALIPAY_PENDING" : "DEMO_PENDING");
        log.setRemark((billId != null ? ("BILL:" + billId) : ("RECHARGE:" + eDelta + ":" + wDelta))
                + "|AMOUNT=" + totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        log.setCreateTime(LocalDateTime.now());
        paymentLogMapper.insert(log);

        return res;
    }

    /**
     * 处理支付宝异步回调通知 (WebHook 自动验签与订单核销)
     */
    @Transactional(rollbackFor = Exception.class)
    public String handleAlipayNotify(Map<String, String> params) {
        if (alipayConfig == null || !alipayConfig.isEnabled()) {
            return "failure";
        }
        try {
            if (!Boolean.TRUE.equals(Factory.Payment.Common().verifyNotify(params))) return "failure";
        } catch (Exception e) {
            return "failure";
        }
        if (!Objects.equals(alipayConfig.getAppId(), params.get("app_id"))) return "failure";

            String tradeStatus = params.get("trade_status");
            String outTradeNo = params.get("out_trade_no");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return "success";
            }
            UtilityPaymentLog log = paymentLogMapper.selectOne(new LambdaQueryWrapper<UtilityPaymentLog>()
                    .eq(UtilityPaymentLog::getTxnNo, outTradeNo).last("FOR UPDATE"));
            if (log == null) {
                return "failure";
            }
            String remark = log.getRemark();
            int amountMarker = remark == null ? -1 : remark.lastIndexOf("|AMOUNT=");
            if (amountMarker < 0 || params.get("total_amount") == null) return "failure";
            try {
                BigDecimal expected = new BigDecimal(remark.substring(amountMarker + 8));
                if (expected.signum() <= 0 || expected.compareTo(new BigDecimal(params.get("total_amount"))) != 0) return "failure";
            } catch (NumberFormatException ex) {
                return "failure";
            }
            if (!"ALIPAY_PENDING".equals(log.getChannel()) && !"ALIPAY".equals(log.getChannel())) return "failure";
            if ("ALIPAY".equals(log.getChannel())) {
                return "success"; // 已处理，幂等返回
            }

            DormRoom room = roomMapper.selectOne(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getId, log.getRoomId()).last("FOR UPDATE"));
            if (room == null) return "failure";

            BigDecimal eBefore = room.getElectricBalance() != null ? room.getElectricBalance() : BigDecimal.ZERO;
            BigDecimal wBefore = room.getWaterBalance() != null ? room.getWaterBalance() : BigDecimal.ZERO;

            if ("PAY_BILL".equals(log.getActionType()) && log.getBillId() != null) {
                UtilityBill bill = utilityBillMapper.selectById(log.getBillId());
                if (bill == null || !Objects.equals(bill.getRoomId(), log.getRoomId()) || !Integer.valueOf(0).equals(bill.getPayStatus())) return "failure";
                bill.setPayStatus(1);
                bill.setPayTime(LocalDateTime.now());
                utilityBillMapper.updateById(bill);
                log.setElectricAfter(eBefore);
                log.setWaterAfter(wBefore);
            } else {
                BigDecimal eAfter = eBefore.add(log.getElectricDelta() != null ? log.getElectricDelta() : BigDecimal.ZERO);
                BigDecimal wAfter = wBefore.add(log.getWaterDelta() != null ? log.getWaterDelta() : BigDecimal.ZERO);
                room.setElectricBalance(eAfter);
                room.setWaterBalance(wAfter);
                roomMapper.updateById(room);
                log.setElectricAfter(eAfter);
                log.setWaterAfter(wAfter);
            }
            log.setChannel("ALIPAY");
            paymentLogMapper.updateById(log);
            return "success";
    }
}
