package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartRepairService;
import com.dormitory.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repair")
@RequiredArgsConstructor
public class RepairOrderController {

    private final RepairOrderMapper repairOrderMapper;
    private final SmartRepairService smartRepairService;
    private final SysUserMapper userMapper;
    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final DormBedMapper bedMapper;
    private final BuildingScopeService buildingScope;
    private final UserMessageService messageService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listOrders(@RequestParam(required = false) Long studentId,
                                                        @RequestParam(required = false) Long workerId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String priority,
                                                        HttpServletRequest request) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        if ("REPAIRMAN".equals(request.getAttribute("currentUserRole"))) {
            workerId = (Long) request.getAttribute("currentUserId");
        }
        Set<Long> scope = buildingScope.resolve(request);
        if (scope != null) {
            if (scope.isEmpty()) {
                return Result.success(List.of());
            }
            wrapper.in(RepairOrder::getBuildingId, scope);
        }
        if (studentId != null) wrapper.eq(RepairOrder::getStudentId, studentId);
        if (workerId != null) wrapper.eq(RepairOrder::getAssignedWorkerId, workerId);
        if (status != null && !status.isEmpty()) wrapper.eq(RepairOrder::getStatus, status);
        if (priority != null && !priority.isEmpty()) wrapper.eq(RepairOrder::getPriority, priority);
        // 高危工单优先置顶展示
        wrapper.orderByAsc(RepairOrder::getPriority).orderByDesc(RepairOrder::getCreateTime);

        List<RepairOrder> orders = repairOrderMapper.selectList(wrapper);
        Map<Long, SysUser> userMap = userMapper.selectList(null).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream().collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream().collect(Collectors.toMap(DormBuilding::getId, b -> b));

        List<Map<String, Object>> result = new ArrayList<>();
        for (RepairOrder o : orders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", o.getId());
            item.put("orderNo", o.getOrderNo());
            item.put("studentId", o.getStudentId());
            SysUser stu = userMap.get(o.getStudentId());
            item.put("studentName", stu != null ? stu.getRealName() : "未知");
            item.put("studentPhone", stu != null ? stu.getPhone() : "-");
            item.put("buildingId", o.getBuildingId());
            DormBuilding b = buildingMap.get(o.getBuildingId());
            item.put("buildingName", b != null ? b.getBuildingName() : "");
            item.put("roomId", o.getRoomId());
            DormRoom r = roomMap.get(o.getRoomId());
            item.put("roomNo", r != null ? r.getRoomNo() : "");
            item.put("repairType", o.getRepairType());
            item.put("title", o.getTitle());
            item.put("description", o.getDescription());
            item.put("priority", o.getPriority());
            item.put("assignedWorkerId", o.getAssignedWorkerId());
            SysUser worker = userMap.get(o.getAssignedWorkerId());
            item.put("workerName", worker != null ? worker.getRealName() : "待指派");
            item.put("workerPhone", worker != null ? worker.getPhone() : "-");
            item.put("dispatchRemark", o.getDispatchRemark());
            item.put("status", o.getStatus());
            item.put("repairResult", o.getRepairResult());
            item.put("rating", o.getRating());
            item.put("ratingComment", o.getRatingComment());
            item.put("finishTime", o.getFinishTime());
            item.put("createTime", o.getCreateTime());
            item.put("photoUrls", o.getPhotoUrls());
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/submit")
    public Result<RepairOrder> submitOrder(@RequestBody RepairOrder order, HttpServletRequest request) {
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            Long uid = (Long) request.getAttribute("currentUserId");
            order.setStudentId(uid);
            DormBed bed = bedMapper.selectOne(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getStudentId, uid)
                            .eq(DormBed::getStatus, 1)
                            .last("LIMIT 1")
            );
            if (bed == null) {
                return Result.error("未找到您的在住床位，无法报修");
            }
            if (order.getRoomId() != null && !Objects.equals(order.getRoomId(), bed.getRoomId())) {
                return Result.error(403, "只能为自己的寝室提交报修");
            }
            order.setRoomId(bed.getRoomId());
            DormRoom room = roomMapper.selectById(bed.getRoomId());
            if (room != null) {
                order.setBuildingId(room.getBuildingId());
            }
        } else {
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), order.getBuildingId());
            if (denied != null) return (Result<RepairOrder>) denied;
        }
        RepairOrder saved = smartRepairService.createAndSmartDispatch(order);
        return Result.success("报修提交成功，系统已完成智能优先级识别与派单！", saved);
    }

    @PostMapping("/process")
    public Result<RepairOrder> processOrder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.get("status").toString();
        String repairResult = body.get("repairResult") != null ? body.get("repairResult").toString() : null;

        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            return Result.error("工单不存在");
        }
        if ("REPAIRMAN".equals(request.getAttribute("currentUserRole"))) {
            if (!Objects.equals(order.getAssignedWorkerId(), request.getAttribute("currentUserId"))) {
                return Result.error(403, "只能处理分配给自己的工单");
            }
            if (!Set.of("ASSIGNED", "PROCESSING").contains(order.getStatus())
                    || !Set.of("PROCESSING", "COMPLETED").contains(status)) {
                return Result.error("工单状态不允许此操作");
            }
        } else {
            Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), order.getBuildingId());
            if (denied != null) return (Result<RepairOrder>) denied;
        }
        order.setStatus(status);
        if (repairResult != null) {
            order.setRepairResult(repairResult);
        }
        if ("COMPLETED".equals(status)) {
            order.setFinishTime(LocalDateTime.now());
        }
        repairOrderMapper.updateById(order);
        if ("COMPLETED".equals(status) && order.getStudentId() != null) {
            messageService.push(order.getStudentId(), "报修已完成",
                    "工单「" + order.getTitle() + "」已完工，请前往评价",
                    "REPAIR", String.valueOf(order.getId()));
        } else if ("PROCESSING".equals(status) && order.getStudentId() != null) {
            messageService.push(order.getStudentId(), "师傅已接单上门",
                    "工单「" + order.getTitle() + "」正在处理中",
                    "REPAIR", String.valueOf(order.getId()));
        }
        return Result.success("工单状态已更新", order);
    }

    @PostMapping("/rate")
    public Result<RepairOrder> rateOrder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long id = Long.valueOf(body.get("id").toString());
        Integer rating = Integer.valueOf(body.get("rating").toString());
        String comment = body.get("ratingComment") != null ? body.get("ratingComment").toString() : "好评！";

        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            return Result.error("工单不存在");
        }
        if ("STUDENT".equals(request.getAttribute("currentUserRole"))) {
            if (!Objects.equals(order.getStudentId(), request.getAttribute("currentUserId"))) {
                return Result.error(403, "只能评价自己的报修工单");
            }
        }
        order.setRating(rating);
        order.setRatingComment(comment);
        order.setStatus("RATED");
        repairOrderMapper.updateById(order);
        return Result.success("感谢您的评价！", order);
    }

    @PostMapping("/reassign")
    public Result<RepairOrder> reassignOrder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if ("REPAIRMAN".equals(request.getAttribute("currentUserRole"))
                || "STUDENT".equals(request.getAttribute("currentUserRole"))) {
            return Result.error(403, "仅管理员或宿管可改派工单");
        }
        Long id = Long.valueOf(body.get("id").toString());
        Long workerId = Long.valueOf(body.get("workerId").toString());
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        RepairOrder existing = repairOrderMapper.selectById(id);
        if (existing == null) {
            return Result.error("工单不存在");
        }
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), existing.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<RepairOrder> typed = (Result<RepairOrder>) denied;
            return typed;
        }
        try {
            RepairOrder updated = smartRepairService.reassignWorker(id, workerId, remark);
            return Result.success("工单已改派", updated);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping("/cancel")
    public Result<RepairOrder> cancelOrder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if ("REPAIRMAN".equals(request.getAttribute("currentUserRole"))
                || "STUDENT".equals(request.getAttribute("currentUserRole"))) {
            return Result.error(403, "仅管理员或宿管可取消工单");
        }
        Long id = Long.valueOf(body.get("id").toString());
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        RepairOrder existing = repairOrderMapper.selectById(id);
        if (existing == null) {
            return Result.error("工单不存在");
        }
        Result<?> denied = buildingScope.forbiddenIfOutOfScope(buildingScope.resolve(request), existing.getBuildingId());
        if (denied != null) {
            @SuppressWarnings("unchecked")
            Result<RepairOrder> typed = (Result<RepairOrder>) denied;
            return typed;
        }
        try {
            RepairOrder updated = smartRepairService.cancelOrder(id, remark);
            return Result.success("工单已取消关闭", updated);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Result.error(ex.getMessage());
        }
    }

}
