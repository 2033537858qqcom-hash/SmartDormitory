package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.UserMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResidenceWorkflowTest {
    @Mock ResidenceRequestMapper requests;
    @Mock DormBedMapper beds;
    @Mock DormRoomMapper rooms;
    @Mock SysUserMapper users;
    @Mock BuildingScopeService scope;
    @Mock UserMessageService messages;
    @InjectMocks ResidenceController controller;

    private MockHttpServletRequest student() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("currentUserId", 10L); r.setAttribute("currentUserRole", "STUDENT"); return r;
    }

    @Test
    void holidayRequestUsesActualStudentAndBed() {
        SysUser user = new SysUser(); user.setId(10L);
        DormBed bed = new DormBed(); bed.setStudentId(10L); bed.setRoomId(1L); bed.setStatus(1);
        when(users.selectOne(any(Wrapper.class))).thenReturn(user);
        when(beds.selectOne(any(Wrapper.class))).thenReturn(bed);
        ResidenceRequest request = new ResidenceRequest(); request.setStudentId(99L); request.setRoomId(99L);
        request.setRequestType("HOLIDAY"); request.setFromDate(LocalDate.now().plusDays(1)); request.setToDate(LocalDate.now().plusDays(2));
        request.setReason("留校学习");
        assertEquals(200, controller.submit(request, student()).getCode());
        assertEquals(10L, request.getStudentId()); assertEquals(1L, request.getRoomId());
        assertEquals("PENDING", request.getStatus());
    }

    @Test
    void invalidHolidayDatesAreRejected() {
        ResidenceRequest request = new ResidenceRequest(); request.setRequestType("HOLIDAY");
        request.setFromDate(LocalDate.now().plusDays(3)); request.setToDate(LocalDate.now().plusDays(1)); request.setReason("留校");
        assertNotEquals(200, controller.submit(request, student()).getCode());
        verify(requests, never()).insert(any(ResidenceRequest.class));
    }

    @Test
    void alreadyReviewedRequestCannotBeReviewedAgain() {
        ResidenceRequest request = new ResidenceRequest(); request.setStatus("APPROVED");
        when(requests.selectOne(any(Wrapper.class))).thenReturn(request);
        assertNotEquals(200, controller.review(Map.of("id", 1, "status", "APPROVED"), student()).getCode());
        verify(requests, never()).updateById(any(ResidenceRequest.class));
    }
}
