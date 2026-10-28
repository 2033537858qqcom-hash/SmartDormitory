package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import com.dormitory.service.SmartEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/affair")
@RequiredArgsConstructor
public class DormAffairController {

    private final SmartEvaluationService smartEvaluationService;
    private final SmartAllocationService smartAllocationService;
    private final HygieneInspectionMapper hygieneMapper;
    private final DisciplineRecordMapper disciplineMapper;
    private final DormAdjustApplyMapper adjustApplyMapper;
    private final VisitorRecordMapper visitorMapper;
    private final SysNoticeMapper noticeMapper;
    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final DormBedMapper bedMapper;
    private final SysUserMapper userMapper;
    private final StudentHabitProfileMapper habitProfileMapper;

    @GetMapping("/evaluation")
    public Result<List<Map<String, Object>>> getCivilizedEvaluation() {
        return Result.success(smartEvaluationService.evaluateAllRooms());
    }

    @PostMapping("/reward-civilized")
    public Result<Map<String, Object>> rewardCivilizedRooms(@RequestBody(required = false) Map<String, Object> body) {
        BigDecimal amount = (body != null && body.get("amount") != null)
                ? new BigDecimal(body.get("amount").toString()) : BigDecimal.valueOf(30.00);
        Map<String, Object> res = smartEvaluationService.rewardFiveStarRooms(amount);
        return Result.success("已为五星文明示范寝室发放电费奖励补贴并同步发布红榜公示！", res);
    }

    @GetMapping("/hygiene")
    public Result<List<Map<String, Object>>> listHygiene() {
        List<HygieneInspection> list = hygieneMapper.selectList(
                new LambdaQueryWrapper<HygieneInspection>().orderByDesc(HygieneInspection::getCheckDate)
        );
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream().collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream().collect(Collectors.toMap(DormBuilding::getId, b -> b));

        List<Map<String, Object>> res = new ArrayList<>();
        for (HygieneInspection h : list) {
            DormRoom r = roomMap.get(h.getRoomId());
            DormBuilding b = buildingMap.get(h.getBuildingId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("checkWeek", h.getCheckWeek());
            m.put("checkDate", h.getCheckDate());
            m.put("buildingId", h.getBuildingId());
            m.put("buildingName", b != null ? b.getBuildingName() : "");
            m.put("roomId", h.getRoomId());
            m.put("roomNo", r != null ? r.getRoomNo() : "");
            m.put("floorScore", h.getFloorScore());
            m.put("bedScore", h.getBedScore());
            m.put("deskScore", h.getDeskScore());
            m.put("safetyScore", h.getSafetyScore());
            m.put("totalScore", h.getTotalScore());
            m.put("grade", h.getGrade());
            m.put("remark", h.getRemark());
            res.add(m);
        }
        return Result.success(res);
    }

    @PostMapping("/hygiene")
    public Result<HygieneInspection> saveHygiene(@RequestBody HygieneInspection h) {
        DormRoom room = roomMapper.selectById(h.getRoomId());
        if (room != null) {
            h.setBuildingId(room.getBuildingId());
        }
        int total = (h.getFloorScore() != null ? h.getFloorScore() : 25)
                + (h.getBedScore() != null ? h.getBedScore() : 25)
                + (h.getDeskScore() != null ? h.getDeskScore() : 25)
                + (h.getSafetyScore() != null ? h.getSafetyScore() : 25);
        h.setTotalScore(total);
        if (total >= 90) h.setGrade("优秀");
        else if (total >= 80) h.setGrade("良好");
        else if (total >= 60) h.setGrade("合格");
        else h.setGrade("不合格");

        if (h.getCheckDate() == null) h.setCheckDate(LocalDate.now());
        hygieneMapper.insert(h);
        // 联动触发文明寝室量化重算
        smartEvaluationService.evaluateAllRooms();
        return Result.success("卫生检查记录已保存并同步更新文明寝室量化分", h);
    }

    @GetMapping("/discipline")
    public Result<List<Map<String, Object>>> listDiscipline(@RequestParam(required = false) Long studentId) {
        LambdaQueryWrapper<DisciplineRecord> wrapper = new LambdaQueryWrapper<>();
        if (studentId != null) wrapper.eq(DisciplineRecord::getStudentId, studentId);
        wrapper.orderByDesc(DisciplineRecord::getOccurTime);
        List<DisciplineRecord> list = disciplineMapper.selectList(wrapper);

        Map<Long, SysUser> userMap = userMapper.selectList(null).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream().collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream().collect(Collectors.toMap(DormBuilding::getId, b -> b));

        List<Map<String, Object>> res = new ArrayList<>();
        for (DisciplineRecord d : list) {
            SysUser stu = userMap.get(d.getStudentId());
            DormRoom r = roomMap.get(d.getRoomId());
            DormBuilding b = r != null ? buildingMap.get(r.getBuildingId()) : null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("studentId", d.getStudentId());
            m.put("studentName", stu != null ? stu.getRealName() : "");
            m.put("studentNo", stu != null ? stu.getUsername() : "");
            m.put("roomId", d.getRoomId());
            m.put("roomName", (b != null ? b.getBuildingName() : "") + "-" + (r != null ? r.getRoomNo() : ""));
            m.put("recordType", d.getRecordType());
            m.put("deductScore", d.getDeductScore());
            m.put("occurTime", d.getOccurTime());
            m.put("description", d.getDescription());
            m.put("status", d.getStatus());
            res.add(m);
        }
        return Result.success(res);
    }

    @PostMapping("/discipline")
    public Result<DisciplineRecord> saveDiscipline(@RequestBody DisciplineRecord d) {
        if (d.getOccurTime() == null) d.setOccurTime(LocalDateTime.now());
        if (d.getStatus() == null) d.setStatus(1);
        if (d.getRoomId() == null && d.getStudentId() != null) {
            DormBed bed = bedMapper.selectOne(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getStudentId, d.getStudentId())
                            .eq(DormBed::getStatus, 1)
            );
            if (bed != null) {
                d.setRoomId(bed.getRoomId());
            }
        }
        disciplineMapper.insert(d);
        smartEvaluationService.evaluateAllRooms();
        return Result.success("违纪/晚归记录已登记并同步更新寝室量化分", d);
    }

    /**
     * 学生在线提交违纪/晚归申诉（状态置为 3-申诉待审）
     */
    @PostMapping("/discipline-appeal")
    public Result<DisciplineRecord> appealDiscipline(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String appealReason = body.get("appealReason") != null ? body.get("appealReason").toString().trim() : "因科研/急事晚归，申请核销扣分";
        DisciplineRecord d = disciplineMapper.selectById(id);
        if (d == null) {
            return Result.error("未找到该条记录");
        }
        String baseDesc = d.getDescription() != null ? d.getDescription().split(" \\| \\[")[0] : "";
        d.setDescription(baseDesc + " | [学生申诉] " + appealReason);
        d.setStatus(3);
        disciplineMapper.updateById(d);
        return Result.success("申诉已提交宿管中心审核", d);
    }

    /**
     * 宿管/管理员审核学生违纪申诉（通过则置为 2-申诉撤销并自动恢复寝室文明分）
     */
    @PostMapping("/discipline-review")
    @Transactional(rollbackFor = Exception.class)
    public Result<DisciplineRecord> reviewDisciplineAppeal(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        boolean approved = body.get("approved") == null || Boolean.parseBoolean(body.get("approved").toString());
        String reviewNote = body.get("reviewNote") != null ? body.get("reviewNote").toString().trim()
                : (approved ? "证明属实，予以核销免扣分" : "理由不充分，维持原记录");

        DisciplineRecord d = disciplineMapper.selectById(id);
        if (d == null) {
            return Result.error("未找到该条记录");
        }
        String desc = d.getDescription() != null ? d.getDescription() : "";
        if (approved) {
            d.setStatus(2); // 2-申诉撤销（不再扣减文明分）
            d.setDescription(desc + " | [宿管核销] " + reviewNote);
        } else {
            d.setStatus(1); // 1-维持生效
            d.setDescription(desc + " | [申诉驳回] " + reviewNote);
        }
        disciplineMapper.updateById(d);
        smartEvaluationService.evaluateAllRooms();
        return Result.success(approved ? "申诉审核通过，已撤销扣分并恢复寝室文明积分！" : "已驳回申诉，维持原扣分记录", d);
    }

    /**
     * 23:00 门禁考勤实时统计与模拟智能稽核
     */
    @GetMapping("/curfew-stats")
    public Result<Map<String, Object>> getCurfewStats() {
        long totalCheckedIn = bedMapper.selectCount(new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1));
        List<DisciplineRecord> allRecords = disciplineMapper.selectList(null);
        long reportedLateCount = allRecords.stream()
                .filter(r -> r.getDescription() != null && r.getDescription().contains("[学生报备]"))
                .count();
        long unreportedLateCount = allRecords.stream()
                .filter(r -> "LATE_RETURN".equals(r.getRecordType()) && (r.getDescription() == null || !r.getDescription().contains("[学生报备]")) && r.getStatus() != 2)
                .count();
        long appealPendingCount = allRecords.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 3)
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalCheckedIn", totalCheckedIn);
        data.put("normalInDormCount", Math.max(0, totalCheckedIn - reportedLateCount - unreportedLateCount));
        data.put("reportedLateCount", reportedLateCount);
        data.put("unreportedLateCount", unreportedLateCount);
        data.put("appealPendingCount", appealPendingCount);
        return Result.success(data);
    }

    @PostMapping("/curfew-check")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> runCurfewAudit(@RequestBody(required = false) Map<String, Object> body) {
        List<DormBed> occupiedBeds = bedMapper.selectList(new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1));
        List<DisciplineRecord> allRecords = disciplineMapper.selectList(null);

        Long targetStudentId = (body != null && body.get("studentId") != null)
                ? Long.valueOf(body.get("studentId").toString()) : null;

        DormBed targetBed = null;
        if (targetStudentId != null) {
            targetBed = occupiedBeds.stream().filter(b -> Objects.equals(b.getStudentId(), targetStudentId)).findFirst().orElse(null);
        } else {
            // 默认优先巡检302室或尚未登记门禁抓拍的晚睡型学生
            for (DormBed bed : occupiedBeds) {
                if (Objects.equals(bed.getRoomId(), 2L)) {
                    targetBed = bed;
                    break;
                }
            }
            if (targetBed == null && !occupiedBeds.isEmpty()) {
                targetBed = occupiedBeds.get(0);
            }
        }

        if (targetBed == null || targetBed.getStudentId() == null) {
            return Result.error("当前无在住学生可供门禁考勤稽核");
        }

        SysUser stu = userMapper.selectById(targetBed.getStudentId());
        DormRoom room = roomMapper.selectById(targetBed.getRoomId());
        boolean hasPreReport = allRecords.stream().anyMatch(r ->
                Objects.equals(r.getStudentId(), stu.getId())
                        && r.getDescription() != null
                        && r.getDescription().contains("[学生报备]")
        );

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("studentName", stu.getRealName());
        res.put("roomNo", room != null ? room.getRoomNo() : "");
        res.put("hasPreReport", hasPreReport);

        if (hasPreReport) {
            res.put("auditResult", "EXEMPTED");
            return Result.success("门禁核验完成：检测到【" + stu.getRealName() + "】已提交晚归报备，自动标记为合规通行（免除恶意晚归扣分）", res);
        } else {
            DisciplineRecord record = new DisciplineRecord();
            record.setStudentId(stu.getId());
            record.setRoomId(targetBed.getRoomId());
            record.setRecordType("LATE_RETURN");
            record.setDeductScore(5);
            record.setOccurTime(LocalDateTime.now());
            record.setDescription("[23:00人脸门禁考勤稽核] 超过门禁时间未归且无请假报备记录，系统自动抓拍预警");
            record.setStatus(1);
            disciplineMapper.insert(record);
            smartEvaluationService.evaluateAllRooms();
            res.put("auditResult", "WARNED");
            res.put("record", record);
            return Result.success("门禁智能稽核完成：发现【" + stu.getRealName() + "】未报备超时晚归，已自动生成预警记录并联动更新文明分！", res);
        }
    }

    @GetMapping("/adjust-list")
    public Result<List<Map<String, Object>>> listAdjustApplies(@RequestParam(required = false) Long studentId) {
        LambdaQueryWrapper<DormAdjustApply> wrapper = new LambdaQueryWrapper<>();
        if (studentId != null) wrapper.eq(DormAdjustApply::getStudentId, studentId);
        wrapper.orderByDesc(DormAdjustApply::getCreateTime);
        List<DormAdjustApply> list = adjustApplyMapper.selectList(wrapper);

        Map<Long, SysUser> userMap = userMapper.selectList(null).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream().collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream().collect(Collectors.toMap(DormBuilding::getId, b -> b));
        Map<Long, StudentHabitProfile> profileMap = habitProfileMapper.selectList(null).stream()
                .collect(Collectors.toMap(StudentHabitProfile::getStudentId, p -> p, (x, y) -> x));
        List<DormBed> allOccupiedBeds = bedMapper.selectList(new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1));

        List<Map<String, Object>> res = new ArrayList<>();
        for (DormAdjustApply a : list) {
            SysUser stu = userMap.get(a.getStudentId());
            DormRoom oldR = roomMap.get(a.getOldRoomId());
            DormBuilding oldB = oldR != null ? buildingMap.get(oldR.getBuildingId()) : null;
            DormRoom targetR = a.getTargetRoomId() != null ? roomMap.get(a.getTargetRoomId()) : null;
            DormBuilding targetB = targetR != null ? buildingMap.get(targetR.getBuildingId()) : null;

            // 计算调入目标寝室后的预计六维契合度
            Double predictedHarmony = null;
            if ("ADJUST".equals(a.getApplyType()) && targetR != null) {
                List<StudentHabitProfile> targetProfiles = new ArrayList<>();
                for (DormBed b : allOccupiedBeds) {
                    if (Objects.equals(b.getRoomId(), targetR.getId()) && !Objects.equals(b.getStudentId(), a.getStudentId())) {
                        StudentHabitProfile rp = profileMap.get(b.getStudentId());
                        if (rp != null) targetProfiles.add(rp);
                    }
                }
                StudentHabitProfile myP = profileMap.get(a.getStudentId());
                if (myP != null) targetProfiles.add(myP);
                predictedHarmony = smartAllocationService.calculateGroupHarmony(targetProfiles);
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("studentId", a.getStudentId());
            m.put("studentName", stu != null ? stu.getRealName() : "");
            m.put("studentNo", stu != null ? stu.getUsername() : "");
            m.put("applyType", a.getApplyType());
            m.put("oldRoomId", a.getOldRoomId());
            m.put("oldRoomName", (oldB != null ? oldB.getBuildingName() : "") + "-" + (oldR != null ? oldR.getRoomNo() : "") + " (" + a.getOldBedNo() + "号床)");
            m.put("targetRoomId", a.getTargetRoomId());
            m.put("targetBedNo", a.getTargetBedNo());
            m.put("targetRoomName", targetR != null ? ((targetB != null ? targetB.getBuildingName() : "") + "-" + targetR.getRoomNo() + " (" + a.getTargetBedNo() + "号床)") : "退宿离校");
            m.put("predictedHarmony", predictedHarmony);
            m.put("reason", a.getReason());
            m.put("status", a.getStatus());
            m.put("reviewComment", a.getReviewComment());
            m.put("reviewTime", a.getReviewTime());
            m.put("createTime", a.getCreateTime());
            res.add(m);
        }
        return Result.success(res);
    }

    @PostMapping("/adjust-apply")
    public Result<DormAdjustApply> submitAdjustApply(@RequestBody DormAdjustApply apply) {
        if (apply.getApplyType() == null || apply.getApplyType().isEmpty()) {
            apply.setApplyType("ADJUST");
        }
        apply.setStatus("PENDING");
        apply.setCreateTime(LocalDateTime.now());
        adjustApplyMapper.insert(apply);
        String msg = "CHECKOUT".equals(apply.getApplyType())
                ? "退宿离校申请已提交，请等待宿管中心审批核验"
                : "调宿申请已提交，请等待管理员审批";
        return Result.success(msg, apply);
    }

    @PostMapping("/adjust-review")
    @Transactional(rollbackFor = Exception.class)
    public Result<DormAdjustApply> reviewAdjustApply(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.get("status").toString();
        String comment = body.get("reviewComment") != null ? body.get("reviewComment").toString() : "";

        DormAdjustApply apply = adjustApplyMapper.selectById(id);
        if (apply == null) return Result.error("申请记录不存在");

        apply.setStatus(status);
        apply.setReviewComment(comment);
        apply.setReviewTime(LocalDateTime.now());
        adjustApplyMapper.updateById(apply);

        // 若审批通过，自动联动更新原床位、目标床位状态以及新旧寝室的六维契合度
        if ("APPROVED".equals(status)) {
            DormBed oldBed = bedMapper.selectOne(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getRoomId, apply.getOldRoomId())
                            .eq(DormBed::getBedNo, apply.getOldBedNo())
            );
            if (oldBed != null) {
                oldBed.setStudentId(null);
                oldBed.setCheckInDate(null);
                oldBed.setStatus(0);
                bedMapper.updateById(oldBed);
            }

            if ("CHECKOUT".equals(apply.getApplyType())) {
                StudentHabitProfile hp = habitProfileMapper.selectOne(
                        new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, apply.getStudentId())
                );
                if (hp != null) {
                    hp.setIsAllocated(0);
                    habitProfileMapper.updateById(hp);
                }
            } else if ("ADJUST".equals(apply.getApplyType()) && apply.getTargetRoomId() != null && apply.getTargetBedNo() != null) {
                DormBed targetBed = bedMapper.selectOne(
                        new LambdaQueryWrapper<DormBed>()
                                .eq(DormBed::getRoomId, apply.getTargetRoomId())
                                .eq(DormBed::getBedNo, apply.getTargetBedNo())
                );
                if (targetBed != null) {
                    targetBed.setStudentId(apply.getStudentId());
                    targetBed.setCheckInDate(LocalDate.now());
                    targetBed.setStatus(1);
                    bedMapper.updateById(targetBed);
                }
            }

            // 重新计算原寝室与目标寝室的入住人数与室友契合度
            smartAllocationService.recalculateRoomHarmony(apply.getOldRoomId());
            if (apply.getTargetRoomId() != null) {
                smartAllocationService.recalculateRoomHarmony(apply.getTargetRoomId());
            }
        }
        return Result.success("审批处理完成，床位数据与寝室契合度已自动同步", apply);
    }

    @GetMapping("/visitors")
    public Result<List<Map<String, Object>>> listVisitors() {
        List<VisitorRecord> list = visitorMapper.selectList(
                new LambdaQueryWrapper<VisitorRecord>().orderByDesc(VisitorRecord::getEnterTime)
        );
        Map<Long, SysUser> userMap = userMapper.selectList(null).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream().collect(Collectors.toMap(DormBuilding::getId, b -> b));

        List<Map<String, Object>> res = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (VisitorRecord v : list) {
            SysUser stu = userMap.get(v.getVisitStudentId());
            DormBuilding b = buildingMap.get(v.getBuildingId());
            long stayMinutes = 0;
            if (v.getEnterTime() != null) {
                LocalDateTime end = v.getLeaveTime() != null ? v.getLeaveTime() : now;
                stayMinutes = Math.max(1, Duration.between(v.getEnterTime(), end).toMinutes());
            }
            boolean overstay = v.getStatus() != null && v.getStatus() == 0 && stayMinutes > 120;
            String passCode = String.format("VP-%06d", Math.abs((v.getId() != null ? v.getId() : 1L) * 13579L + 802100L) % 1000000L);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("passCode", passCode);
            m.put("visitorName", v.getVisitorName());
            m.put("visitorPhone", v.getVisitorPhone());
            m.put("idCardMask", v.getIdCardMask());
            m.put("visitStudentId", v.getVisitStudentId());
            m.put("visitStudentName", stu != null ? stu.getRealName() : "");
            m.put("buildingId", v.getBuildingId());
            m.put("buildingName", b != null ? b.getBuildingName() : "");
            m.put("roomNo", v.getRoomNo());
            m.put("relation", v.getRelation());
            m.put("visitReason", v.getVisitReason());
            m.put("enterTime", v.getEnterTime());
            m.put("leaveTime", v.getLeaveTime());
            m.put("stayMinutes", stayMinutes);
            m.put("overstayFlag", overstay ? 1 : 0);
            m.put("status", v.getStatus());
            res.add(m);
        }
        return Result.success(res);
    }

    @PostMapping("/visitor")
    public Result<VisitorRecord> createVisitor(@RequestBody VisitorRecord v) {
        v.setEnterTime(LocalDateTime.now());
        v.setStatus(0);
        visitorMapper.insert(v);
        return Result.success("来访登记成功", v);
    }

    @PostMapping("/visitor-leave/{id}")
    public Result<VisitorRecord> leaveVisitor(@PathVariable Long id) {
        VisitorRecord v = visitorMapper.selectById(id);
        if (v != null) {
            v.setLeaveTime(LocalDateTime.now());
            v.setStatus(1);
            visitorMapper.updateById(v);
        }
        return Result.success("访客已签离", v);
    }

    @GetMapping("/notices")
    public Result<List<SysNotice>> listNotices() {
        return Result.success(noticeMapper.selectList(
                new LambdaQueryWrapper<SysNotice>()
                        .orderByDesc(SysNotice::getIsTop)
                        .orderByDesc(SysNotice::getCreateTime)
        ));
    }

    @PostMapping("/notice")
    public Result<SysNotice> saveNotice(@RequestBody SysNotice notice) {
        if (notice.getId() == null) {
            notice.setCreateTime(LocalDateTime.now());
            if (notice.getViewCount() == null) notice.setViewCount(1);
            noticeMapper.insert(notice);
        } else {
            noticeMapper.updateById(notice);
        }
        return Result.success("公告发布成功", notice);
    }

    @DeleteMapping("/notice/{id}")
    public Result<String> deleteNotice(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return Result.success("公告已删除", "OK");
    }
}
