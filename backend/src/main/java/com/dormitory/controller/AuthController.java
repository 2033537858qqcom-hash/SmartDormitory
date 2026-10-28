package com.dormitory.controller;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.Result;
import com.dormitory.common.SessionService;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final DormBedMapper bedMapper;
    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final StudentHabitProfileMapper habitProfileMapper;
    private final UtilityBillMapper utilityBillMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final SysNoticeMapper noticeMapper;
    private final VisitorRecordMapper visitorMapper;
    private final DisciplineRecordMapper disciplineMapper;
    private final DormAdjustApplyMapper adjustApplyMapper;
    private final SmartAllocationService smartAllocationService;
    private final SessionService sessions;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
        );
        if (user == null) {
            return Result.error("账号或密码错误");
        }
        if (!passwordMatches(password, user.getPassword())) {
            return Result.error("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.error("该账号已被禁用");
        }

        if (!isBcrypt(user.getPassword())) {
            SysUser upgrade = new SysUser();
            upgrade.setId(user.getId());
            upgrade.setPassword(BCrypt.hashpw(password));
            sysUserMapper.updateById(upgrade);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        user.setPassword(null);
        data.put("user", user);
        data.put("token", sessions.create(user.getId()));

        if ("DORM_MANAGER".equals(user.getRole())) {
            DormBuilding managed = buildingMapper.selectOne(
                    new LambdaQueryWrapper<DormBuilding>().eq(DormBuilding::getManagerId, user.getId())
            );
            data.put("managedBuilding", managed);
        }

        if ("STUDENT".equals(user.getRole())) {
            enrichStudentContext(user.getId(), data);
        }
        return Result.success("登录成功", data);
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        sessions.revoke(token);
        return Result.success("已退出登录", "OK");
    }

    @GetMapping("/student-dashboard/{studentId}")
    public Result<Map<String, Object>> getStudentDashboard(@PathVariable Long studentId) {
        SysUser user = sysUserMapper.selectById(studentId);
        if (user == null) {
            return Result.error("未找到该学生信息");
        }
        user.setPassword(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        enrichStudentContext(studentId, data);

        // 最新公告
        List<SysNotice> notices = noticeMapper.selectList(
                new LambdaQueryWrapper<SysNotice>()
                        .orderByDesc(SysNotice::getIsTop)
                        .orderByDesc(SysNotice::getCreateTime)
                        .last("LIMIT 6")
        );
        data.put("notices", notices);
        return Result.success(data);
    }

    private void enrichStudentContext(Long studentId, Map<String, Object> data) {
        StudentHabitProfile profile = habitProfileMapper.selectOne(
                new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, studentId)
        );
        data.put("habitProfile", profile);

        DormBed bed = bedMapper.selectOne(
                new LambdaQueryWrapper<DormBed>()
                        .eq(DormBed::getStudentId, studentId)
                        .eq(DormBed::getStatus, 1)
        );
        data.put("bed", bed);

        if (bed != null) {
            DormRoom room = roomMapper.selectById(bed.getRoomId());
            data.put("room", room);
            if (room != null) {
                DormBuilding building = buildingMapper.selectById(room.getBuildingId());
                data.put("building", building);
                data.put("roomRadar", smartAllocationService.getRoomRadarAnalysis(room.getId()));

                List<UtilityBill> rawBills = utilityBillMapper.selectList(
                        new LambdaQueryWrapper<UtilityBill>()
                                .eq(UtilityBill::getRoomId, room.getId())
                                .orderByDesc(UtilityBill::getBillMonth)
                );
                int occ = (room.getOccupiedCount() != null && room.getOccupiedCount() > 0) ? room.getOccupiedCount() : 4;
                List<Map<String, Object>> billList = new ArrayList<>();
                for (UtilityBill b : rawBills) {
                    Map<String, Object> bm = new LinkedHashMap<>();
                    bm.put("id", b.getId());
                    bm.put("billMonth", b.getBillMonth());
                    bm.put("buildingId", b.getBuildingId());
                    bm.put("roomId", b.getRoomId());
                    bm.put("electricUsed", b.getElectricUsed());
                    bm.put("electricCost", b.getElectricCost());
                    bm.put("waterUsed", b.getWaterUsed());
                    bm.put("waterCost", b.getWaterCost());
                    bm.put("totalCost", b.getTotalCost());
                    bm.put("perCapitaCost", b.getTotalCost().divide(java.math.BigDecimal.valueOf(occ), 2, java.math.RoundingMode.HALF_UP));
                    bm.put("abnormalFlag", b.getAbnormalFlag());
                    bm.put("abnormalReason", b.getAbnormalReason());
                    bm.put("payStatus", b.getPayStatus());
                    bm.put("payTime", b.getPayTime());
                    billList.add(bm);
                }
                data.put("bills", billList);
            }
            // 智能推荐调宿候选寝室（按六维契合度降序排列）
            data.put("recommendedRooms", smartAllocationService.recommendTransferRooms(studentId));
        }

        List<RepairOrder> myRepairs = repairOrderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStudentId, studentId)
                        .orderByDesc(RepairOrder::getCreateTime)
        );
        data.put("myRepairs", myRepairs);

        // 我的访客预约通行码记录（含动态电子核验码）
        List<VisitorRecord> rawVisitors = visitorMapper.selectList(
                new LambdaQueryWrapper<VisitorRecord>()
                        .eq(VisitorRecord::getVisitStudentId, studentId)
                        .orderByDesc(VisitorRecord::getEnterTime)
        );
        List<Map<String, Object>> visitorViews = new ArrayList<>();
        for (VisitorRecord v : rawVisitors) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("id", v.getId());
            vm.put("passCode", String.format("VP-%06d", Math.abs((v.getId() != null ? v.getId() : 1L) * 13579L + 802100L) % 1000000L));
            vm.put("visitorName", v.getVisitorName());
            vm.put("visitorPhone", v.getVisitorPhone());
            vm.put("idCardMask", v.getIdCardMask());
            vm.put("visitStudentId", v.getVisitStudentId());
            vm.put("buildingId", v.getBuildingId());
            vm.put("roomNo", v.getRoomNo());
            vm.put("relation", v.getRelation());
            vm.put("visitReason", v.getVisitReason());
            vm.put("enterTime", v.getEnterTime());
            vm.put("leaveTime", v.getLeaveTime());
            vm.put("status", v.getStatus());
            visitorViews.add(vm);
        }
        data.put("myVisitors", visitorViews);

        // 我的调宿/退宿申请进度
        List<DormAdjustApply> myAdjusts = adjustApplyMapper.selectList(
                new LambdaQueryWrapper<DormAdjustApply>()
                        .eq(DormAdjustApply::getStudentId, studentId)
                        .orderByDesc(DormAdjustApply::getCreateTime)
        );
        data.put("myAdjusts", myAdjusts);

        // 我的晚归报备与纪律记录
        List<DisciplineRecord> myDisciplines = disciplineMapper.selectList(
                new LambdaQueryWrapper<DisciplineRecord>()
                        .eq(DisciplineRecord::getStudentId, studentId)
                        .orderByDesc(DisciplineRecord::getOccurTime)
        );
        data.put("myDisciplines", myDisciplines);
    }

    @GetMapping("/users")
    public Result<List<SysUser>> listUsers(@RequestParam(required = false) String role,
                                           @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (role != null && !role.isEmpty()) {
            wrapper.eq(SysUser::getRole, role);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getRealName, keyword).or().like(SysUser::getUsername, keyword));
        }
        wrapper.orderByAsc(SysUser::getId);
        List<SysUser> list = sysUserMapper.selectList(wrapper);
        list.forEach(u -> u.setPassword(null));
        return Result.success(list);
    }

    @PostMapping("/user")
    public Result<SysUser> saveOrUpdateUser(@RequestBody SysUser user) {
        if (user.getId() == null) {
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword("123456");
            }
            user.setPassword(BCrypt.hashpw(user.getPassword()));
            if (user.getStatus() == null) {
                user.setStatus(1);
            }
            sysUserMapper.insert(user);
        } else {
            boolean revoke = user.getPassword() != null && !user.getPassword().isEmpty()
                    || Integer.valueOf(0).equals(user.getStatus());
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                user.setPassword(BCrypt.hashpw(user.getPassword()));
            } else {
                user.setPassword(null);
            }
            sysUserMapper.updateById(user);
            if (revoke) sessions.revokeUser(user.getId());
        }
        user.setPassword(null);
        return Result.success("用户信息保存成功", user);
    }

    @PostMapping("/change-password")
    public Result<String> changePassword(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String oldPwd = body.get("oldPassword").toString();
        String newPwd = body.get("newPassword").toString();

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!passwordMatches(oldPwd, user.getPassword())) {
            return Result.error("原密码校验不通过");
        }
        user.setPassword(BCrypt.hashpw(newPwd));
        sysUserMapper.updateById(user);
        sessions.revokeUser(userId);
        return Result.success("密码修改成功", "OK");
    }

    @PostMapping("/reset-password/{id}")
    public Result<String> resetPassword(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) return Result.error("用户不存在");
        user.setPassword(BCrypt.hashpw("123456"));
        sysUserMapper.updateById(user);
        sessions.revokeUser(id);
        return Result.success("密码已重置为默认密码 123456", "OK");
    }

    @DeleteMapping("/user/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        if (id != null && id == 1L) {
            return Result.error("超级管理员账号不可删除");
        }
        sysUserMapper.deleteById(id);
        sessions.revokeUser(id);
        return Result.success("账号已删除", "OK");
    }

    private static boolean isBcrypt(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private static boolean passwordMatches(String raw, String stored) {
        if (raw == null || stored == null) return false;
        if (isBcrypt(stored)) {
            try {
                return BCrypt.checkpw(raw, stored);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return Objects.equals(stored, raw) || Objects.equals(stored, DigestUtil.md5Hex(raw));
    }
}
