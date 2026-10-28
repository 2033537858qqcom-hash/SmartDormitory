package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dorm")
@RequiredArgsConstructor
public class DormManageController {

    private final DormBuildingMapper buildingMapper;
    private final DormRoomMapper roomMapper;
    private final DormBedMapper bedMapper;
    private final SysUserMapper userMapper;
    private final StudentHabitProfileMapper habitProfileMapper;
    private final SmartAllocationService smartAllocationService;
    private final BuildingScopeService buildingScope;

    @GetMapping("/buildings")
    public Result<List<Map<String, Object>>> listBuildings(HttpServletRequest request) {
        LambdaQueryWrapper<DormBuilding> bq = new LambdaQueryWrapper<DormBuilding>().orderByAsc(DormBuilding::getBuildingCode);
        Set<Long> scope = buildingScope.resolve(request);
        // 学生端可读全校楼栋列表（申请调宿等）；宿管仅见管辖楼栋
        if (scope != null) {
            if (scope.isEmpty()) return Result.success(List.of());
            bq.in(DormBuilding::getId, scope);
        }
        List<DormBuilding> buildings = buildingMapper.selectList(bq);
        List<DormRoom> allRooms = roomMapper.selectList(null);
        Map<Long, SysUser> userMap = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (DormBuilding b : buildings) {
            List<DormRoom> bRooms = allRooms.stream()
                    .filter(r -> Objects.equals(r.getBuildingId(), b.getId()))
                    .collect(Collectors.toList());
            int totalCapacity = bRooms.stream().mapToInt(DormRoom::getCapacity).sum();
            int totalOccupied = bRooms.stream().mapToInt(DormRoom::getOccupiedCount).sum();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", b.getId());
            item.put("buildingCode", b.getBuildingCode());
            item.put("buildingName", b.getBuildingName());
            item.put("genderType", b.getGenderType());
            item.put("totalFloors", b.getTotalFloors());
            item.put("managerId", b.getManagerId());
            SysUser manager = userMap.get(b.getManagerId());
            item.put("managerName", manager != null ? manager.getRealName() : "未指派");
            item.put("managerPhone", manager != null ? manager.getPhone() : "-");
            item.put("locationDesc", b.getLocationDesc());
            item.put("remark", b.getRemark());
            item.put("roomCount", bRooms.size());
            item.put("totalCapacity", totalCapacity);
            item.put("totalOccupied", totalOccupied);
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/building")
    public Result<DormBuilding> saveBuilding(@RequestBody DormBuilding building, HttpServletRequest request) {
        // 楼栋↔宿管绑定仅管理员；宿管入口已被拦截器挡住，此处再兜底
        if (!"ADMIN".equals(request.getAttribute("currentUserRole"))) {
            return Result.error(403, "仅管理员可维护楼栋与宿管绑定");
        }
        if (building.getManagerId() != null) {
            SysUser mgr = userMapper.selectById(building.getManagerId());
            if (mgr == null || !"DORM_MANAGER".equals(mgr.getRole())) {
                return Result.error("managerId 必须指向角色为宿管员(DORM_MANAGER)的账号");
            }
        }
        if (building.getId() == null) {
            buildingMapper.insert(building);
        } else {
            buildingMapper.updateById(building);
        }
        return Result.success("楼栋保存成功", building);
    }

    @GetMapping("/rooms")
    public Result<List<Map<String, Object>>> listRooms(@RequestParam(required = false) Long buildingId,
                                                       HttpServletRequest request) {
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null) {
            if (scope.isEmpty()) return Result.success(List.of());
            if (buildingId != null && !scope.contains(buildingId)) {
                return Result.error(403, "只能查看管辖楼栋的房间");
            }
        }
        LambdaQueryWrapper<DormRoom> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(DormRoom::getBuildingId, scope);
        }
        if (buildingId != null) {
            wrapper.eq(DormRoom::getBuildingId, buildingId);
        }
        wrapper.orderByAsc(DormRoom::getBuildingId).orderByAsc(DormRoom::getFloorNo).orderByAsc(DormRoom::getRoomNo);
        List<DormRoom> rooms = roomMapper.selectList(wrapper);

        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b));
        List<DormBed> allBeds = bedMapper.selectList(new LambdaQueryWrapper<DormBed>().orderByAsc(DormBed::getBedNo));
        Map<Long, SysUser> userMap = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (DormRoom r : rooms) {
            DormBuilding b = buildingMap.get(r.getBuildingId());
            List<DormBed> rBeds = allBeds.stream()
                    .filter(bed -> Objects.equals(bed.getRoomId(), r.getId()))
                    .collect(Collectors.toList());

            List<Map<String, Object>> bedViews = new ArrayList<>();
            for (DormBed bed : rBeds) {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("id", bed.getId());
                bm.put("bedNo", bed.getBedNo());
                bm.put("status", bed.getStatus());
                bm.put("studentId", bed.getStudentId());
                bm.put("checkInDate", bed.getCheckInDate());
                if (bed.getStudentId() != null) {
                    SysUser stu = userMap.get(bed.getStudentId());
                    if (stu != null) {
                        bm.put("studentName", stu.getRealName());
                        bm.put("studentNo", stu.getUsername());
                        bm.put("className", stu.getClassName());
                        bm.put("phone", stu.getPhone());
                    }
                }
                bedViews.add(bm);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("buildingId", r.getBuildingId());
            item.put("buildingName", b != null ? b.getBuildingName() : "");
            item.put("buildingCode", b != null ? b.getBuildingCode() : "");
            item.put("genderType", b != null ? b.getGenderType() : 1);
            item.put("floorNo", r.getFloorNo());
            item.put("roomNo", r.getRoomNo());
            item.put("capacity", r.getCapacity());
            item.put("occupiedCount", r.getOccupiedCount());
            item.put("electricBalance", r.getElectricBalance());
            item.put("waterBalance", r.getWaterBalance());
            item.put("harmonyScore", r.getHarmonyScore());
            item.put("civilizedScore", r.getCivilizedScore());
            item.put("starLevel", r.getStarLevel());
            item.put("status", r.getStatus());
            item.put("beds", bedViews);
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/room")
    @Transactional(rollbackFor = Exception.class)
    public Result<DormRoom> createOrUpdateRoom(@RequestBody DormRoom room, HttpServletRequest request) {
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), room.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<DormRoom> typed = (Result<DormRoom>) denied;
            return typed;
        }
        if (room.getId() == null) {
            if (room.getCapacity() == null) room.setCapacity(4);
            if (room.getOccupiedCount() == null) room.setOccupiedCount(0);
            if (room.getElectricBalance() == null) room.setElectricBalance(BigDecimal.valueOf(100.00));
            if (room.getWaterBalance() == null) room.setWaterBalance(BigDecimal.valueOf(50.00));
            if (room.getHarmonyScore() == null) room.setHarmonyScore(BigDecimal.valueOf(90.00));
            if (room.getCivilizedScore() == null) room.setCivilizedScore(BigDecimal.valueOf(90.00));
            if (room.getStarLevel() == null) room.setStarLevel(4);
            if (room.getStatus() == null) room.setStatus(1);
            roomMapper.insert(room);

            // 自动初始化该房间的床位
            for (int i = 1; i <= room.getCapacity(); i++) {
                DormBed bed = new DormBed();
                bed.setRoomId(room.getId());
                bed.setBedNo(i);
                bed.setStatus(0);
                bedMapper.insert(bed);
            }
        } else {
            roomMapper.updateById(room);
        }
        return Result.success("房间保存成功", room);
    }

    @PostMapping("/assign-bed")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> assignOrReleaseBed(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long bedId = Long.valueOf(body.get("bedId").toString());
        Long studentId = body.get("studentId") != null ? Long.valueOf(body.get("studentId").toString()) : null;

        DormBed bed = bedMapper.selectById(bedId);
        if (bed == null) {
            return Result.error("床位不存在");
        }
        DormRoom room = roomMapper.selectById(bed.getRoomId());
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(
                buildingScope.resolve(request), room != null ? room.getBuildingId() : null);
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<String> typed = (Result<String>) denied;
            return typed;
        }

        if (studentId == null) {
            // 退宿释放床位
            Long oldStudentId = bed.getStudentId();
            bed.setStudentId(null);
            bed.setCheckInDate(null);
            bed.setStatus(0);
            bedMapper.updateById(bed);

            if (oldStudentId != null) {
                StudentHabitProfile hp = habitProfileMapper.selectOne(
                        new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, oldStudentId)
                );
                if (hp != null) {
                    hp.setIsAllocated(0);
                    habitProfileMapper.updateById(hp);
                }
            }
        } else {
            // 办理入住：校验学生性别与楼栋性别限制一致
            SysUser student = userMapper.selectById(studentId);
            if (student == null) {
                return Result.error("学生不存在");
            }
            DormBuilding building = room != null ? buildingMapper.selectById(room.getBuildingId()) : null;
            if (building != null && building.getGenderType() != null && student.getGender() != null
                    && !Objects.equals(building.getGenderType(), student.getGender())) {
                String need = building.getGenderType() == 1 ? "男生" : "女生";
                String got = student.getGender() == 1 ? "男" : "女";
                return Result.error("性别不符：【" + building.getBuildingName() + "】仅允许" + need
                        + "入住，当前学生性别为" + got + "，已拒绝混寝分配");
            }
            // 同一学生不可占用多床：先释放其旧床位
            List<DormBed> oldBeds = bedMapper.selectList(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getStudentId, studentId)
                            .eq(DormBed::getStatus, 1)
            );
            for (DormBed ob : oldBeds) {
                if (!Objects.equals(ob.getId(), bedId)) {
                    ob.setStudentId(null);
                    ob.setCheckInDate(null);
                    ob.setStatus(0);
                    bedMapper.updateById(ob);
                    DormRoom oldRoom = roomMapper.selectById(ob.getRoomId());
                    if (oldRoom != null) {
                        smartAllocationService.recalculateRoomHarmony(oldRoom.getId());
                    }
                }
            }
            bed.setStudentId(studentId);
            bed.setCheckInDate(LocalDate.now());
            bed.setStatus(1);
            bedMapper.updateById(bed);

            StudentHabitProfile hp = habitProfileMapper.selectOne(
                    new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, studentId)
            );
            if (hp != null) {
                hp.setIsAllocated(1);
                habitProfileMapper.updateById(hp);
            }
        }

        if (room != null) {
            smartAllocationService.recalculateRoomHarmony(room.getId());
        }
        return Result.success("床位状态与寝室契合度已同步更新", "OK");
    }
}
