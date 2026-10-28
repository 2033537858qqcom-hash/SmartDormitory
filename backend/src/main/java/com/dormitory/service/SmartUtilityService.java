package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.DormRoom;
import com.dormitory.entity.UtilityBill;
import com.dormitory.mapper.DormRoomMapper;
import com.dormitory.mapper.UtilityBillMapper;
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
        DormRoom room = roomMapper.selectById(bill.getRoomId());
        if (room != null) {
            bill.setBuildingId(room.getBuildingId());
        }

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

        if (bill.getPayStatus() == null) {
            bill.setPayStatus(0);
        }
        bill.setCreateTime(LocalDateTime.now());

        UtilityBill existing = utilityBillMapper.selectOne(
                new LambdaQueryWrapper<UtilityBill>()
                        .eq(UtilityBill::getBillMonth, bill.getBillMonth())
                        .eq(UtilityBill::getRoomId, bill.getRoomId())
        );
        if (existing != null) {
            bill.setId(existing.getId());
            utilityBillMapper.updateById(bill);
        } else {
            utilityBillMapper.insert(bill);
        }
        return bill;
    }

    /**
     * 学生在线缴纳水电账单（联动抵扣寝室水电账户余额）或充值水电余额
     */
    @Transactional(rollbackFor = Exception.class)
    public DormRoom payBillOrRecharge(Long roomId, Long billId, BigDecimal electricRecharge, BigDecimal waterRecharge) {
        DormRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RuntimeException("未找到对应寝室");
        }
        if (electricRecharge != null && electricRecharge.compareTo(BigDecimal.ZERO) > 0) {
            room.setElectricBalance(room.getElectricBalance().add(electricRecharge));
        }
        if (waterRecharge != null && waterRecharge.compareTo(BigDecimal.ZERO) > 0) {
            room.setWaterBalance(room.getWaterBalance().add(waterRecharge));
        }
        if (billId != null) {
            UtilityBill bill = utilityBillMapper.selectById(billId);
            if (bill != null && bill.getPayStatus() == 0) {
                BigDecimal eCost = bill.getElectricCost() != null ? bill.getElectricCost() : BigDecimal.ZERO;
                BigDecimal wCost = bill.getWaterCost() != null ? bill.getWaterCost() : BigDecimal.ZERO;
                // 优先从寝室电费、水费余额账户中抵扣，不足部分视为在线直缴补齐
                BigDecimal nextElec = room.getElectricBalance().subtract(eCost);
                BigDecimal nextWater = room.getWaterBalance().subtract(wCost);
                room.setElectricBalance(nextElec.compareTo(BigDecimal.ZERO) > 0 ? nextElec : BigDecimal.ZERO);
                room.setWaterBalance(nextWater.compareTo(BigDecimal.ZERO) > 0 ? nextWater : BigDecimal.ZERO);

                bill.setPayStatus(1);
                bill.setPayTime(LocalDateTime.now());
                utilityBillMapper.updateById(bill);
            }
        }
        roomMapper.updateById(room);
        return room;
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
}
