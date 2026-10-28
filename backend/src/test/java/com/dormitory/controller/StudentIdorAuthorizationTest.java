package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartAllocationService;
import com.dormitory.service.SmartEvaluationService;
import com.dormitory.service.SmartRepairService;
import com.dormitory.service.SmartUtilityService;
import com.dormitory.service.UserMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentIdorAuthorizationTest {
    @Mock RepairOrderMapper repairOrderMapper;
    @Mock SmartRepairService smartRepairService;
    @Mock SysUserMapper userMapper;
    @Mock DormRoomMapper roomMapper;
    @Mock DormBuildingMapper buildingMapper;
    @Mock DormBedMapper bedMapper;
    @Mock BuildingScopeService buildingScope;
    @Mock UtilityBillMapper utilityBillMapper;
    @Mock SmartUtilityService smartUtilityService;
    @Mock StudentHabitProfileMapper habitProfileMapper;
    @Mock DormAllocationLogMapper allocationLogMapper;
    @Mock DisciplineRecordMapper disciplineMapper;
    @Mock DormAdjustApplyMapper adjustApplyMapper;
    @Mock VisitorRecordMapper visitorMapper;
    @Mock SysNoticeMapper noticeMapper;
    @Mock HygieneInspectionMapper hygieneMapper;
    @Mock SmartEvaluationService smartEvaluationService;
    @Mock SmartAllocationService smartAllocationService;
    @Mock UserMessageService messageService;

    RepairOrderController repairController;
    UtilityBillController utilityController;
    SmartAllocationController allocController;
    DormAffairController affairController;

    @BeforeEach
    void init() {
        repairController = new RepairOrderController(
                repairOrderMapper, smartRepairService, userMapper, roomMapper, buildingMapper, bedMapper, buildingScope, messageService);
        utilityController = new UtilityBillController(utilityBillMapper, smartUtilityService, smartRepairService, roomMapper, buildingMapper, bedMapper, buildingScope, messageService);
        allocController = new SmartAllocationController(
                smartAllocationService, userMapper, habitProfileMapper, bedMapper, roomMapper, buildingMapper, allocationLogMapper);
        affairController = new DormAffairController(
                smartEvaluationService, smartAllocationService, hygieneMapper, disciplineMapper, adjustApplyMapper,
                visitorMapper, noticeMapper, roomMapper, buildingMapper, bedMapper, userMapper, habitProfileMapper, buildingScope, messageService);
    }

    private MockHttpServletRequest student(long id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "STUDENT");
        request.setAttribute("currentUserId", id);
        return request;
    }

    @Test
    void studentCannotRateAnotherStudentsRepairOrder() {
        RepairOrder order = new RepairOrder();
        order.setId(9L);
        order.setStudentId(11L);
        when(repairOrderMapper.selectById(9L)).thenReturn(order);
        assertEquals(403, repairController.rateOrder(Map.of("id", 9, "rating", 5), student(10L)).getCode());
        verify(repairOrderMapper, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void studentSubmitForcesOwnStudentIdAndOwnRoom() {
        DormBed bed = new DormBed();
        bed.setRoomId(1L);
        bed.setStudentId(10L);
        bed.setStatus(1);
        when(bedMapper.selectOne(any(Wrapper.class))).thenReturn(bed);
        DormRoom room = new DormRoom();
        room.setId(1L);
        room.setBuildingId(1L);
        when(roomMapper.selectById(1L)).thenReturn(room);
        when(smartRepairService.createAndSmartDispatch(any(RepairOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        RepairOrder forged = new RepairOrder();
        forged.setStudentId(99L);
        forged.setRoomId(2L);
        forged.setTitle("漏水");
        assertEquals(403, repairController.submitOrder(forged, student(10L)).getCode());

        RepairOrder ok = new RepairOrder();
        ok.setStudentId(99L);
        ok.setRoomId(1L);
        ok.setTitle("漏水");
        var res = repairController.submitOrder(ok, student(10L));
        assertEquals(200, res.getCode());
        assertEquals(10L, res.getData().getStudentId());
        assertEquals(1L, res.getData().getRoomId());
        assertEquals(1L, res.getData().getBuildingId());
    }

    @Test
    void studentCannotRechargeAnotherRoom() {
        DormRoom room = new DormRoom();
        room.setId(2L);
        room.setBuildingId(1L);
        when(roomMapper.selectById(2L)).thenReturn(room);
        when(bedMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertEquals(403, utilityController.payOrRecharge(
                Map.of("roomId", 2, "electricRecharge", 10), student(10L)).getCode());
        verify(smartUtilityService, never()).payBillOrRecharge(anyLong(), any(), any(), any());
    }

    @Test
    void habitProfileIgnoresForgedStudentId() {
        when(habitProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        StudentHabitProfile profile = new StudentHabitProfile();
        profile.setStudentId(99L);
        profile.setScheduleScore(3);
        var res = allocController.saveHabitProfile(profile, student(10L));
        assertEquals(200, res.getCode());
        assertEquals(10L, profile.getStudentId());
        verify(habitProfileMapper).insert(argThat((StudentHabitProfile p) -> Long.valueOf(10L).equals(p.getStudentId())));
    }

    @Test
    void studentCannotAppealAnotherStudentsDiscipline() {
        DisciplineRecord record = new DisciplineRecord();
        record.setId(5L);
        record.setStudentId(11L);
        record.setDescription("晚归");
        when(disciplineMapper.selectById(5L)).thenReturn(record);
        assertEquals(403, affairController.appealDiscipline(
                Map.of("id", 5, "appealReason", "有事"), student(10L)).getCode());
        verify(disciplineMapper, never()).updateById(any(DisciplineRecord.class));
    }

    @Test
    void studentCannotRegisterDisciplineEvenIfControllerReached() {
        DisciplineRecord d = new DisciplineRecord();
        d.setStudentId(11L);
        d.setRecordType("LATE_RETURN");
        assertEquals(403, affairController.saveDiscipline(d, student(10L)).getCode());
        verify(disciplineMapper, never()).insert(org.mockito.ArgumentMatchers.any(DisciplineRecord.class));
    }

    @Test
    void adjustApplyForcesOwnStudentId() {
        DormAdjustApply apply = new DormAdjustApply();
        apply.setStudentId(99L);
        apply.setOldRoomId(1L);
        apply.setReason("调宿");
        var res = affairController.submitAdjustApply(apply, student(10L));
        assertEquals(200, res.getCode());
        assertEquals(10L, apply.getStudentId());
        verify(adjustApplyMapper).insert(argThat((DormAdjustApply a) -> Long.valueOf(10L).equals(a.getStudentId())));
    }
}
