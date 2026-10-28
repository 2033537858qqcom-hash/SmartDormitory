package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DormBuildingMapper buildingMapper;
    private final DormRoomMapper roomMapper;
    private final DormBedMapper bedMapper;
    private final SysUserMapper userMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final UtilityBillMapper utilityBillMapper;
    private final BuildingScopeService buildingScope;

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(HttpServletRequest request) {
        Set<Long> scope = buildingScope.resolve(request);
        List<DormBuilding> allBuildings = buildingMapper.selectList(null);
        List<DormRoom> allRooms = roomMapper.selectList(null);
        List<DormBed> allBeds = bedMapper.selectList(null);
        List<RepairOrder> allRepairs = repairOrderMapper.selectList(null);
        List<UtilityBill> allBills = utilityBillMapper.selectList(null);
        List<DormBuilding> buildings = allBuildings;
        List<DormRoom> rooms = allRooms;
        List<DormBed> beds = allBeds;
        List<RepairOrder> repairs = allRepairs;
        List<UtilityBill> bills = allBills;
        if (scope != null) {
            buildings = allBuildings.stream().filter(b -> scope.contains(b.getId())).collect(Collectors.toList());
            Set<Long> buildingIds = buildings.stream().map(DormBuilding::getId).collect(Collectors.toSet());
            rooms = allRooms.stream().filter(r -> buildingIds.contains(r.getBuildingId())).collect(Collectors.toList());
            Set<Long> roomIds = rooms.stream().map(DormRoom::getId).collect(Collectors.toSet());
            beds = allBeds.stream().filter(b -> roomIds.contains(b.getRoomId())).collect(Collectors.toList());
            repairs = allRepairs.stream().filter(r -> buildingIds.contains(r.getBuildingId())).collect(Collectors.toList());
            bills = allBills.stream().filter(b -> buildingIds.contains(b.getBuildingId())).collect(Collectors.toList());
        }
        final List<DormRoom> roomSnapshot = rooms;

        long totalBeds = beds.size();
        long occupiedBeds = beds.stream().filter(b -> b.getStatus() != null && b.getStatus() == 1).count();
        double occupancyRate = totalBeds == 0 ? 0.0 :
                BigDecimal.valueOf(occupiedBeds * 100.0 / totalBeds).setScale(1, RoundingMode.HALF_UP).doubleValue();

        long totalStudents = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "STUDENT"));
        long unallocatedStudents = Math.max(0, totalStudents - occupiedBeds);

        long pendingRepairs = repairs.stream()
                .filter(r -> Arrays.asList("PENDING", "ASSIGNED", "PROCESSING").contains(r.getStatus()))
                .count();
        long highRiskRepairs = repairs.stream()
                .filter(r -> "HIGH".equals(r.getPriority()))
                .count();
        long abnormalBills = bills.stream()
                .filter(b -> b.getAbnormalFlag() != null && b.getAbnormalFlag() == 1)
                .count();
        long abnormalRooms = bills.stream()
                .filter(b -> b.getAbnormalFlag() != null && b.getAbnormalFlag() == 1)
                .map(UtilityBill::getRoomId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double avgHarmony = rooms.stream()
                .filter(r -> r.getOccupiedCount() > 0 && r.getHarmonyScore() != null && r.getHarmonyScore().doubleValue() > 0)
                .mapToDouble(r -> r.getHarmonyScore().doubleValue())
                .average().orElse(92.5);

        // 1. 各楼栋床位容量与入住统计 (柱状图)
        List<Map<String, Object>> buildingStats = new ArrayList<>();
        for (DormBuilding b : buildings) {
            List<DormRoom> bRooms = rooms.stream().filter(r -> Objects.equals(r.getBuildingId(), b.getId())).collect(Collectors.toList());
            int cap = bRooms.stream().mapToInt(DormRoom::getCapacity).sum();
            int occ = bRooms.stream().mapToInt(DormRoom::getOccupiedCount).sum();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("buildingName", b.getBuildingName());
            item.put("capacity", cap);
            item.put("occupied", occ);
            item.put("free", Math.max(0, cap - occ));
            buildingStats.add(item);
        }

        // 2. 报修类型分布 (饼图)
        Map<String, String> typeLabels = new LinkedHashMap<>();
        typeLabels.put("ELECTRIC", "电力照明");
        typeLabels.put("WATER", "水暖卫浴");
        typeLabels.put("WOOD", "门窗床桌");
        typeLabels.put("NETWORK", "网络与空调");

        List<Map<String, Object>> repairTypeChart = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeLabels.entrySet()) {
            long cnt = repairs.stream().filter(r -> entry.getKey().equals(r.getRepairType())).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getValue());
            item.put("value", cnt);
            repairTypeChart.add(item);
        }

        // 3. 月度水电能耗趋势 (折线图)
        List<String> months = Arrays.asList("2026-08", "2026-09", "2026-10");
        List<Double> electricTrend = new ArrayList<>();
        List<Double> waterTrend = new ArrayList<>();
        for (String m : months) {
            double eSum = bills.stream().filter(b -> m.equals(b.getBillMonth()))
                    .mapToDouble(b -> b.getElectricUsed().doubleValue()).sum();
            double wSum = bills.stream().filter(b -> m.equals(b.getBillMonth()))
                    .mapToDouble(b -> b.getWaterUsed().doubleValue()).sum();
            electricTrend.add(BigDecimal.valueOf(eSum).setScale(1, RoundingMode.HALF_UP).doubleValue());
            waterTrend.add(BigDecimal.valueOf(wSum).setScale(1, RoundingMode.HALF_UP).doubleValue());
        }

        // 4. 文明寝室红黑榜与能耗异常预警列表
        Map<Long, String> bNameMap = buildings.stream().collect(Collectors.toMap(DormBuilding::getId, DormBuilding::getBuildingName));
        List<Map<String, Object>> roomRankings = rooms.stream()
                .filter(r -> r.getOccupiedCount() > 0)
                .sorted((a, b) -> b.getCivilizedScore().compareTo(a.getCivilizedScore()))
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("roomId", r.getId());
                    m.put("roomName", bNameMap.getOrDefault(r.getBuildingId(), "") + "-" + r.getRoomNo());
                    m.put("harmonyScore", r.getHarmonyScore());
                    m.put("civilizedScore", r.getCivilizedScore());
                    m.put("starLevel", r.getStarLevel());
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> abnormalAlerts = bills.stream()
                .filter(b -> b.getAbnormalFlag() != null && b.getAbnormalFlag() == 1)
                .map(b -> {
                    DormRoom r = roomSnapshot.stream().filter(rm -> Objects.equals(rm.getId(), b.getRoomId())).findFirst().orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("billMonth", b.getBillMonth());
                    m.put("roomName", bNameMap.getOrDefault(b.getBuildingId(), "") + "-" + (r != null ? r.getRoomNo() : b.getRoomId()));
                    m.put("reason", b.getAbnormalReason());
                    m.put("totalCost", b.getTotalCost());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalBuildings", buildings.size());
        data.put("totalRooms", rooms.size());
        data.put("totalBeds", totalBeds);
        data.put("occupiedBeds", occupiedBeds);
        data.put("occupancyRate", occupancyRate);
        data.put("unallocatedStudents", unallocatedStudents);
        data.put("pendingRepairs", pendingRepairs);
        data.put("highRiskRepairs", highRiskRepairs);
        data.put("abnormalBills", abnormalBills);
        data.put("abnormalRooms", abnormalRooms);
        data.put("avgHarmonyScore", BigDecimal.valueOf(avgHarmony).setScale(1, RoundingMode.HALF_UP));
        data.put("buildingStats", buildingStats);
        data.put("repairTypeChart", repairTypeChart);
        data.put("months", months);
        data.put("electricTrend", electricTrend);
        data.put("waterTrend", waterTrend);
        data.put("roomRankings", roomRankings);
        data.put("abnormalAlerts", abnormalAlerts);
        return Result.success(data);
    }
}
