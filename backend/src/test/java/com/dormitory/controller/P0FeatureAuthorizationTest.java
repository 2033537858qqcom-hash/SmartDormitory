package com.dormitory.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.Result;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import com.dormitory.service.SmartEvaluationService;
import com.dormitory.service.SmartRepairService;
import com.dormitory.service.UserMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class P0FeatureAuthorizationTest {
    @Mock DormBuildingMapper buildingMapper;
    @Mock DormRoomMapper roomMapper;
    @Mock DormBedMapper bedMapper;
    @Mock SysUserMapper userMapper;
    @Mock StudentHabitProfileMapper habitProfileMapper;
    @Mock SmartAllocationService smartAllocationService;
    @Mock BuildingScopeService buildingScope;
    @Mock RepairOrderMapper repairOrderMapper;
    @Mock SmartRepairService smartRepairService;
    @Mock SmartEvaluationService smartEvaluationService;
    @Mock HygieneInspectionMapper hygieneMapper;
    @Mock DisciplineRecordMapper disciplineMapper;
    @Mock DormAdjustApplyMapper adjustApplyMapper;
    @Mock VisitorRecordMapper visitorMapper;
    @Mock SysNoticeMapper noticeMapper;
    @Mock UserMessageService messageService;

    DormManageController dormController;
    RepairOrderController repairController;
    DormAffairController affairController;

    @BeforeEach
    void init() {
        dormController = new DormManageController(
                buildingMapper, roomMapper, bedMapper, userMapper, habitProfileMapper,
                smartAllocationService, buildingScope);
        repairController = new RepairOrderController(
                repairOrderMapper, smartRepairService, userMapper, roomMapper, buildingMapper, bedMapper, buildingScope, messageService);
        affairController = new DormAffairController(
                smartEvaluationService, smartAllocationService, hygieneMapper, disciplineMapper,
                adjustApplyMapper, visitorMapper, noticeMapper, roomMapper, buildingMapper, bedMapper,
                userMapper, habitProfileMapper, buildingScope, messageService);
        lenient().when(buildingScope.resolve(any())).thenReturn(null);
        lenient().when(buildingScope.forbiddenIfOutOfScope(isNull(), any())).thenReturn(null);
        lenient().when(buildingScope.forbiddenIfOutOfScope(any(), isNull())).thenReturn(null);
    }

    private MockHttpServletRequest admin() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("currentUserRole", "ADMIN");
        r.setAttribute("currentUserId", 1L);
        return r;
    }

    private MockHttpServletRequest manager() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("currentUserRole", "DORM_MANAGER");
        r.setAttribute("currentUserId", 2L);
        return r;
    }

    private MockHttpServletRequest worker() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("currentUserRole", "REPAIRMAN");
        r.setAttribute("currentUserId", 7L);
        return r;
    }

    @Test
    void assignBedRejectsGenderMismatch() {
        DormBed bed = new DormBed();
        bed.setId(5L);
        bed.setRoomId(10L);
        bed.setStatus(0);
        DormRoom room = new DormRoom();
        room.setId(10L);
        room.setBuildingId(1L);
        DormBuilding building = new DormBuilding();
        building.setId(1L);
        building.setBuildingName("1号楼");
        building.setGenderType(1); // 男
        SysUser girl = new SysUser();
        girl.setId(99L);
        girl.setGender(2); // 女
        girl.setRealName("测试女同学");

        when(bedMapper.selectById(5L)).thenReturn(bed);
        when(roomMapper.selectById(10L)).thenReturn(room);
        when(buildingMapper.selectById(1L)).thenReturn(building);
        when(userMapper.selectById(99L)).thenReturn(girl);
        when(buildingScope.resolve(any())).thenReturn(null);
        when(buildingScope.forbiddenIfOutOfScope(isNull(), eq(1L))).thenReturn(null);

        Result<String> res = dormController.assignOrReleaseBed(Map.of("bedId", 5, "studentId", 99), admin());
        assertNotEquals(200, res.getCode());
        assertTrue(res.getMessage().contains("性别不符"));
        verify(bedMapper, never()).updateById(any(DormBed.class));
    }

    @Test
    void assignBedAllowsMatchingGender() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), DormBed.class);
        DormBed bed = new DormBed();
        bed.setId(5L);
        bed.setRoomId(10L);
        bed.setStatus(0);
        DormRoom room = new DormRoom();
        room.setId(10L);
        room.setBuildingId(1L);
        DormBuilding building = new DormBuilding();
        building.setId(1L);
        building.setBuildingName("1号楼");
        building.setGenderType(1);
        SysUser boy = new SysUser();
        boy.setId(88L);
        boy.setGender(1);
        when(bedMapper.selectById(5L)).thenReturn(bed);
        when(roomMapper.selectById(10L)).thenReturn(room);
        when(buildingMapper.selectById(1L)).thenReturn(building);
        when(userMapper.selectById(88L)).thenReturn(boy);
        when(bedMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(habitProfileMapper.selectOne(any())).thenReturn(null);
        when(buildingScope.resolve(any())).thenReturn(null);
        when(buildingScope.forbiddenIfOutOfScope(isNull(), eq(1L))).thenReturn(null);

        Result<String> res = dormController.assignOrReleaseBed(Map.of("bedId", 5, "studentId", 88), admin());
        assertEquals(200, res.getCode());
        verify(bedMapper).updateById(any(DormBed.class));
        verify(smartAllocationService).recordManualChange(1L, 5L, null, 88L, 1L);
    }

    @Test
    void manualAssignmentCannotOverwriteOccupiedBed() {
        DormBed bed = new DormBed(); bed.setId(5L); bed.setRoomId(10L); bed.setStatus(1); bed.setStudentId(10L);
        DormRoom room = new DormRoom(); room.setId(10L); room.setBuildingId(1L);
        when(bedMapper.selectById(5L)).thenReturn(bed);
        when(roomMapper.selectById(10L)).thenReturn(room);
        var result = dormController.assignOrReleaseBed(Map.of("bedId", 5, "studentId", 88), admin());
        assertNotEquals(200, result.getCode());
        assertTrue(result.getMessage().contains("已占用"));
        verify(bedMapper, never()).updateById(any(DormBed.class));
    }

    @Test
    void nonAdminCannotBindBuildingManager() {
        DormBuilding b = new DormBuilding();
        b.setId(1L);
        b.setManagerId(2L);
        Result<DormBuilding> res = dormController.saveBuilding(b, manager());
        assertEquals(403, res.getCode());
        verify(buildingMapper, never()).updateById(any(DormBuilding.class));
    }

    @Test
    void repairmanCannotReassign() {
        Result<RepairOrder> res = repairController.reassignOrder(
                Map.of("id", 1, "workerId", 8), worker());
        assertEquals(403, res.getCode());
        verify(smartRepairService, never()).reassignWorker(anyLong(), anyLong(), any());
    }

    @Test
    void managerReassignRespectsBuildingScope() {
        RepairOrder order = new RepairOrder();
        order.setId(3L);
        order.setBuildingId(2L);
        when(repairOrderMapper.selectById(3L)).thenReturn(order);
        when(buildingScope.resolve(any())).thenReturn(Set.of(1L));
        when(buildingScope.forbiddenIfOutOfScope(eq(Set.of(1L)), eq(2L)))
                .thenReturn(Result.error(403, "只能操作管辖楼栋内的数据"));

        Result<RepairOrder> res = repairController.reassignOrder(
                Map.of("id", 3, "workerId", 8), manager());
        assertEquals(403, res.getCode());
        verify(smartRepairService, never()).reassignWorker(anyLong(), anyLong(), any());
    }

    @Test
    void curfewAuditRespectsManagerBuildingScope() {
        when(buildingScope.resolve(any())).thenReturn(Set.of(1L));
        when(buildingScope.forbiddenIfOutOfScope(eq(Set.of(1L)), eq(2L)))
                .thenReturn(Result.error(403, "只能操作管辖楼栋内的数据"));

        Result<Map<String, Object>> res = affairController.runCurfewAudit(
                Map.of("buildingId", 2), manager());
        assertEquals(403, res.getCode());
        verify(bedMapper, never()).selectList(any());
    }
}
