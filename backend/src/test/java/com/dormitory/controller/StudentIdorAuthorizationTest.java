package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.common.PassCodeUtil;
import com.dormitory.common.Result;
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
import java.util.List;
import java.util.Set;

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
    @Mock UtilityPaymentLogMapper paymentLogMapper;
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
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), VisitorRecord.class);
        repairController = new RepairOrderController(
                repairOrderMapper, smartRepairService, userMapper, roomMapper, buildingMapper, bedMapper, buildingScope, messageService);
        utilityController = new UtilityBillController(utilityBillMapper, smartUtilityService, smartRepairService, roomMapper, buildingMapper, bedMapper, buildingScope, messageService, paymentLogMapper);
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
    void lateReportUsesAuthenticatedStudentAndOccupiedRoom() {
        DormBed bed = new DormBed();
        bed.setStudentId(10L);
        bed.setRoomId(20L);
        when(bedMapper.selectOne(any(Wrapper.class))).thenReturn(bed);
        var result = affairController.reportLateReturn(Map.of("reason", "晚自习", "expectedTime", "23:30", "studentId", 99), student(10L));
        assertEquals(200, result.getCode());
        assertEquals(10L, result.getData().getStudentId());
        assertEquals(20L, result.getData().getRoomId());
        assertEquals(0, result.getData().getDeductScore());
        verify(disciplineMapper).insert(any(DisciplineRecord.class));
    }

    @Test
    void lateReportRequiresOccupiedBed() {
        when(bedMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertNotEquals(200, affairController.reportLateReturn(Map.of("reason", "晚自习", "expectedTime", "23:30"), student(10L)).getCode());
        verify(disciplineMapper, never()).insert(any(DisciplineRecord.class));
    }

    @Test
    void lateReportRejectsInvalidTimeAndOtherRoles() {
        assertNotEquals(200, affairController.reportLateReturn(Map.of("reason", "晚自习", "expectedTime", "25:00"), student(10L)).getCode());
        MockHttpServletRequest manager = new MockHttpServletRequest();
        manager.setAttribute("currentUserRole", "DORM_MANAGER");
        manager.setAttribute("currentUserId", 10L);
        assertEquals(403, affairController.reportLateReturn(Map.of("reason", "晚自习", "expectedTime", "23:30"), manager).getCode());
        verify(disciplineMapper, never()).insert(any(DisciplineRecord.class));
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
        verify(smartUtilityService, never()).processPayment(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void authorizedPaymentUsesIdempotentTransactionService() {
        DormRoom room = new DormRoom(); room.setId(1L);
        DormBed bed = new DormBed(); bed.setRoomId(1L); bed.setStudentId(10L); bed.setStatus(1);
        when(roomMapper.selectById(1L)).thenReturn(room);
        when(bedMapper.selectOne(any(Wrapper.class))).thenReturn(bed);
        when(smartUtilityService.processPayment(eq(1L), isNull(), any(), any(), eq(10L), eq("payment_request_007")))
                .thenReturn(Map.of("txnNo", "DEMO-payment_request_007"));
        var result = utilityController.payOrRecharge(Map.of("roomId", 1, "electricRecharge", 10, "requestId", "payment_request_007"), student(10L));
        assertEquals(200, result.getCode());
        assertEquals("DEMO-payment_request_007", result.getData().get("txnNo"));
    }

    @Test
    void paymentHistoryRequiresOwnRoom() {
        when(bedMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertEquals(403, utilityController.paymentHistory(2L, student(10L)).getCode());
        verify(paymentLogMapper, never()).selectList(any(Wrapper.class));
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
        DormBed bed = new DormBed();
        bed.setStudentId(10L);
        bed.setRoomId(1L);
        bed.setBedNo(1);
        bed.setStatus(1);
        when(bedMapper.selectOne(any(Wrapper.class))).thenReturn(bed);
        DormAdjustApply apply = new DormAdjustApply();
        apply.setStudentId(99L);
        apply.setOldRoomId(1L);
        apply.setReason("调宿");
        var res = affairController.submitAdjustApply(apply, student(10L));
        assertEquals(200, res.getCode());
        assertEquals(10L, apply.getStudentId());
        verify(adjustApplyMapper).insert(argThat((DormAdjustApply a) -> Long.valueOf(10L).equals(a.getStudentId())));
    }

    @Test
    void visitorVerificationRejectsOtherBuildingsBeforeUpdating() {
        VisitorRecord visitor = new VisitorRecord(); visitor.setId(5L); visitor.setBuildingId(2L); visitor.setStatus(0);
        MockHttpServletRequest manager = new MockHttpServletRequest();
        when(visitorMapper.selectList(any(Wrapper.class))).thenReturn(List.of(visitor));
        when(buildingScope.resolve(manager)).thenReturn(Set.of(1L));
        when(buildingScope.forbiddenIfOutOfScope(Set.of(1L), 2L)).thenReturn(Result.error(403, "只能操作管辖楼栋内的数据"));
        var result = affairController.verifyVisitorPass(Map.of("passCode", PassCodeUtil.fromVisitorId(5L), "checkOut", true), manager);
        assertEquals(403, result.getCode());
        verify(visitorMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void visitorCheckoutUpdatesOnlyOnceAndNotifiesOnlyWinner() {
        VisitorRecord visitor = new VisitorRecord(); visitor.setId(5L); visitor.setBuildingId(1L); visitor.setVisitStudentId(10L); visitor.setVisitorName("访客"); visitor.setStatus(0);
        MockHttpServletRequest manager = new MockHttpServletRequest();
        when(visitorMapper.selectList(any(Wrapper.class))).thenReturn(List.of(visitor));
        when(visitorMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        var result = affairController.verifyVisitorPass(Map.of("passCode", PassCodeUtil.fromVisitorId(5L), "checkOut", true), manager);
        assertEquals(1, result.getData().get("status"));
        assertEquals(false, result.getData().get("passOk"));
        verify(visitorMapper).update(isNull(), any(Wrapper.class));
        verify(messageService).push(eq(10L), anyString(), anyString(), eq("VISITOR"), eq("5"));
    }

    @Test
    void concurrentVisitorCheckoutDoesNotSendAnotherNotification() {
        VisitorRecord snapshot = new VisitorRecord(); snapshot.setId(5L); snapshot.setBuildingId(1L); snapshot.setVisitStudentId(10L); snapshot.setStatus(0);
        VisitorRecord signedOut = new VisitorRecord(); signedOut.setId(5L); signedOut.setBuildingId(1L); signedOut.setVisitStudentId(10L); signedOut.setStatus(1);
        MockHttpServletRequest manager = new MockHttpServletRequest();
        when(visitorMapper.selectList(any(Wrapper.class))).thenReturn(List.of(snapshot));
        when(visitorMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        when(visitorMapper.selectById(5L)).thenReturn(signedOut);
        var result = affairController.verifyVisitorPass(Map.of("passCode", PassCodeUtil.fromVisitorId(5L), "checkOut", true), manager);
        assertEquals(1, result.getData().get("status"));
        verify(messageService, never()).push(anyLong(), anyString(), anyString(), anyString(), anyString());
    }
    @Test
    void alipayOrderRejectsStudentOutsideRoom() {
        DormRoom room = new DormRoom(); room.setId(20L); room.setBuildingId(2L);
        when(roomMapper.selectById(20L)).thenReturn(room);
        assertEquals(403, utilityController.createAlipayOrder(Map.of("roomId", 20, "electricRecharge", 10), student(10L)).getCode());
        verify(smartUtilityService, never()).createAlipayOrder(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void alipayOrderRejectsManagerOutsideBuilding() {
        DormRoom room = new DormRoom(); room.setId(20L); room.setBuildingId(2L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "DORM_MANAGER"); request.setAttribute("currentUserId", 3L);
        when(roomMapper.selectById(20L)).thenReturn(room);
        when(buildingScope.resolve(request)).thenReturn(Set.of(1L));
        when(buildingScope.forbiddenIfOutOfScope(Set.of(1L), 2L)).thenReturn(Result.error(403, "无权限"));
        assertEquals(403, utilityController.createAlipayOrder(Map.of("roomId", 20, "electricRecharge", 10), request).getCode());
        verify(smartUtilityService, never()).createAlipayOrder(anyLong(), any(), any(), any(), anyLong());
    }

}
