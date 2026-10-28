package com.dormitory.common;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.entity.DormBuilding;
import com.dormitory.mapper.DormBuildingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BuildingScopeServiceTest {
    @Test
    void adminAndStudentAreUnrestricted() {
        DormBuildingMapper mapper = mock(DormBuildingMapper.class);
        BuildingScopeService scope = new BuildingScopeService(mapper);
        MockHttpServletRequest admin = new MockHttpServletRequest();
        admin.setAttribute("currentUserRole", "ADMIN");
        admin.setAttribute("currentUserId", 1L);
        assertNull(scope.resolve(admin));
        MockHttpServletRequest student = new MockHttpServletRequest();
        student.setAttribute("currentUserRole", "STUDENT");
        student.setAttribute("currentUserId", 10L);
        assertNull(scope.resolve(student));
        verify(mapper, never()).selectList(any());
    }

    @Test
    void dormManagerOnlySeesAssignedBuildings() {
        DormBuildingMapper mapper = mock(DormBuildingMapper.class);
        DormBuilding b = new DormBuilding();
        b.setId(1L);
        b.setManagerId(2L);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(b));
        BuildingScopeService scope = new BuildingScopeService(mapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "DORM_MANAGER");
        request.setAttribute("currentUserId", 2L);
        assertEquals(Set.of(1L), scope.resolve(request));
        assertTrue(scope.allows(Set.of(1L), 1L));
        assertFalse(scope.allows(Set.of(1L), 2L));
        assertEquals(403, scope.forbiddenIfOutOfScope(Set.of(1L), 2L).getCode());
        assertNull(scope.forbiddenIfOutOfScope(Set.of(1L), 1L));
    }
}
