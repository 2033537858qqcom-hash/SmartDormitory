package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.DormBuilding;
import com.dormitory.entity.RepairOrder;
import com.dormitory.entity.SysUser;
import com.dormitory.mapper.DormBuildingMapper;
import com.dormitory.mapper.RepairOrderMapper;
import com.dormitory.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RepairSlaReminderService {
    private final RepairOrderMapper orders;
    private final DormBuildingMapper buildings;
    private final SysUserMapper users;
    private final UserMessageService messages;

    @Transactional(rollbackFor = Exception.class)
    public void notifyForOrder(Long id, LocalDateTime now) {
        RepairOrder order = orders.selectOne(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getId, id).last("FOR UPDATE"));
        if (order == null || order.getDueAt() == null || !Set.of("PENDING", "ASSIGNED", "PROCESSING").contains(order.getStatus())) return;
        boolean overdue = !order.getDueAt().isAfter(now);
        if (overdue && order.getSlaEscalatedAt() != null) return;
        if (!overdue && (order.getSlaReminderSentAt() != null || order.getDueAt().isAfter(now.plusHours(1)))) return;

        Set<Long> recipients = new LinkedHashSet<>();
        if (order.getAssignedWorkerId() != null) recipients.add(order.getAssignedWorkerId());
        if (overdue || recipients.isEmpty()) {
            DormBuilding building = order.getBuildingId() == null ? null : buildings.selectById(order.getBuildingId());
            if (building != null && building.getManagerId() != null) recipients.add(building.getManagerId());
            else users.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "ADMIN").eq(SysUser::getStatus, 1))
                    .forEach(user -> recipients.add(user.getId()));
        }
        if (recipients.isEmpty()) return;
        String title = overdue ? "报修工单已超时" : "报修工单即将到期";
        String content = "工单「" + order.getTitle() + "」" + (overdue ? "已超过处理时限，请跟进。" : "将在一小时内到期，请及时处理。");
        for (Long recipient : recipients) messages.push(recipient, title, content, "REPAIR", String.valueOf(id));
        if (overdue) order.setSlaEscalatedAt(now); else order.setSlaReminderSentAt(now);
        orders.updateById(order);
    }
}
