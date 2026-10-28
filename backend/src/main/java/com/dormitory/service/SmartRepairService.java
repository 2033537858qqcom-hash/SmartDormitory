package com.dormitory.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.RepairOrder;
import com.dormitory.entity.SysUser;
import com.dormitory.mapper.RepairOrderMapper;
import com.dormitory.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 核心亮点二：智能报修高危关键词识别与维修工最小负载派单引擎
 */
@Service
@RequiredArgsConstructor
public class SmartRepairService {

    private final RepairOrderMapper repairOrderMapper;
    private final SysUserMapper sysUserMapper;

    private static final List<String> HIGH_RISK_KEYWORDS = Arrays.asList(
            "跳闸", "短路", "火花", "焦糊", "冒烟", "触电", "爆管", "漏水严重", "喷水", "反水", "门锁打不开", "被困", "断电"
    );

    /**
     * 提交报修并自动执行：1.紧急度识别 2.工种匹配与最小待办负载派单
     */
    public RepairOrder createAndSmartDispatch(RepairOrder order) {
        order.setOrderNo("REP-" + DateUtil.format(new Date(), "yyyyMMdd") + "-" + RandomUtil.randomNumbers(3));
        order.setCreateTime(LocalDateTime.now());

        // 1. 关键词高危紧急度智能判定
        String fullText = (order.getTitle() != null ? order.getTitle() : "") + " " +
                (order.getDescription() != null ? order.getDescription() : "");
        List<String> matchedKeywords = new ArrayList<>();
        for (String kw : HIGH_RISK_KEYWORDS) {
            if (fullText.contains(kw)) {
                matchedKeywords.add(kw);
            }
        }
        if (!matchedKeywords.isEmpty()) {
            order.setPriority("HIGH");
        } else if (order.getPriority() == null || order.getPriority().isEmpty()) {
            order.setPriority("NORMAL");
        }

        // 2. 匹配对应工种的在岗维修师傅，并统计每人当前未完成工单数量（负载均衡）
        String specialty = order.getRepairType() != null ? order.getRepairType() : "ELECTRIC";
        List<SysUser> workers = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRole, "REPAIRMAN")
                        .eq(SysUser::getStatus, 1)
                        .eq(SysUser::getRepairSpecialty, specialty)
        );
        if (workers.isEmpty()) {
            workers = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getRole, "REPAIRMAN")
                            .eq(SysUser::getStatus, 1)
            );
        }

        SysUser chosenWorker = null;
        long minActiveCount = Long.MAX_VALUE;
        for (SysUser w : workers) {
            long activeOrders = repairOrderMapper.selectCount(
                    new LambdaQueryWrapper<RepairOrder>()
                            .eq(RepairOrder::getAssignedWorkerId, w.getId())
                            .in(RepairOrder::getStatus, Arrays.asList("ASSIGNED", "PROCESSING"))
            );
            if (activeOrders < minActiveCount) {
                minActiveCount = activeOrders;
                chosenWorker = w;
            }
        }

        StringBuilder remark = new StringBuilder();
        if (!matchedKeywords.isEmpty()) {
            remark.append("智能识别高危词").append(matchedKeywords).append(" -> 提权为HIGH；");
        } else {
            remark.append("常规故障优先级NORMAL；");
        }

        if (chosenWorker != null) {
            order.setAssignedWorkerId(chosenWorker.getId());
            order.setStatus("ASSIGNED");
            remark.append("匹配[").append(specialty).append("]工种且当前负载最低(")
                    .append(minActiveCount).append("单) -> 自动指派给 ").append(chosenWorker.getRealName());
        } else {
            order.setStatus("PENDING");
            remark.append("暂无空闲对应工种师傅，进入待人工调度队列");
        }
        order.setDispatchRemark(remark.toString());
        order.setDueAt(computeDueAt(order.getCreateTime(), order.getPriority()));

        repairOrderMapper.insert(order);
        return order;
    }

    /** HIGH 4h / NORMAL 24h / LOW 48h */
    public static LocalDateTime computeDueAt(LocalDateTime created, String priority) {
        LocalDateTime base = created != null ? created : LocalDateTime.now();
        int hours = slaHours(priority);
        return base.plusHours(hours);
    }

    public static int slaHours(String priority) {
        if ("HIGH".equals(priority)) return 4;
        if ("LOW".equals(priority)) return 48;
        return 24;
    }

    /** 未完结且已过 dueAt 视为超时 */
    public static boolean isOverdue(RepairOrder order, LocalDateTime now) {
        if (order == null || order.getDueAt() == null) return false;
        String st = order.getStatus();
        if (st == null || Set.of("COMPLETED", "RATED", "CANCELLED").contains(st)) return false;
        return order.getDueAt().isBefore(now != null ? now : LocalDateTime.now());
    }

    /**
     * 管理端改派：将工单指派给同工种（或任意在岗）维修工，并写入派单备注。
     */
    public RepairOrder reassignWorker(Long orderId, Long newWorkerId, String remark) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (!java.util.Set.of("PENDING", "ASSIGNED", "PROCESSING").contains(order.getStatus())) {
            throw new IllegalStateException("仅待派单/待维修/维修中工单可改派");
        }
        SysUser worker = sysUserMapper.selectById(newWorkerId);
        if (worker == null || !"REPAIRMAN".equals(worker.getRole()) || Integer.valueOf(0).equals(worker.getStatus())) {
            throw new IllegalArgumentException("目标维修工无效或已停用");
        }
        Long oldId = order.getAssignedWorkerId();
        order.setAssignedWorkerId(newWorkerId);
        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("ASSIGNED");
        }
        String note = (remark != null && !remark.isBlank()) ? remark.trim() : "管理端改派";
        String prev = order.getDispatchRemark() != null ? order.getDispatchRemark() + "；" : "";
        order.setDispatchRemark(prev + note + " -> " + worker.getRealName()
                + (oldId != null ? "（原指派#" + oldId + "）" : ""));
        repairOrderMapper.updateById(order);
        return order;
    }

    /**
     * 取消/关闭工单并写备注（不进入评价流）。
     */
    public RepairOrder cancelOrder(Long orderId, String remark) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (java.util.Set.of("COMPLETED", "RATED", "CANCELLED").contains(order.getStatus())) {
            throw new IllegalStateException("工单已结束，无法取消");
        }
        String note = (remark != null && !remark.isBlank()) ? remark.trim() : "管理端关闭";
        order.setStatus("CANCELLED");
        order.setRepairResult((order.getRepairResult() != null ? order.getRepairResult() + "；" : "") + "[已取消] " + note);
        order.setFinishTime(java.time.LocalDateTime.now());
        repairOrderMapper.updateById(order);
        return order;
    }
}
