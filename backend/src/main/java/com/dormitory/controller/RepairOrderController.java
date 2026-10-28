package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartRepairService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listOrders(@RequestParam(required = false) Long studentId,
                                                        @RequestParam(required = false) Long workerId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String priority) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
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
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/submit")
    public Result<RepairOrder> submitOrder(@RequestBody RepairOrder order) {
        RepairOrder saved = smartRepairService.createAndSmartDispatch(order);
        return Result.success("报修提交成功，系统已完成智能优先级识别与派单！", saved);
    }

    @PostMapping("/process")
    public Result<RepairOrder> processOrder(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.get("status").toString();
        String repairResult = body.get("repairResult") != null ? body.get("repairResult").toString() : null;

        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            return Result.error("工单不存在");
        }
        order.setStatus(status);
        if (repairResult != null) {
            order.setRepairResult(repairResult);
        }
        if ("COMPLETED".equals(status)) {
            order.setFinishTime(LocalDateTime.now());
        }
        repairOrderMapper.updateById(order);
        return Result.success("工单状态已更新", order);
    }

    @PostMapping("/rate")
    public Result<RepairOrder> rateOrder(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        Integer rating = Integer.valueOf(body.get("rating").toString());
        String comment = body.get("ratingComment") != null ? body.get("ratingComment").toString() : "好评！";

        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            return Result.error("工单不存在");
        }
        order.setRating(rating);
        order.setRatingComment(comment);
        order.setStatus("RATED");
        repairOrderMapper.updateById(order);
        return Result.success("感谢您的评价！", order);
    }
}
