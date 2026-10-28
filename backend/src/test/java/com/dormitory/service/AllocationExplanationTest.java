package com.dormitory.service;

import com.dormitory.entity.StudentHabitProfile;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.dormitory.mapper.DormBedMapper;
import com.dormitory.mapper.SysUserMapper;
import com.dormitory.entity.SysUser;
import com.dormitory.entity.DormBed;
import com.dormitory.entity.DormRoom;
import com.dormitory.mapper.DormRoomMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AllocationExplanationTest {
    private final SmartAllocationService service = new SmartAllocationService(null, null, null, null, null, null);

    @Test
    void explainsLargestDifferencesAndHandlesSingletonGroups() {
        StudentHabitProfile early = new StudentHabitProfile(); early.setScheduleScore(1); early.setAtmosphereScore(3);
        StudentHabitProfile late = new StudentHabitProfile(); late.setScheduleScore(5); late.setAtmosphereScore(3);
        String explanation = service.explainGroup(List.of(early, late));
        assertTrue(explanation.contains("作息"));
        assertTrue(explanation.contains("4"));
        assertTrue(service.explainGroup(List.of(early)).contains("单人"));
    }

    @Test
    void allocationRejectsBedsClaimedAfterPreview() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), DormBed.class);
        SysUserMapper users = mock(SysUserMapper.class);
        DormBedMapper beds = mock(DormBedMapper.class);
        SmartAllocationService allocator = new SmartAllocationService(users, null, null, null, beds, null);
        SysUser student = new SysUser(); student.setId(10L);
        when(users.selectOne(any(Wrapper.class))).thenReturn(student);
        when(beds.update(any(), any(Wrapper.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> allocator.reserveBed(5L, 10L));
    }

    @Test
    void harmonyRecalculationDoesNotOverwriteRoomAccountBalances() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), DormRoom.class);
        DormRoomMapper rooms = mock(DormRoomMapper.class);
        DormBedMapper beds = mock(DormBedMapper.class);
        SmartAllocationService allocator = new SmartAllocationService(null, null, null, rooms, beds, null);
        DormRoom room = new DormRoom(); room.setId(1L);
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(room);
        when(beds.selectList(any(Wrapper.class))).thenReturn(List.of());
        allocator.recalculateRoomHarmony(1L);
        verify(rooms).update(isNull(), any(Wrapper.class));
        verify(rooms, never()).updateById(any(DormRoom.class));
    }
}
