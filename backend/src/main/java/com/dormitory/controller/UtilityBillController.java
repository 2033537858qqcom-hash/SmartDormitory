package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartUtilityService;
import com.dormitory.service.SmartRepairService;
import com.dormitory.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityBillController {

    private final UtilityBillMapper utilityBillMapper;
    private final SmartUtilityService smartUtilityService;
    private final SmartRepairService smartRepairService;
    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final DormBedMapper bedMapper;
    private final BuildingScopeService buildingScope;
    private final UserMessageService messageService;
    private final UtilityPaymentLogMapper paymentLogMapper;

    @GetMapping("/bills")
    public Result<List<Map<String, Object>>> listBills(@RequestParam(required = false) Long buildingId,
                                                       @RequestParam(required = false) Long roomId,
                                                       @RequestParam(required = false) String billMonth,
                                                       @RequestParam(required = false) Integer abnormalFlag,
                                                       HttpServletRequest request) {
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null) {
            if (scope.isEmpty()) {
                return Result.success(List.of());
            }
            if (buildingId != null && !scope.contains(buildingId)) {
                return Result.error(403, "只能查看管辖楼栋的账单");
            }
            if (buildingId == null) {
                // 强制限制在管辖楼栋
            }
        }
        LambdaQueryWrapper<UtilityBill> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(UtilityBill::getBuildingId, scope);
        }
        if (buildingId != null) wrapper.eq(UtilityBill::getBuildingId, buildingId);
        if (roomId != null) wrapper.eq(UtilityBill::getRoomId, roomId);
        if (billMonth != null && !billMonth.isEmpty()) wrapper.eq(UtilityBill::getBillMonth, billMonth);
        if (abnormalFlag != null) wrapper.eq(UtilityBill::getAbnormalFlag, abnormalFlag);
        wrapper.orderByDesc(UtilityBill::getBillMonth).orderByDesc(UtilityBill::getAbnormalFlag);

        List<UtilityBill> bills = utilityBillMapper.selectList(wrapper);
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream().collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream().collect(Collectors.toMap(DormBuilding::getId, b -> b));

        List<Map<String, Object>> result = new ArrayList<>();
        for (UtilityBill b : bills) {
            DormRoom r = roomMap.get(b.getRoomId());
            DormBuilding bd = buildingMap.get(b.getBuildingId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", b.getId());
            item.put("billMonth", b.getBillMonth());
            item.put("buildingId", b.getBuildingId());
            item.put("buildingName", bd != null ? bd.getBuildingName() : "");
            item.put("roomId", b.getRoomId());
            item.put("roomNo", r != null ? r.getRoomNo() : "");
            item.put("occupiedCount", r != null ? r.getOccupiedCount() : 4);
            item.put("electricUsed", b.getElectricUsed());
            item.put("electricCost", b.getElectricCost());
            item.put("waterUsed", b.getWaterUsed());
            item.put("waterCost", b.getWaterCost());
            item.put("totalCost", b.getTotalCost());
            int occ = (r != null && r.getOccupiedCount() != null && r.getOccupiedCount() > 0) ? r.getOccupiedCount() : 4;
            item.put("perCapitaCost", b.getTotalCost().divide(BigDecimal.valueOf(occ), 2, java.math.RoundingMode.HALF_UP));
            item.put("roomElectricBalance", r != null ? r.getElectricBalance() : BigDecimal.ZERO);
            item.put("roomWaterBalance", r != null ? r.getWaterBalance() : BigDecimal.ZERO);
            boolean lowBal = r != null && (r.getElectricBalance().doubleValue() < 30.0 || r.getWaterBalance().doubleValue() < 15.0);
            item.put("lowBalanceFlag", lowBal ? 1 : 0);
            item.put("abnormalFlag", b.getAbnormalFlag());
            item.put("abnormalReason", b.getAbnormalReason());
            item.put("anomalyStatus", b.getAnomalyStatus() == null ? "PENDING" : b.getAnomalyStatus());
            item.put("anomalyNote", b.getAnomalyNote());
            item.put("anomalyRepairId", b.getAnomalyRepairId());
            item.put("anomalyUpdatedAt", b.getAnomalyUpdatedAt());
            item.put("payStatus", b.getPayStatus());
            item.put("payTime", b.getPayTime());
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/meter-entry")
    public Result<UtilityBill> saveMeterReading(@RequestBody UtilityBill bill, HttpServletRequest request) {
        DormRoom room = bill.getRoomId() == null ? null : roomMapper.selectById(bill.getRoomId());
        if (room == null) return Result.error("寝室不存在");
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), room.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<UtilityBill> typed = (Result<UtilityBill>) denied;
            return typed;
        }
        bill.setBuildingId(room.getBuildingId());
        UtilityBill saved = smartUtilityService.createOrUpdateMeterBill(bill);
        String msg = saved.getAbnormalFlag() != null && saved.getAbnormalFlag() == 1
                ? "抄表核算完成！⚠️触发智能能耗突增异常预警：" + saved.getAbnormalReason()
                : "抄表录入与阶梯计价核算完成！";
        return Result.success(msg, saved);
    }

    @PostMapping("/pay-or-recharge")
    public Result<Map<String, Object>> payOrRecharge(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long roomId = Long.valueOf(body.get("roomId").toString());
        Long billId = body.get("billId") != null ? Long.valueOf(body.get("billId").toString()) : null;
        BigDecimal electricRecharge = body.get("electricRecharge") != null
                ? new BigDecimal(body.get("electricRecharge").toString()) : BigDecimal.ZERO;
        BigDecimal waterRecharge = body.get("waterRecharge") != null
                ? new BigDecimal(body.get("waterRecharge").toString()) : BigDecimal.ZERO;

        DormRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            return Result.error("寝室不存在");
        }
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            Long uid = (Long) request.getAttribute("currentUserId");
            DormBed bed = bedMapper.selectOne(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getStudentId, uid)
                            .eq(DormBed::getRoomId, roomId)
                            .eq(DormBed::getStatus, 1)
                            .last("LIMIT 1")
            );
            if (bed == null) {
                return Result.error(403, "只能为自己入住的寝室缴费或充值");
            }
        } else {
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), room.getBuildingId());
            if (denied != null) {
                @SuppressWarnings("unchecked")
                Result<Map<String, Object>> typed = (Result<Map<String, Object>>) denied;
                return typed;
            }
        }

        try {
            Map<String, Object> payload = smartUtilityService.processPayment(roomId, billId, electricRecharge,
                    waterRecharge, (Long) request.getAttribute("currentUserId"), (String) body.get("requestId"));
            return Result.success("【演示模拟】缴费/充值已核销（非真实支付），流水号 " + payload.get("txnNo"), payload);
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @GetMapping("/threshold-rules")
    public Result<Map<String, Object>> thresholdRules() {
        return Result.success(smartUtilityService.thresholdRules());
    }

    @GetMapping("/payment-logs")
    public Result<List<UtilityPaymentLog>> paymentHistory(@RequestParam Long roomId, HttpServletRequest request) {
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            DormBed bed = bedMapper.selectOne(new LambdaQueryWrapper<DormBed>()
                    .eq(DormBed::getStudentId, request.getAttribute("currentUserId"))
                    .eq(DormBed::getRoomId, roomId).eq(DormBed::getStatus, 1).last("LIMIT 1"));
            if (bed == null) return Result.error(403, "只能查看自己寝室的缴费流水");
        } else {
            DormRoom room = roomMapper.selectById(roomId);
            if (room == null) return Result.error("寝室不存在");
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), room.getBuildingId());
            if (denied != null) return Result.error(403, denied.getMessage());
        }
        List<UtilityPaymentLog> history = paymentLogMapper.selectList(new LambdaQueryWrapper<UtilityPaymentLog>()
                .eq(UtilityPaymentLog::getRoomId, roomId).orderByDesc(UtilityPaymentLog::getCreateTime).last("LIMIT 100"));
        history.forEach(log -> log.setRemark(null));
        return Result.success(history);
    }

    @PostMapping("/meter-batch")
    public Result<Map<String, Object>> batchMeter(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String billMonth = body.get("billMonth") != null ? body.get("billMonth").toString() : null;
        if (billMonth == null || billMonth.isBlank()) {
            return Result.error("请指定账单月份，如 2026-10");
        }
        Object entriesObj = body.get("entries");
        if (!(entriesObj instanceof List<?> entries) || entries.isEmpty()) {
            return Result.error("请提供至少一条抄表记录");
        }
        Set<Long> scope = buildingScope.resolve(request);
        List<UtilityBill> saved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int abnormal = 0;
        for (Object raw : entries) {
            if (!(raw instanceof Map<?, ?> em)) {
                errors.add("条目格式无效");
                continue;
            }
            try {
                Long roomId = Long.valueOf(em.get("roomId").toString());
                DormRoom room = roomMapper.selectById(roomId);
                if (room == null) {
                    errors.add("房间不存在:" + roomId);
                    continue;
                }
                Result<?> denied = buildingScope.forbiddenIfOutOfScope(scope, room.getBuildingId());
                if (denied != null) {
                    errors.add("无权限房间:" + roomId);
                    continue;
                }
                UtilityBill bill = new UtilityBill();
                bill.setBillMonth(billMonth);
                bill.setRoomId(roomId);
                bill.setBuildingId(room.getBuildingId());
                bill.setElectricUsed(new BigDecimal(em.get("electricUsed").toString()));
                bill.setWaterUsed(new BigDecimal(em.get("waterUsed").toString()));
                UtilityBill out = smartUtilityService.createOrUpdateMeterBill(bill);
                saved.add(out);
                if (out.getAbnormalFlag() != null && out.getAbnormalFlag() == 1) abnormal++;
            } catch (Exception ex) {
                errors.add(ex.getMessage() != null ? ex.getMessage() : "条目失败");
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("savedCount", saved.size());
        res.put("abnormalCount", abnormal);
        res.put("errors", errors);
        res.put("bills", saved);
        return Result.success("批量抄表完成：成功 " + saved.size() + " 条，异常 " + abnormal + " 条", res);
    }

    @PostMapping("/anomaly-to-repair")
    @Transactional(rollbackFor = Exception.class)
    public Result<RepairOrder> anomalyToRepair(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long billId = Long.valueOf(body.get("billId").toString());
        UtilityBill bill = utilityBillMapper.selectOne(new LambdaQueryWrapper<UtilityBill>()
                .eq(UtilityBill::getId, billId).last("FOR UPDATE"));
        if (bill == null) return Result.error("账单不存在");
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), bill.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<RepairOrder> typed = (Result<RepairOrder>) denied;
            return typed;
        }
        if (bill.getAbnormalFlag() == null || bill.getAbnormalFlag() != 1) {
            return Result.error("该账单未标记异常，无需转报修");
        }
        if (bill.getAnomalyRepairId() != null) return Result.error("该异常已转报修，请查看原工单");
        if ("RESOLVED".equals(bill.getAnomalyStatus())) return Result.error("该异常已解决，无需转报修");
        DormRoom room = roomMapper.selectById(bill.getRoomId());
        // 取寝室任一在住学生作为报修人
        DormBed bed = bedMapper.selectOne(new LambdaQueryWrapper<DormBed>()
                .eq(DormBed::getRoomId, bill.getRoomId())
                .eq(DormBed::getStatus, 1)
                .last("LIMIT 1"));
        if (bed == null || bed.getStudentId() == null) {
            return Result.error("该寝室暂无在住学生，无法创建报修工单");
        }
        String reason = bill.getAbnormalReason() != null ? bill.getAbnormalReason() : "能耗异常";
        boolean water = reason.contains("用水") || reason.contains("漏水");
        RepairOrder order = new RepairOrder();
        order.setStudentId(bed.getStudentId());
        order.setBuildingId(bill.getBuildingId());
        order.setRoomId(bill.getRoomId());
        order.setRepairType(water ? "WATER" : "ELECTRIC");
        order.setTitle(water ? "水电异常排查（疑似漏水）" : "水电异常排查（疑似大功率/长开）");
        order.setDescription("由" + bill.getBillMonth() + "月异常账单自动转单：" + reason
                + "（房间 " + (room != null ? room.getRoomNo() : bill.getRoomId()) + "）");
        RepairOrder saved = smartRepairService.createAndSmartDispatch(order);
        bill.setAnomalyRepairId(saved.getId());
        bill.setAnomalyStatus("INVESTIGATING");
        bill.setAnomalyUpdatedBy((Long) request.getAttribute("currentUserId"));
        bill.setAnomalyUpdatedAt(java.time.LocalDateTime.now());
        utilityBillMapper.updateById(bill);
        messageService.push(bed.getStudentId(), "异常能耗已转报修",
                "系统已根据水电异常为您创建排查工单「" + saved.getTitle() + "」",
                "REPAIR", String.valueOf(saved.getId()));
        return Result.success("已生成异常排查报修工单", saved);
    }

    @PostMapping("/anomaly-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<UtilityBill> updateAnomaly(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long billId = Long.valueOf(body.get("billId").toString());
        String status = String.valueOf(body.get("status"));
        String note = body.get("note") == null ? "" : body.get("note").toString().trim();
        if (!Set.of("PENDING", "INVESTIGATING", "RESOLVED").contains(status)) return Result.error("无效的排查状态");
        if (note.length() > 500 || ("RESOLVED".equals(status) && note.isEmpty())) return Result.error("请填写不超过 500 字的处理结果");
        UtilityBill bill = utilityBillMapper.selectOne(new LambdaQueryWrapper<UtilityBill>()
                .eq(UtilityBill::getId, billId).last("FOR UPDATE"));
        if (bill == null || !Integer.valueOf(1).equals(bill.getAbnormalFlag())) return Result.error("异常账单不存在");
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), bill.getBuildingId());
        if (denied != null) return Result.error(403, denied.getMessage());
        bill.setAnomalyStatus(status); bill.setAnomalyNote(note);
        bill.setAnomalyUpdatedBy((Long) request.getAttribute("currentUserId"));
        bill.setAnomalyUpdatedAt(java.time.LocalDateTime.now());
        utilityBillMapper.updateById(bill);
        return Result.success("排查记录已保存", bill);
    }
}
