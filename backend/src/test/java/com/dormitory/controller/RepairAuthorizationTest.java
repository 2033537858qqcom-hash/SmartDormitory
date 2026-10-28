package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.entity.RepairOrder;
import com.dormitory.mapper.RepairOrderMapper;
import com.dormitory.mapper.SysUserMapper;
import com.dormitory.mapper.DormRoomMapper;
import com.dormitory.mapper.DormBuildingMapper;
import com.dormitory.mapper.DormBedMapper;
import com.dormitory.service.SmartRepairService;
import com.dormitory.service.UserMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairAuthorizationTest {
    @Mock RepairOrderMapper orders;
    @Mock SysUserMapper users;
    @Mock DormRoomMapper rooms;
    @Mock DormBuildingMapper buildings;
    @Mock DormBedMapper beds;
    @Mock BuildingScopeService buildingScope;
    @Mock SmartRepairService service;
    @Mock UserMessageService messageService;

    RepairOrderController controller;

    @BeforeEach
    void init() {
        controller = new RepairOrderController(orders, service, users, rooms, buildings, beds, buildingScope, messageService);
        lenient().when(buildingScope.resolve(any())).thenReturn(null);
    }

    private MockHttpServletRequest worker() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "REPAIRMAN");
        request.setAttribute("currentUserId", 7L);
        return request;
    }

    @Test
    void workerCannotProcessAnotherWorkersOrder() {
        RepairOrder order = new RepairOrder();
        order.setId(2L);
        order.setAssignedWorkerId(8L);
        when(orders.selectById(2L)).thenReturn(order);
        assertEquals(403, controller.processOrder(Map.of("id", 2, "status", "COMPLETED"), worker()).getCode());
        verify(orders, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void workerListAlwaysUsesAuthenticatedWorkerId() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), RepairOrder.class);
        doAnswer(invocation -> {
            LambdaQueryWrapper<RepairOrder> wrapper = invocation.getArgument(0);
            assertTrue(wrapper.getSqlSegment().contains("assigned_worker_id"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));
            assertFalse(wrapper.getParamNameValuePairs().containsValue(8L));
            return java.util.List.of();
        }).when(orders).selectList(any(Wrapper.class));
        when(users.selectList(any())).thenReturn(java.util.List.of());
        when(rooms.selectList(any())).thenReturn(java.util.List.of());
        when(buildings.selectList(any())).thenReturn(java.util.List.of());
        controller.listOrders(null, 8L, null, null, worker());
    }
}
