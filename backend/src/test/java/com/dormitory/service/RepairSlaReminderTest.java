package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.entity.DormBuilding;
import com.dormitory.entity.RepairOrder;
import com.dormitory.mapper.DormBuildingMapper;
import com.dormitory.mapper.RepairOrderMapper;
import com.dormitory.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairSlaReminderTest {
    @Mock RepairOrderMapper orders;
    @Mock DormBuildingMapper buildings;
    @Mock SysUserMapper users;
    @Mock UserMessageService messages;
    @InjectMocks RepairSlaReminderService reminders;

    private RepairOrder order(LocalDateTime due) {
        RepairOrder order = new RepairOrder(); order.setId(4L); order.setBuildingId(2L); order.setAssignedWorkerId(7L);
        order.setStatus("ASSIGNED"); order.setDueAt(due); order.setTitle("插座故障"); return order;
    }

    @Test
    void upcomingDeadlineNotifiesWorkerOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 12, 0);
        RepairOrder order = order(now.plusMinutes(30));
        when(orders.selectOne(any(Wrapper.class))).thenReturn(order);
        reminders.notifyForOrder(4L, now);
        reminders.notifyForOrder(4L, now);
        verify(messages, times(1)).push(eq(7L), anyString(), anyString(), eq("REPAIR"), eq("4"));
        verify(orders, times(1)).updateById(order);
    }

    @Test
    void overdueEscalatesToBuildingManagerAndWorker() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 12, 0);
        RepairOrder order = order(now.minusMinutes(1));
        DormBuilding building = new DormBuilding(); building.setManagerId(2L);
        when(orders.selectOne(any(Wrapper.class))).thenReturn(order);
        when(buildings.selectById(2L)).thenReturn(building);
        reminders.notifyForOrder(4L, now);
        reminders.notifyForOrder(4L, now);
        verify(messages, times(1)).push(eq(7L), anyString(), anyString(), eq("REPAIR"), eq("4"));
        verify(messages, times(1)).push(eq(2L), anyString(), anyString(), eq("REPAIR"), eq("4"));
    }

    @Test
    void completedOrdersDoNotGenerateReminders() {
        RepairOrder order = order(LocalDateTime.now().minusHours(1)); order.setStatus("COMPLETED");
        when(orders.selectOne(any(Wrapper.class))).thenReturn(order);
        reminders.notifyForOrder(4L, LocalDateTime.now());
        verifyNoInteractions(messages);
    }
}
