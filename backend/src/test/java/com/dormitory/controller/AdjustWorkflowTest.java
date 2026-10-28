package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.entity.DormAdjustApply;
import com.dormitory.entity.DormBed;
import com.dormitory.entity.DormRoom;
import com.dormitory.mapper.*;
import com.dormitory.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdjustWorkflowTest {
    @Mock SmartEvaluationService evaluation;
    @Mock SmartAllocationService allocation;
    @Mock HygieneInspectionMapper hygiene;
    @Mock DisciplineRecordMapper discipline;
    @Mock DormAdjustApplyMapper applies;
    @Mock VisitorRecordMapper visitors;
    @Mock SysNoticeMapper notices;
    @Mock DormRoomMapper rooms;
    @Mock DormBuildingMapper buildings;
    @Mock DormBedMapper beds;
    @Mock SysUserMapper users;
    @Mock StudentHabitProfileMapper profiles;
    @Mock BuildingScopeService scope;
    @Mock UserMessageService messages;
    @InjectMocks DormAffairController controller;

    private MockHttpServletRequest admin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "ADMIN");
        request.setAttribute("currentUserId", 1L);
        return request;
    }

    private DormAdjustApply application(String status) {
        DormAdjustApply apply = new DormAdjustApply();
        apply.setId(1L); apply.setStudentId(10L); apply.setStatus(status);
        apply.setApplyType("ADJUST"); apply.setOldRoomId(1L); apply.setOldBedNo(1);
        apply.setTargetRoomId(2L); apply.setTargetBedNo(2);
        return apply;
    }

    private DormBed bed(long id, long room, int number, Long student, int status) {
        DormBed bed = new DormBed(); bed.setId(id); bed.setRoomId(room); bed.setBedNo(number);
        bed.setStudentId(student); bed.setStatus(status); return bed;
    }

    @Test
    void processedApplicationCannotBeApprovedAgain() {
        when(applies.selectOne(any(Wrapper.class))).thenReturn(application("APPROVED"));
        assertNotEquals(200, controller.reviewAdjustApply(Map.of("id", 1, "status", "APPROVED"), admin()).getCode());
        verifyNoInteractions(beds, messages);
    }

    @Test
    void occupiedTargetBedMustNotReleaseOriginalBed() {
        when(applies.selectOne(any(Wrapper.class))).thenReturn(application("PENDING"));
        DormRoom room = new DormRoom(); room.setBuildingId(1L);
        when(rooms.selectById(1L)).thenReturn(room);
        when(beds.selectOne(any(Wrapper.class))).thenReturn(bed(1, 1, 1, 10L, 1), bed(2, 2, 2, 11L, 1));
        assertNotEquals(200, controller.reviewAdjustApply(Map.of("id", 1, "status", "APPROVED"), admin()).getCode());
        verify(beds, never()).update(any(), any(Wrapper.class));
        verify(applies, never()).updateById(any(DormAdjustApply.class));
    }

    @Test
    void applicationMustUseAuthenticatedStudentsActualBed() {
        DormBed actual = bed(1, 3, 4, 10L, 1);
        when(beds.selectOne(any(Wrapper.class))).thenReturn(actual);
        MockHttpServletRequest request = admin(); request.setAttribute("currentUserRole", "STUDENT"); request.setAttribute("currentUserId", 10L);
        DormAdjustApply apply = application("PENDING"); apply.setStudentId(99L);
        assertEquals(200, controller.submitAdjustApply(apply, request).getCode());
        assertEquals(10L, apply.getStudentId());
        assertEquals(3L, apply.getOldRoomId());
        assertEquals(4, apply.getOldBedNo());
    }
}
