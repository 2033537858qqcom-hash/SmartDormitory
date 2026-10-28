package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import com.dormitory.service.SmartRepairService;
import com.dormitory.service.SmartUtilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DormManagerBuildingScopeTest {
    @Mock DormBuildingMapper buildingMapper;
    @Mock DormRoomMapper roomMapper;
    @Mock DormBedMapper bedMapper;
    @Mock SysUserMapper userMapper;
    @Mock StudentHabitProfileMapper habitProfileMapper;
    @Mock SmartAllocationService smartAllocationService;
    @Mock BuildingScopeService buildingScope;
    @Mock UtilityBillMapper utilityBillMapper;
    @Mock SmartUtilityService smartUtilityService;
    @Mock RepairOrderMapper repairOrderMapper;
    @Mock SmartRepairService smartRepairService;

    DormManageController dormController;
    UtilityBillController utilityController;
    RepairOrderController repairController;

    @BeforeEach
    void init() {
        dormController = new DormManageController(
                buildingMapper, roomMapper, bedMapper, userMapper, habitProfileMapper, smartAllocationService, buildingScope);
        utilityController = new UtilityBillController(
                utilityBillMapper, smartUtilityService, roomMapper, buildingMapper, bedMapper, buildingScope);
        repairController = new RepairOrderController(
                repairOrderMapper, smartRepairService, userMapper, roomMapper, buildingMapper, bedMapper, buildingScope);
        lenient().when(buildingScope.resolve(any())).thenReturn(Set.of(1L));
        lenient().when(buildingScope.forbiddenIfOutOfScope(eq(Set.of(1L)), eq(1L))).thenReturn(null);
        lenient().when(buildingScope.forbiddenIfOutOfScope(eq(Set.of(1L)), eq(2L)))
                .thenReturn(Result.error(403, "只能操作管辖楼栋内的数据"));
        lenient().when(buildingScope.forbiddenIfOutOfScope(eq(Set.of(1L)), isNull()))
                .thenReturn(Result.error(403, "只能操作管辖楼栋内的数据"));
    }

    private MockHttpServletRequest manager() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "DORM_MANAGER");
        request.setAttribute("currentUserId", 2L);
        return request;
    }

    @Test
    void managerCannotListOtherBuildingRooms() {
        assertEquals(403, dormController.listRooms(2L, manager()).getCode());
        verify(roomMapper, never()).selectList(any());
    }

    @Test
    void managerRoomListAlwaysFiltersToScopedBuildings() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), DormRoom.class);
        doAnswer(invocation -> {
            LambdaQueryWrapper<DormRoom> wrapper = invocation.getArgument(0);
            assertTrue(wrapper.getSqlSegment().contains("building_id"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
            return List.of();
        }).when(roomMapper).selectList(any(Wrapper.class));
        when(buildingMapper.selectList(any())).thenReturn(List.of());
        when(bedMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of());
        assertEquals(200, dormController.listRooms(null, manager()).getCode());
    }

    @Test
    void managerCannotCreateRoomInForeignBuilding() {
        DormRoom room = new DormRoom();
        room.setBuildingId(2L);
        room.setRoomNo("999");
        assertEquals(403, dormController.createOrUpdateRoom(room, manager()).getCode());
        verify(roomMapper, never()).insert(org.mockito.ArgumentMatchers.any(DormRoom.class));
    }

    @Test
    void managerCannotRechargeForeignBuildingRoom() {
        DormRoom room = new DormRoom();
        room.setId(7L);
        room.setBuildingId(2L);
        when(roomMapper.selectById(7L)).thenReturn(room);
        assertEquals(403, utilityController.payOrRecharge(
                java.util.Map.of("roomId", 7, "electricRecharge", 20), manager()).getCode());
        verify(smartUtilityService, never()).payBillOrRecharge(anyLong(), any(), any(), any());
    }

    @Test
    void managerRepairListFiltersByBuildingScope() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), RepairOrder.class);
        doAnswer(invocation -> {
            LambdaQueryWrapper<RepairOrder> wrapper = invocation.getArgument(0);
            assertTrue(wrapper.getSqlSegment().contains("building_id"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
            return List.of();
        }).when(repairOrderMapper).selectList(any(Wrapper.class));
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(roomMapper.selectList(any())).thenReturn(List.of());
        when(buildingMapper.selectList(any())).thenReturn(List.of());
        assertEquals(200, repairController.listOrders(null, null, null, null, manager()).getCode());
    }
}
