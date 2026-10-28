package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.UserMessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/residence")
@RequiredArgsConstructor
public class ResidenceController {
    private final ResidenceRequestMapper requests;
    private final DormBedMapper beds;
    private final DormRoomMapper rooms;
    private final SysUserMapper users;
    private final BuildingScopeService scope;
    private final UserMessageService messages;

    @GetMapping("/requests")
    public Result<List<ResidenceRequest>> list(HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        LambdaQueryWrapper<ResidenceRequest> query = new LambdaQueryWrapper<>();
        if ("STUDENT".equals(role)) query.eq(ResidenceRequest::getStudentId, request.getAttribute("currentUserId"));
        else {
            Set<Long> permitted = scope.resolve(request);
            if (permitted != null) {
                if (permitted.isEmpty()) return Result.success(List.of());
                List<Long> roomIds = rooms.selectList(new LambdaQueryWrapper<DormRoom>().in(DormRoom::getBuildingId, permitted))
                        .stream().map(DormRoom::getId).toList();
                if (roomIds.isEmpty()) return Result.success(List.of());
                query.in(ResidenceRequest::getRoomId, roomIds);
            }
        }
        return Result.success(requests.selectList(query.orderByDesc(ResidenceRequest::getCreatedAt)));
    }

    @PostMapping("/requests")
    @Transactional(rollbackFor = Exception.class)
    public Result<ResidenceRequest> submit(@RequestBody ResidenceRequest item, HttpServletRequest request) {
        if (!"STUDENT".equals(request.getAttribute("currentUserRole"))) return Result.error(403, "仅学生可提交住宿申请");
        if (!Set.of("CHECKIN", "HOLIDAY").contains(item.getRequestType() == null ? "" : item.getRequestType())) return Result.error("无效的申请类型");
        if (item.getReason() == null || item.getReason().isBlank() || item.getReason().length() > 500) return Result.error("请填写申请说明");
        if ("HOLIDAY".equals(item.getRequestType()) && (item.getFromDate() == null || item.getToDate() == null
                || item.getFromDate().isBefore(LocalDate.now()) || item.getToDate().isBefore(item.getFromDate())
                || item.getToDate().isAfter(item.getFromDate().plusDays(180)))) return Result.error("留宿日期无效，最长 180 天");
        Long uid = (Long) request.getAttribute("currentUserId");
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, uid).last("FOR UPDATE"));
        if (user == null) return Result.error("账号不存在");
        DormBed bed = beds.selectOne(new LambdaQueryWrapper<DormBed>()
                .eq(DormBed::getStudentId, uid).eq(DormBed::getStatus, 1).last("LIMIT 1"));
        if (bed == null) return Result.error("请先分配床位");
        if (requests.selectCount(new LambdaQueryWrapper<ResidenceRequest>().eq(ResidenceRequest::getStudentId, uid)
                .eq(ResidenceRequest::getRequestType, item.getRequestType()).eq(ResidenceRequest::getStatus, "PENDING")) > 0)
            return Result.error("已有同类申请正在审核");
        item.setId(null); item.setStudentId(uid); item.setRoomId(bed.getRoomId()); item.setStatus("PENDING");
        item.setReviewerId(null); item.setReviewComment(null); item.setReviewedAt(null); item.setCreatedAt(LocalDateTime.now());
        if ("CHECKIN".equals(item.getRequestType())) { item.setFromDate(null); item.setToDate(null); }
        requests.insert(item);
        return Result.success("申请已提交", item);
    }

    @PostMapping("/review")
    @Transactional(rollbackFor = Exception.class)
    public Result<ResidenceRequest> review(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if (!"ADMIN".equals(role) && !"DORM_MANAGER".equals(role)) return Result.error(403, "无权审核申请");
        String status = String.valueOf(body.get("status"));
        if (!Set.of("APPROVED", "REJECTED").contains(status)) return Result.error("无效的审核结果");
        Long id = Long.valueOf(body.get("id").toString());
        ResidenceRequest item = requests.selectOne(new LambdaQueryWrapper<ResidenceRequest>().eq(ResidenceRequest::getId, id).last("FOR UPDATE"));
        if (item == null || !"PENDING".equals(item.getStatus())) return Result.error("申请不存在或已审核");
        DormRoom room = rooms.selectById(item.getRoomId());
        if (room == null) return Result.error("申请寝室不存在");
        Result<?> denied = scope.forbiddenIfOutOfScope(scope.resolve(request), room.getBuildingId());
        if (denied != null) return Result.error(403, denied.getMessage());
        String comment = body.get("comment") == null ? "" : body.get("comment").toString().trim();
        if (comment.length() > 500) return Result.error("审核意见过长");
        item.setStatus(status); item.setReviewerId((Long) request.getAttribute("currentUserId"));
        item.setReviewComment(comment); item.setReviewedAt(LocalDateTime.now());
        requests.updateById(item);
        messages.push(item.getStudentId(), "住宿申请已审核", comment.isEmpty() ? ("APPROVED".equals(status) ? "申请已通过" : "申请已驳回") : comment,
                "RESIDENCE", String.valueOf(item.getId()));
        return Result.success("审核完成", item);
    }
}
