package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartUtilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final DormBedMapper bedMapper;
    private final BuildingScopeService buildingScope;

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
            item.put("payStatus", b.getPayStatus());
            item.put("payTime", b.getPayTime());
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/meter-entry")
    public Result<UtilityBill> saveMeterReading(@RequestBody UtilityBill bill, HttpServletRequest request) {
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), bill.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<UtilityBill> typed = (Result<UtilityBill>) denied;
            return typed;
        }
        UtilityBill saved = smartUtilityService.createOrUpdateMeterBill(bill);
        String msg = saved.getAbnormalFlag() != null && saved.getAbnormalFlag() == 1
                ? "抄表核算完成！⚠️触发智能能耗突增异常预警：" + saved.getAbnormalReason()
                : "抄表录入与阶梯计价核算完成！";
        return Result.success(msg, saved);
    }

    @PostMapping("/pay-or-recharge")
    public Result<DormRoom> payOrRecharge(@RequestBody Map<String, Object> body, HttpServletRequest request) {
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
                Result<DormRoom> typed = (Result<DormRoom>) denied;
                return typed;
            }
        }

        DormRoom updatedRoom = smartUtilityService.payBillOrRecharge(roomId, billId, electricRecharge, waterRecharge);
        return Result.success("缴费/充值成功，寝室水电账户已实时更新！", updatedRoom);
    }
}
