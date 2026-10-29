package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/smart-alloc")
@RequiredArgsConstructor
public class SmartAllocationController {

    private final SmartAllocationService smartAllocationService;
    private final SysUserMapper userMapper;
    private final StudentHabitProfileMapper habitProfileMapper;
    private final DormBedMapper bedMapper;
    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final DormAllocationLogMapper allocationLogMapper;

    @org.springframework.beans.factory.annotation.Value("${app.demo-enabled:false}")
    private boolean demoEnabled;

    @GetMapping("/candidates")
    public Result<List<Map<String, Object>>> listCandidates() {
        List<SysUser> students = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "STUDENT").orderByAsc(SysUser::getUsername)
        );
        Map<Long, StudentHabitProfile> profileMap = habitProfileMapper.selectList(null).stream()
                .collect(Collectors.toMap(StudentHabitProfile::getStudentId, p -> p, (a, b) -> a));
        Map<Long, DormBed> bedMap = bedMapper.selectList(
                new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1)
        ).stream().filter(b -> b.getStudentId() != null)
                .collect(Collectors.toMap(DormBed::getStudentId, b -> b, (a, b) -> a));
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b));

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser s : students) {
            StudentHabitProfile hp = profileMap.get(s.getId());
            DormBed bed = bedMap.get(s.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", s.getId());
            item.put("studentNo", s.getUsername());
            item.put("realName", s.getRealName());
            item.put("gender", s.getGender());
            item.put("college", s.getCollege());
            item.put("major", s.getMajor());
            item.put("className", s.getClassName());
            item.put("grade", s.getGrade());
            item.put("allocated", bed != null);
            if (bed != null) {
                DormRoom r = roomMap.get(bed.getRoomId());
                DormBuilding b = r != null ? buildingMap.get(r.getBuildingId()) : null;
                item.put("roomInfo", (b != null ? b.getBuildingName() : "") + " " + (r != null ? r.getRoomNo() : "") + "室-" + bed.getBedNo() + "号床");
                item.put("roomId", bed.getRoomId());
            } else {
                item.put("roomInfo", "待智能分寝");
            }
            item.put("profile", hp);
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/habit-profile")
    public Result<StudentHabitProfile> saveHabitProfile(@RequestBody StudentHabitProfile profile,
                                                        HttpServletRequest request) {
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            profile.setStudentId((Long) request.getAttribute("currentUserId"));
        }
        StudentHabitProfile existing = habitProfileMapper.selectOne(
                new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, profile.getStudentId())
        );
        if (existing != null) {
            profile.setId(existing.getId());
            if (profile.getIsAllocated() == null) {
                profile.setIsAllocated(existing.getIsAllocated());
            }
            habitProfileMapper.updateById(profile);
        } else {
            if (profile.getIsAllocated() == null) {
                profile.setIsAllocated(0);
            }
            habitProfileMapper.insert(profile);
        }
        return Result.success("六维生活习惯画像保存成功", profile);
    }

    @PostMapping("/run")
    public Result<Map<String, Object>> runAllocation(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        Long buildingId = Long.valueOf(body.get("buildingId").toString());
        boolean commit = body.get("commit") != null && Boolean.parseBoolean(body.get("commit").toString());
        String strategy = body.get("strategy") != null ? body.get("strategy").toString() : "HABIT_FIRST";

        double[] customWeights = null;
        if (body.get("weights") instanceof List<?> wList && wList.size() == 6) {
            customWeights = new double[6];
            for (int i = 0; i < 6; i++) {
                customWeights[i] = Double.parseDouble(String.valueOf(wList.get(i)));
            }
        }
        Long operatorId = request.getAttribute("currentUserId") instanceof Long uid ? uid : 1L;
        Map<String, Object> res = smartAllocationService.runSmartAllocation(buildingId, commit, customWeights, strategy, operatorId);
        return Result.success(commit ? "智能分寝已完成并写入床位！" : "智能分寝算法测算预览完成", res);
    }

    @GetMapping("/recommend-rooms/{studentId}")
    public Result<List<Map<String, Object>>> recommendTransferRooms(@PathVariable Long studentId) {
        return Result.success(smartAllocationService.recommendTransferRooms(studentId));
    }

    @PostMapping("/reset-demo")
    public Result<Map<String, Object>> resetDemoFreshmen() {
        if (!demoEnabled) return Result.error(403, "演示数据重置未启用");
        Map<String, Object> res = smartAllocationService.resetDemoFreshmen();
        return Result.success("2026级待分寝新生演示状态已重置，可重新执行分寝对比实验！", res);
    }

    @GetMapping("/room-radar/{roomId}")
    public Result<Map<String, Object>> getRoomRadar(@PathVariable Long roomId) {
        return Result.success(smartAllocationService.getRoomRadarAnalysis(roomId));
    }

    @GetMapping("/logs")
    public Result<List<DormAllocationLog>> listLogs() {
        return Result.success(allocationLogMapper.selectList(
                new LambdaQueryWrapper<DormAllocationLog>().orderByDesc(DormAllocationLog::getCreateTime)
        ));
    }

    /** 分寝前校验：未填画像、性别与空床不匹配、空床不足 */
    @GetMapping("/precheck")
    public Result<Map<String, Object>> precheck(@RequestParam(required = false) Long buildingId) {
        List<SysUser> students = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "STUDENT"));
        Map<Long, StudentHabitProfile> profileMap = habitProfileMapper.selectList(null).stream()
                .collect(Collectors.toMap(StudentHabitProfile::getStudentId, x -> x, (a, b) -> a));
        Set<Long> occupied = bedMapper.selectList(new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1))
                .stream().map(DormBed::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<Map<String, Object>> missingProfile = new ArrayList<>();
        List<Map<String, Object>> unallocated = new ArrayList<>();
        int maleWaiting = 0, femaleWaiting = 0;
        for (SysUser s : students) {
            if (occupied.contains(s.getId())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.getId());
            row.put("studentNo", s.getUsername());
            row.put("realName", s.getRealName());
            row.put("gender", s.getGender());
            unallocated.add(row);
            if (Integer.valueOf(2).equals(s.getGender())) femaleWaiting++;
            else maleWaiting++;
            if (!profileMap.containsKey(s.getId())) {
                missingProfile.add(row);
            }
        }

        LambdaQueryWrapper<DormRoom> rq = new LambdaQueryWrapper<>();
        if (buildingId != null) rq.eq(DormRoom::getBuildingId, buildingId);
        List<DormRoom> rooms = roomMapper.selectList(rq);
        Map<Long, DormBuilding> bmap = buildingMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b));
        int maleEmpty = 0, femaleEmpty = 0;
        for (DormRoom r : rooms) {
            DormBuilding b = bmap.get(r.getBuildingId());
            int cap = r.getCapacity() != null ? r.getCapacity() : 4;
            int occ = r.getOccupiedCount() != null ? r.getOccupiedCount() : 0;
            int empty = Math.max(0, cap - occ);
            if (b != null && Integer.valueOf(2).equals(b.getGenderType())) femaleEmpty += empty;
            else maleEmpty += empty;
        }
        List<String> warnings = new ArrayList<>();
        if (!missingProfile.isEmpty()) warnings.add("有 " + missingProfile.size() + " 名未分配学生尚未填写习惯画像");
        if (maleWaiting > maleEmpty) warnings.add("男生待分 " + maleWaiting + " 人，空床仅 " + maleEmpty);
        if (femaleWaiting > femaleEmpty) warnings.add("女生待分 " + femaleWaiting + " 人，空床仅 " + femaleEmpty);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("unallocatedCount", unallocated.size());
        res.put("missingProfileCount", missingProfile.size());
        res.put("missingProfiles", missingProfile);
        res.put("maleWaiting", maleWaiting);
        res.put("femaleWaiting", femaleWaiting);
        res.put("maleEmptyBeds", maleEmpty);
        res.put("femaleEmptyBeds", femaleEmpty);
        res.put("ready", warnings.isEmpty() && !unallocated.isEmpty());
        res.put("warnings", warnings);
        res.put("buildingId", buildingId);
        return Result.success(res);
    }

}
