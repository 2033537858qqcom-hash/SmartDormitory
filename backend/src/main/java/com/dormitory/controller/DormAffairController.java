package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.hutool.json.JSONUtil;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.PassCodeUtil;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import com.dormitory.service.SmartEvaluationService;
import com.dormitory.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

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
    private final BuildingScopeService buildingScope;
    private final UserMessageService messageService;

    @GetMapping("/evaluation")
    public Result<List<Map<String, Object>>> getCivilizedEvaluation(HttpServletRequest request) {
        List<Map<String, Object>> all = smartEvaluationService.evaluateAllRooms();
        Set<Long> scope = buildingScope.resolve(request);
        if (scope == null) return Result.success(all);
        if (scope.isEmpty()) return Result.success(List.of());
        return Result.success(all.stream().filter(m -> {
            Object bid = m.get("buildingId");
            return bid != null && scope.contains(Long.valueOf(bid.toString()));
        }).collect(Collectors.toList()));
    }

    @PostMapping("/reward-civilized")
    public Result<Map<String, Object>> rewardCivilizedRooms(@RequestBody(required = false) Map<String, Object> body) {
        BigDecimal amount = (body != null && body.get("amount") != null)
                ? new BigDecimal(body.get("amount").toString()) : BigDecimal.valueOf(30.00);
        Map<String, Object> res = smartEvaluationService.rewardFiveStarRooms(amount);
        return Result.success("已为五星文明示范寝室发放电费奖励补贴并同步发布红榜公示！", res);
    }

    @GetMapping("/hygiene")
    public Result<List<Map<String, Object>>> listHygiene(HttpServletRequest request) {
        LambdaQueryWrapper<HygieneInspection> hq = new LambdaQueryWrapper<HygieneInspection>()
                .orderByDesc(HygieneInspection::getCheckDate);
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null) {
            if (scope.isEmpty()) return Result.success(List.of());
            hq.in(HygieneInspection::getBuildingId, scope);
        }
        List<HygieneInspection> list = hygieneMapper.selectList(hq);
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
            m.put("photoUrls", h.getPhotoUrls());
            res.add(m);
        }
        return Result.success(res);
    }

    @PostMapping("/hygiene")
    public Result<HygieneInspection> saveHygiene(@RequestBody HygieneInspection h, HttpServletRequest request) {
        DormRoom room = roomMapper.selectById(h.getRoomId());
        if (room != null) {
            h.setBuildingId(room.getBuildingId());
        }
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), h.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<HygieneInspection> typed = (Result<HygieneInspection>) denied;
            return typed;
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

    /** 寝室卫生分近 N 次趋势（默认最近 12 条） */
    @GetMapping("/hygiene-trend")
    public Result<Map<String, Object>> hygieneTrend(@RequestParam Long roomId,
                                                    @RequestParam(required = false, defaultValue = "12") Integer limit,
                                                    HttpServletRequest request) {
        DormRoom room = roomMapper.selectById(roomId);
        if (room == null) return Result.error("寝室不存在");
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), room.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<Map<String, Object>> typed = (Result<Map<String, Object>>) denied;
            return typed;
        }
        int lim = limit == null || limit < 1 ? 12 : Math.min(limit, 52);
        List<HygieneInspection> list = hygieneMapper.selectList(
                new LambdaQueryWrapper<HygieneInspection>()
                        .eq(HygieneInspection::getRoomId, roomId)
                        .orderByAsc(HygieneInspection::getCheckDate)
                        .orderByAsc(HygieneInspection::getId)
        );
        if (list.size() > lim) {
            list = list.subList(list.size() - lim, list.size());
        }
        DormBuilding b = buildingMapper.selectById(room.getBuildingId());
        List<Map<String, Object>> points = new ArrayList<>();
        for (HygieneInspection h : list) {
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("id", h.getId());
            pt.put("checkWeek", h.getCheckWeek());
            pt.put("checkDate", h.getCheckDate());
            pt.put("totalScore", h.getTotalScore());
            pt.put("grade", h.getGrade());
            points.add(pt);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("roomId", roomId);
        res.put("roomNo", room.getRoomNo());
        res.put("buildingName", b != null ? b.getBuildingName() : "");
        res.put("points", points);
        return Result.success(res);
    }

    @GetMapping("/discipline")
    public Result<List<Map<String, Object>>> listDiscipline(@RequestParam(required = false) Long studentId,
                                                            HttpServletRequest request) {
        LambdaQueryWrapper<DisciplineRecord> wrapper = new LambdaQueryWrapper<>();
        if (studentId != null) wrapper.eq(DisciplineRecord::getStudentId, studentId);
        wrapper.orderByDesc(DisciplineRecord::getOccurTime);
        List<DisciplineRecord> list = disciplineMapper.selectList(wrapper);
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null) {
            if (scope.isEmpty()) return Result.success(List.of());
            Set<Long> roomIds = roomMapper.selectList(
                    new LambdaQueryWrapper<DormRoom>().in(DormRoom::getBuildingId, scope)
            ).stream().map(DormRoom::getId).collect(Collectors.toSet());
            list = list.stream().filter(d -> d.getRoomId() != null && roomIds.contains(d.getRoomId())).collect(Collectors.toList());
        }

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
            m.put("appealProofType", d.getAppealProofType());
            m.put("appealWitness", d.getAppealWitness());
            m.put("appealAttachments", d.getAppealAttachments());
            res.add(m);
        }
        return Result.success(res);
    }

    @PostMapping("/discipline")
    public Result<DisciplineRecord> saveDiscipline(@RequestBody DisciplineRecord d, HttpServletRequest request) {
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            return Result.error(403, "学生无权登记违纪记录");
        }
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
        if (d.getRoomId() != null) {
            DormRoom scopedRoom = roomMapper.selectById(d.getRoomId());
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(
                    buildingScope.resolve(request), scopedRoom != null ? scopedRoom.getBuildingId() : null);
            if (denied != null) {
                @SuppressWarnings("unchecked")
                Result<DisciplineRecord> typed = (Result<DisciplineRecord>) denied;
                return typed;
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
    public Result<DisciplineRecord> appealDiscipline(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long id = Long.valueOf(body.get("id").toString());
        String appealReason = body.get("appealReason") != null ? body.get("appealReason").toString().trim() : "因科研/急事晚归，申请核销扣分";
        String proofType = body.get("appealProofType") != null ? body.get("appealProofType").toString().trim() : null;
        String witness = body.get("appealWitness") != null ? body.get("appealWitness").toString().trim() : null;
        String attachments = body.get("appealAttachments") != null ? body.get("appealAttachments").toString().trim() : null;
        DisciplineRecord d = disciplineMapper.selectById(id);
        if (d == null) {
            return Result.error("未找到该条记录");
        }
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))
                && !Objects.equals(d.getStudentId(), request.getAttribute("currentUserId"))) {
            return Result.error(403, "只能申诉本人的违纪记录");
        }
        String baseDesc = d.getDescription() != null ? d.getDescription().split(" \\| \\[")[0] : "";
        d.setDescription(baseDesc + " | [学生申诉] " + appealReason);
        if (proofType != null && !proofType.isEmpty()) {
            d.setAppealProofType(proofType);
        }
        if (witness != null && !witness.isEmpty()) {
            d.setAppealWitness(witness);
        }
        if (attachments != null && !attachments.isEmpty()) {
            d.setAppealAttachments(attachments);
        }
        d.setStatus(3);
        disciplineMapper.updateById(d);
        return Result.success("申诉已提交宿管中心审核", d);
    }

    /**
     * 宿管/管理员审核学生违纪申诉（通过则置为 2-申诉撤销并自动恢复寝室文明分）
     */
    @PostMapping("/discipline-review")
    @Transactional(rollbackFor = Exception.class)
    public Result<DisciplineRecord> reviewDisciplineAppeal(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long id = Long.valueOf(body.get("id").toString());
        boolean approved = body.get("approved") == null || Boolean.parseBoolean(body.get("approved").toString());
        String reviewNote = body.get("reviewNote") != null ? body.get("reviewNote").toString().trim()
                : (approved ? "证明属实，予以核销免扣分" : "理由不充分，维持原记录");

        DisciplineRecord d = disciplineMapper.selectById(id);
        if (d == null) {
            return Result.error("未找到该条记录");
        }
        if (d.getRoomId() != null) {
            DormRoom rr = roomMapper.selectById(d.getRoomId());
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(
                    buildingScope.resolve(request), rr != null ? rr.getBuildingId() : null);
            if (denied != null) {
                @SuppressWarnings("unchecked")
                Result<DisciplineRecord> typed = (Result<DisciplineRecord>) denied;
                return typed;
            }
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
        if (d.getStudentId() != null) {
            messageService.push(d.getStudentId(),
                    approved ? "违纪申诉已通过" : "违纪申诉未通过",
                    approved ? ("宿管已核销：" + reviewNote) : ("申诉驳回：" + reviewNote),
                    "APPEAL", String.valueOf(d.getId()));
        }
        return Result.success(approved ? "申诉审核通过，已撤销扣分并恢复寝室文明积分！" : "已驳回申诉，维持原扣分记录", d);
    }

    /**
     * 23:00 门禁考勤实时统计（可按楼栋；宿管自动限制管辖范围）
     */
    @GetMapping("/curfew-stats")
    public Result<Map<String, Object>> getCurfewStats(@RequestParam(required = false) Long buildingId,
                                                      HttpServletRequest request) {
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null && buildingId != null && !scope.contains(buildingId)) {
            return Result.error(403, "只能查看管辖楼栋的门禁统计");
        }
        Set<Long> buildingFilter = null;
        if (buildingId != null) {
            buildingFilter = Set.of(buildingId);
        } else if (scope != null) {
            buildingFilter = scope;
        }

        Set<Long> roomIds = null;
        if (buildingFilter != null) {
            if (buildingFilter.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("totalCheckedIn", 0);
                empty.put("normalInDormCount", 0);
                empty.put("reportedLateCount", 0);
                empty.put("unreportedLateCount", 0);
                empty.put("appealPendingCount", 0);
                empty.put("buildingId", buildingId);
                return Result.success(empty);
            }
            roomIds = roomMapper.selectList(
                    new LambdaQueryWrapper<DormRoom>().in(DormRoom::getBuildingId, buildingFilter)
            ).stream().map(DormRoom::getId).collect(Collectors.toSet());
        }

        LambdaQueryWrapper<DormBed> bedQ = new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1);
        if (roomIds != null) {
            if (roomIds.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("totalCheckedIn", 0);
                empty.put("normalInDormCount", 0);
                empty.put("reportedLateCount", 0);
                empty.put("unreportedLateCount", 0);
                empty.put("appealPendingCount", 0);
                empty.put("buildingId", buildingId);
                return Result.success(empty);
            }
            bedQ.in(DormBed::getRoomId, roomIds);
        }
        long totalCheckedIn = bedMapper.selectCount(bedQ);

        List<DisciplineRecord> allRecords = disciplineMapper.selectList(null);
        if (roomIds != null) {
            Set<Long> finalRoomIds = roomIds;
            allRecords = allRecords.stream()
                    .filter(r -> r.getRoomId() != null && finalRoomIds.contains(r.getRoomId()))
                    .collect(Collectors.toList());
        }
        LocalDate today = LocalDate.now();
        long reportedLateCount = allRecords.stream()
                .filter(r -> r.getDescription() != null && r.getDescription().contains("[学生报备]")
                        && r.getOccurTime() != null && r.getOccurTime().toLocalDate().equals(today))
                .count();
        long unreportedLateCount = allRecords.stream()
                .filter(r -> "LATE_RETURN".equals(r.getRecordType())
                        && (r.getDescription() == null || !r.getDescription().contains("[学生报备]"))
                        && r.getStatus() != null && r.getStatus() != 2
                        && r.getOccurTime() != null && r.getOccurTime().toLocalDate().equals(today))
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
        data.put("buildingId", buildingId);
        return Result.success(data);
    }

    /**
     * 按楼栋批量门禁稽核：对比在住床位与当晚晚归报备，产出合规/豁免/预警三类结果。
     * body.buildingId 可选；宿管仅可稽核管辖楼栋。不再默认硬编码 roomId=2。
     */
    @PostMapping("/curfew-check")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> runCurfewAudit(@RequestBody(required = false) Map<String, Object> body,
                                                      HttpServletRequest request) {
        Long buildingId = (body != null && body.get("buildingId") != null)
                ? Long.valueOf(body.get("buildingId").toString()) : null;
        Long targetStudentId = (body != null && body.get("studentId") != null)
                ? Long.valueOf(body.get("studentId").toString()) : null;

        Set<Long> scope = buildingScope.resolve(request);
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(scope, buildingId);
        // buildingId 为空时：宿管扫全部管辖楼栋；管理员扫全校（或指定楼栋）
        if (buildingId != null && denied != null) {
            @SuppressWarnings("unchecked")
            Result<Map<String, Object>> typed = (Result<Map<String, Object>>) denied;
            return typed;
        }

        Set<Long> buildingFilter;
        if (buildingId != null) {
            buildingFilter = Set.of(buildingId);
        } else if (scope != null) {
            buildingFilter = scope;
        } else {
            buildingFilter = null; // 全校
        }

        if (buildingFilter != null && buildingFilter.isEmpty()) {
            return Result.error("当前账号无管辖楼栋，无法执行门禁稽核");
        }

        Set<Long> roomIds = null;
        if (buildingFilter != null) {
            roomIds = roomMapper.selectList(
                    new LambdaQueryWrapper<DormRoom>().in(DormRoom::getBuildingId, buildingFilter)
            ).stream().map(DormRoom::getId).collect(Collectors.toSet());
            if (roomIds.isEmpty()) {
                return Result.error("所选楼栋暂无房间");
            }
        }

        LambdaQueryWrapper<DormBed> bedQ = new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1);
        if (roomIds != null) {
            bedQ.in(DormBed::getRoomId, roomIds);
        }
        List<DormBed> occupiedBeds = bedMapper.selectList(bedQ);
        if (targetStudentId != null) {
            occupiedBeds = occupiedBeds.stream()
                    .filter(b -> Objects.equals(b.getStudentId(), targetStudentId))
                    .collect(Collectors.toList());
        }
        if (occupiedBeds.isEmpty()) {
            return Result.error("当前范围内无在住学生可供门禁考勤稽核");
        }

        List<DisciplineRecord> allRecords = disciplineMapper.selectList(null);
        LocalDate today = LocalDate.now();
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b));

        List<Map<String, Object>> exempted = new ArrayList<>();
        List<Map<String, Object>> warned = new ArrayList<>();
        List<Map<String, Object>> normal = new ArrayList<>();
        int createdWarnings = 0;

        for (DormBed bed : occupiedBeds) {
            if (bed.getStudentId() == null) continue;
            SysUser stu = userMapper.selectById(bed.getStudentId());
            if (stu == null) continue;
            DormRoom room = roomMap.get(bed.getRoomId());
            DormBuilding building = room != null ? buildingMap.get(room.getBuildingId()) : null;

            boolean hasPreReport = allRecords.stream().anyMatch(r ->
                    Objects.equals(r.getStudentId(), stu.getId())
                            && r.getDescription() != null
                            && r.getDescription().contains("[学生报备]")
                            && r.getOccurTime() != null
                            && r.getOccurTime().toLocalDate().equals(today)
            );
            boolean alreadyWarnedToday = allRecords.stream().anyMatch(r ->
                    Objects.equals(r.getStudentId(), stu.getId())
                            && "LATE_RETURN".equals(r.getRecordType())
                            && r.getDescription() != null
                            && r.getDescription().contains("[23:00人脸门禁考勤稽核]")
                            && r.getOccurTime() != null
                            && r.getOccurTime().toLocalDate().equals(today)
                            && r.getStatus() != null && r.getStatus() != 2
            );

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", stu.getId());
            row.put("studentName", stu.getRealName());
            row.put("roomId", bed.getRoomId());
            row.put("roomNo", room != null ? room.getRoomNo() : "");
            row.put("buildingId", room != null ? room.getBuildingId() : null);
            row.put("buildingName", building != null ? building.getBuildingName() : "");
            row.put("hasPreReport", hasPreReport);

            if (hasPreReport) {
                row.put("auditResult", "EXEMPTED");
                exempted.add(row);
            } else if (alreadyWarnedToday) {
                row.put("auditResult", "WARNED");
                row.put("duplicate", true);
                warned.add(row);
            } else {
                // 演示批量稽核：未报备视为预警并生成记录（模拟门禁未刷脸归寝）
                DisciplineRecord record = new DisciplineRecord();
                record.setStudentId(stu.getId());
                record.setRoomId(bed.getRoomId());
                record.setRecordType("LATE_RETURN");
                record.setDeductScore(5);
                record.setOccurTime(LocalDateTime.now());
                record.setDescription("[23:00人脸门禁考勤稽核] 超过门禁时间未归且无请假报备记录，系统自动抓拍预警");
                record.setStatus(1);
                disciplineMapper.insert(record);
                allRecords.add(record);
                createdWarnings++;
                row.put("auditResult", "WARNED");
                row.put("recordId", record.getId());
                warned.add(row);
            }
        }

        if (createdWarnings > 0) {
            smartEvaluationService.evaluateAllRooms();
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("buildingId", buildingId);
        res.put("scannedCount", exempted.size() + warned.size() + normal.size());
        res.put("exemptedCount", exempted.size());
        res.put("warnedCount", warned.size());
        res.put("normalCount", normal.size());
        res.put("createdWarnings", createdWarnings);
        res.put("exempted", exempted);
        res.put("warned", warned);
        res.put("normal", normal);
        String msg = String.format(
                "楼栋批量门禁稽核完成：扫描 %d 人，豁免 %d、预警 %d（新建预警 %d 条）",
                (Integer) res.get("scannedCount"), exempted.size(), warned.size(), createdWarnings);
        return Result.success(msg, res);
    }

    @GetMapping("/adjust-list")
    public Result<List<Map<String, Object>>> listAdjustApplies(@RequestParam(required = false) Long studentId,
                                                               HttpServletRequest request) {
        LambdaQueryWrapper<DormAdjustApply> wrapper = new LambdaQueryWrapper<>();
        if (studentId != null) wrapper.eq(DormAdjustApply::getStudentId, studentId);
        wrapper.orderByDesc(DormAdjustApply::getCreateTime);
        List<DormAdjustApply> list = adjustApplyMapper.selectList(wrapper);
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null) {
            if (scope.isEmpty()) return Result.success(List.of());
            Set<Long> roomIds = roomMapper.selectList(
                    new LambdaQueryWrapper<DormRoom>().in(DormRoom::getBuildingId, scope)
            ).stream().map(DormRoom::getId).collect(Collectors.toSet());
            list = list.stream().filter(a -> a.getOldRoomId() != null && roomIds.contains(a.getOldRoomId())).collect(Collectors.toList());
        }

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
            m.put("checkoutChecklist", a.getCheckoutChecklist());
            m.put("reviewTime", a.getReviewTime());
            m.put("createTime", a.getCreateTime());
            res.add(m);
        }
        return Result.success(res);
    }

    @PostMapping("/adjust-apply")
    public Result<DormAdjustApply> submitAdjustApply(@RequestBody DormAdjustApply apply, HttpServletRequest request) {
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            Long studentId = (Long) request.getAttribute("currentUserId");
            DormBed current = bedMapper.selectOne(new LambdaQueryWrapper<DormBed>()
                    .eq(DormBed::getStudentId, studentId).eq(DormBed::getStatus, 1).last("LIMIT 1"));
            if (current == null) return Result.error("未找到当前在住床位，无法提交申请");
            apply.setStudentId(studentId);
            apply.setOldRoomId(current.getRoomId());
            apply.setOldBedNo(current.getBedNo());
        }
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
    public Result<DormAdjustApply> reviewAdjustApply(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.get("status").toString();
        String comment = body.get("reviewComment") != null ? body.get("reviewComment").toString() : "";

        if (!Set.of("APPROVED", "REJECTED").contains(status)) return Result.error("无效的审批结果");
        DormAdjustApply apply = adjustApplyMapper.selectOne(new LambdaQueryWrapper<DormAdjustApply>()
                .eq(DormAdjustApply::getId, id).last("FOR UPDATE"));
        if (apply == null) return Result.error("申请记录不存在");
        if (!"PENDING".equals(apply.getStatus())) return Result.error("申请已处理，请勿重复审批");
        if ("APPROVED".equals(status) && "CHECKOUT".equals(apply.getApplyType())) {
            Object raw = body.get("handover");
            if (!(raw instanceof Map<?, ?> handover) || !Boolean.TRUE.equals(handover.get("keyReturned"))
                    || !Boolean.TRUE.equals(handover.get("facilitiesChecked")) || !Boolean.TRUE.equals(handover.get("roomCleared")))
                return Result.error("请完成退宿交接清单：钥匙、设施和寝室清理");
            String note = handover.get("note") == null ? "" : handover.get("note").toString().trim();
            if (note.length() > 300) return Result.error("交接备注过长");
            apply.setCheckoutChecklist(JSONUtil.toJsonStr(Map.of("keyReturned", true, "facilitiesChecked", true, "roomCleared", true, "note", note)));
        }
        if (apply.getOldRoomId() != null) {
            DormRoom oldRoom = roomMapper.selectById(apply.getOldRoomId());
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(
                    buildingScope.resolve(request), oldRoom != null ? oldRoom.getBuildingId() : null);
            if (denied != null) {
                @SuppressWarnings("unchecked")
                Result<DormAdjustApply> typed = (Result<DormAdjustApply>) denied;
                return typed;
            }
        }

        // 若审批通过，自动联动更新原床位、目标床位状态以及新旧寝室的六维契合度
        if ("APPROVED".equals(status)) {
            if (!Set.of("ADJUST", "CHECKOUT").contains(apply.getApplyType())) return Result.error("无效的申请类型");
            DormBed oldBed = bedMapper.selectOne(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getRoomId, apply.getOldRoomId())
                            .eq(DormBed::getBedNo, apply.getOldBedNo())
                            .last("FOR UPDATE")
            );
            if (oldBed == null || !Objects.equals(oldBed.getStudentId(), apply.getStudentId())
                    || !Integer.valueOf(1).equals(oldBed.getStatus())) {
                return Result.error("原床位已变化，请重新提交申请");
            }
            DormBed targetBed = null;
            if ("ADJUST".equals(apply.getApplyType())) {
                if (apply.getTargetRoomId() == null || apply.getTargetBedNo() == null) return Result.error("请选择目标床位");
                targetBed = bedMapper.selectOne(new LambdaQueryWrapper<DormBed>()
                        .eq(DormBed::getRoomId, apply.getTargetRoomId())
                        .eq(DormBed::getBedNo, apply.getTargetBedNo()).last("FOR UPDATE"));
                if (targetBed == null || !Integer.valueOf(0).equals(targetBed.getStatus()) || targetBed.getStudentId() != null) {
                    return Result.error("目标床位不可用，请重新选择");
                }
                DormRoom targetRoom = roomMapper.selectById(apply.getTargetRoomId());
                DormBuilding targetBuilding = targetRoom == null ? null : buildingMapper.selectById(targetRoom.getBuildingId());
                SysUser student = userMapper.selectById(apply.getStudentId());
                if (targetRoom == null || targetBuilding == null || student == null) return Result.error("目标房间或学生不存在");
                if (targetBuilding.getGenderType() != null && student.getGender() != null
                        && !Objects.equals(targetBuilding.getGenderType(), student.getGender())) return Result.error("目标楼栋与学生性别不匹配");
            }
            int released = bedMapper.update(null, new LambdaUpdateWrapper<DormBed>()
                    .eq(DormBed::getId, oldBed.getId()).eq(DormBed::getStudentId, apply.getStudentId()).eq(DormBed::getStatus, 1)
                    .set(DormBed::getStudentId, null).set(DormBed::getCheckInDate, null).set(DormBed::getStatus, 0));
            if (released != 1) throw new IllegalStateException("原床位已变化，请重试");
            if ("CHECKOUT".equals(apply.getApplyType())) {
                StudentHabitProfile hp = habitProfileMapper.selectOne(
                        new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, apply.getStudentId())
                );
                if (hp != null) {
                    hp.setIsAllocated(0);
                    habitProfileMapper.updateById(hp);
                }
            } else {
                int assigned = bedMapper.update(null, new LambdaUpdateWrapper<DormBed>()
                        .eq(DormBed::getId, targetBed.getId()).eq(DormBed::getStatus, 0).isNull(DormBed::getStudentId)
                        .set(DormBed::getStudentId, apply.getStudentId()).set(DormBed::getCheckInDate, LocalDate.now()).set(DormBed::getStatus, 1));
                if (assigned != 1) throw new IllegalStateException("目标床位已被占用，请重试");
            }

            // 重新计算原寝室与目标寝室的入住人数与室友契合度
            smartAllocationService.recalculateRoomHarmony(apply.getOldRoomId());
            if (apply.getTargetRoomId() != null) {
                smartAllocationService.recalculateRoomHarmony(apply.getTargetRoomId());
            }
        }
        apply.setStatus(status);
        apply.setReviewComment(comment);
        apply.setReviewerId((Long) request.getAttribute("currentUserId"));
        apply.setReviewTime(LocalDateTime.now());
        adjustApplyMapper.updateById(apply);
        if (apply.getStudentId() != null) {
            String title = "APPROVED".equals(status) ? "调宿/退宿申请已批准" : "调宿/退宿申请已驳回";
            messageService.push(apply.getStudentId(), title,
                    comment == null || comment.isEmpty() ? title : comment,
                    "ADJUST", String.valueOf(apply.getId()));
        }
        return Result.success("审批处理完成，床位数据与寝室契合度已自动同步", apply);
    }

    @GetMapping("/visitors")
    public Result<List<Map<String, Object>>> listVisitors(HttpServletRequest request) {
        LambdaQueryWrapper<VisitorRecord> vq = new LambdaQueryWrapper<VisitorRecord>()
                .orderByDesc(VisitorRecord::getEnterTime);
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null) {
            if (scope.isEmpty()) return Result.success(List.of());
            vq.in(VisitorRecord::getBuildingId, scope);
        }
        List<VisitorRecord> list = visitorMapper.selectList(vq);
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
            String passCode = PassCodeUtil.fromVisitorId(v.getId());

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
    public Result<VisitorRecord> createVisitor(@RequestBody VisitorRecord v, HttpServletRequest request) {
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            v.setVisitStudentId((Long) request.getAttribute("currentUserId"));
            DormBed bed = bedMapper.selectOne(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getStudentId, v.getVisitStudentId())
                            .eq(DormBed::getStatus, 1)
                            .last("LIMIT 1")
            );
            if (bed != null) {
                DormRoom room = roomMapper.selectById(bed.getRoomId());
                if (room != null) {
                    v.setBuildingId(room.getBuildingId());
                    if (v.getRoomNo() == null || v.getRoomNo().isEmpty()) {
                        v.setRoomNo(room.getRoomNo());
                    }
                }
            }
        } else {
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), v.getBuildingId());
            if (denied != null) {
                @SuppressWarnings("unchecked")
                Result<VisitorRecord> typed = (Result<VisitorRecord>) denied;
                return typed;
            }
        }
        v.setEnterTime(LocalDateTime.now());
        v.setStatus(0);
        visitorMapper.insert(v);
        return Result.success("来访登记成功", v);
    }


    @PostMapping("/visitor-verify")
    public Result<Map<String, Object>> verifyVisitorPass(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String raw = body != null && body.get("passCode") != null ? body.get("passCode").toString() : "";
        String code = PassCodeUtil.normalize(raw);
        if (code.isEmpty() || !code.startsWith("VP-")) {
            return Result.error("请输入有效通行码（如 VP-802101）");
        }
        boolean checkOut = body != null && body.get("checkOut") != null && Boolean.parseBoolean(body.get("checkOut").toString());
        Set<Long> scope = buildingScope.resolve(request);
        List<VisitorRecord> list = visitorMapper.selectList(new LambdaQueryWrapper<VisitorRecord>().orderByDesc(VisitorRecord::getEnterTime));
        VisitorRecord matched = null;
        for (VisitorRecord v : list) {
            if (code.equals(PassCodeUtil.fromVisitorId(v.getId()))) {
                matched = v;
                break;
            }
        }
        if (matched == null) {
            return Result.error("未找到匹配的通行码，请核对后重试");
        }
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(scope, matched.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<Map<String, Object>> typed = (Result<Map<String, Object>>) denied;
            return typed;
        }
        SysUser stu = userMapper.selectById(matched.getVisitStudentId());
        DormBuilding b = buildingMapper.selectById(matched.getBuildingId());
        LocalDateTime now = LocalDateTime.now();
        long stayMinutes = 0;
        if (matched.getEnterTime() != null) {
            LocalDateTime end = matched.getLeaveTime() != null ? matched.getLeaveTime() : now;
            stayMinutes = Math.max(1, Duration.between(matched.getEnterTime(), end).toMinutes());
        }
        boolean overstay = matched.getStatus() != null && matched.getStatus() == 0 && stayMinutes > 120;
        boolean alreadyLeft = matched.getStatus() != null && matched.getStatus() == 1;
        if (checkOut && !alreadyLeft) {
            matched.setLeaveTime(now);
            matched.setStatus(1);
            visitorMapper.updateById(matched);
            alreadyLeft = true;
            if (matched.getVisitStudentId() != null) {
                messageService.push(matched.getVisitStudentId(), "访客已签离",
                        matched.getVisitorName() + " 已于门岗核验签离", "VISITOR", String.valueOf(matched.getId()));
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", matched.getId());
        m.put("passCode", PassCodeUtil.fromVisitorId(matched.getId()));
        m.put("visitorName", matched.getVisitorName());
        m.put("visitorPhone", matched.getVisitorPhone());
        m.put("relation", matched.getRelation());
        m.put("visitReason", matched.getVisitReason());
        m.put("visitStudentId", matched.getVisitStudentId());
        m.put("visitStudentName", stu != null ? stu.getRealName() : "");
        m.put("buildingId", matched.getBuildingId());
        m.put("buildingName", b != null ? b.getBuildingName() : "");
        m.put("roomNo", matched.getRoomNo());
        m.put("enterTime", matched.getEnterTime());
        m.put("leaveTime", matched.getLeaveTime());
        m.put("stayMinutes", stayMinutes);
        m.put("overstayFlag", overstay ? 1 : 0);
        m.put("status", matched.getStatus());
        m.put("passOk", !alreadyLeft && matched.getStatus() != null && matched.getStatus() == 0);
        m.put("verifiedAt", now);
        String tip = alreadyLeft ? "该通行码已签离" : (overstay ? "在访中但已超时，建议签离" : "核验通过，允许通行");
        return Result.success(tip, m);
    }

    @PostMapping("/visitor-leave/{id}")
    public Result<VisitorRecord> leaveVisitor(@PathVariable Long id, HttpServletRequest request) {
        VisitorRecord v = visitorMapper.selectById(id);
        if (v != null) {
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), v.getBuildingId());
            if (denied != null) {
                @SuppressWarnings("unchecked")
                Result<VisitorRecord> typed = (Result<VisitorRecord>) denied;
                return typed;
            }
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
