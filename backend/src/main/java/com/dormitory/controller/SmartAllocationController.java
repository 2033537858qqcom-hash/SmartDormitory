package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
                                                     jakarta.servlet.http.HttpServletRequest request) {
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
}
