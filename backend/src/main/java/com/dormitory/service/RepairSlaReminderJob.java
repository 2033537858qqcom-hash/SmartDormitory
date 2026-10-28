package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.RepairOrder;
import com.dormitory.mapper.RepairOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepairSlaReminderJob {
    private final RepairOrderMapper orders;
    private final RepairSlaReminderService reminders;

    @Scheduled(fixedDelayString = "${app.repair-sla-check-ms:300000}", initialDelayString = "${app.repair-sla-check-ms:300000}")
    public void checkDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        orders.selectList(new LambdaQueryWrapper<RepairOrder>()
                .in(RepairOrder::getStatus, "PENDING", "ASSIGNED", "PROCESSING")
                .and(q -> q.le(RepairOrder::getDueAt, now).isNull(RepairOrder::getSlaEscalatedAt)
                        .or(r -> r.gt(RepairOrder::getDueAt, now).le(RepairOrder::getDueAt, now.plusHours(1)).isNull(RepairOrder::getSlaReminderSentAt)))
                .orderByAsc(RepairOrder::getDueAt).last("LIMIT 100"))
                .forEach(order -> {
                    try { reminders.notifyForOrder(order.getId(), now); }
                    catch (RuntimeException ex) { log.error("维修 SLA 通知失败，工单 {}", order.getId(), ex); }
                });
    }
}
