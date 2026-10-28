package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class P1FeatureAuthorizationTest {
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
    @Mock UtilityBillMapper utilityBillMapper;
    @Mock SmartUtilityService smartUtilityService;
    @Mock DormAllocationLogMapper allocationLogMapper;
    @Mock UserMessageService messageService;

    DormAffairController affairController;
    UtilityBillController utilityController;
    SmartAllocationController allocController;

    @BeforeEach
    void init() {
        affairController = new DormAffairController(
                smartEvaluationService, smartAllocationService, hygieneMapper, disciplineMapper,
                adjustApplyMapper, visitorMapper, noticeMapper, roomMapper, buildingMapper, bedMapper,
                userMapper, habitProfileMapper, buildingScope, messageService);
        utilityController = new UtilityBillController(
                utilityBillMapper, smartUtilityService, smartRepairService, roomMapper, buildingMapper,
                bedMapper, buildingScope, messageService);
        allocController = new SmartAllocationController(
                smartAllocationService, userMapper, habitProfileMapper, bedMapper, roomMapper,
                buildingMapper, allocationLogMapper);
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

    @Test
    void passCodeStableFormula() {
        assertEquals("VP-" + String.format("%06d", Math.abs(3L * 13579L + 802100L) % 1000000L),
                PassCodeUtil.fromVisitorId(3L));
        assertEquals("VP-000123", PassCodeUtil.normalize("123"));
    }

    @Test
    void visitorVerifyReturnsGreenPassAndCanCheckout() {
        VisitorRecord v = new VisitorRecord();
        v.setId(3L);
        v.setVisitorName("李家长");
        v.setVisitorPhone("13900001111");
        v.setVisitStudentId(10L);
        v.setBuildingId(1L);
        v.setRoomNo("302");
        v.setRelation("家长");
        v.setVisitReason("送生活用品");
        v.setEnterTime(LocalDateTime.now().minusMinutes(30));
        v.setStatus(0);
        when(visitorMapper.selectList(any())).thenReturn(List.of(v));
        SysUser stu = new SysUser();
        stu.setId(10L);
        stu.setRealName("张三");
        when(userMapper.selectById(10L)).thenReturn(stu);
        DormBuilding b = new DormBuilding();
        b.setId(1L);
        b.setBuildingName("1号楼");
        when(buildingMapper.selectById(1L)).thenReturn(b);

        String code = PassCodeUtil.fromVisitorId(3L);
        Result<Map<String, Object>> res = affairController.verifyVisitorPass(
                Map.of("passCode", code, "checkOut", false), admin());
        assertEquals(200, res.getCode());
        assertTrue((Boolean) res.getData().get("passOk"));
        assertEquals("李家长", res.getData().get("visitorName"));

        Result<Map<String, Object>> leave = affairController.verifyVisitorPass(
                Map.of("passCode", code, "checkOut", true), admin());
        assertEquals(200, leave.getCode());
        verify(visitorMapper).updateById(any(VisitorRecord.class));
        verify(messageService).push(eq(10L), contains("签离"), anyString(), eq("VISITOR"), anyString());
    }

    @Test
    void batchMeterRespectsScopeAndCountsAbnormal() {
        DormRoom room = new DormRoom();
        room.setId(2L);
        room.setBuildingId(1L);
        when(roomMapper.selectById(2L)).thenReturn(room);
        UtilityBill saved = new UtilityBill();
        saved.setId(9L);
        saved.setAbnormalFlag(1);
        when(smartUtilityService.createOrUpdateMeterBill(any())).thenReturn(saved);

        Result<Map<String, Object>> res = utilityController.batchMeter(Map.of(
                "billMonth", "2026-10",
                "entries", List.of(Map.of("roomId", 2, "electricUsed", 200, "waterUsed", 12))
        ), admin());
        assertEquals(200, res.getCode());
        assertEquals(1, res.getData().get("savedCount"));
        assertEquals(1, res.getData().get("abnormalCount"));
    }

    @Test
    void allocationPrecheckReportsMissingProfiles() {
        SysUser s = new SysUser();
        s.setId(99L);
        s.setUsername("20269999");
        s.setRealName("新同学");
        s.setGender(1);
        s.setRole("STUDENT");
        when(userMapper.selectList(any())).thenReturn(List.of(s));
        when(habitProfileMapper.selectList(null)).thenReturn(List.of());
        when(bedMapper.selectList(any())).thenReturn(List.of());
        when(roomMapper.selectList(any())).thenReturn(List.of());
        when(buildingMapper.selectList(null)).thenReturn(List.of());

        Result<Map<String, Object>> res = allocController.precheck(null);
        assertEquals(200, res.getCode());
        assertEquals(1, res.getData().get("missingProfileCount"));
        assertEquals(1, res.getData().get("unallocatedCount"));
    }
}
