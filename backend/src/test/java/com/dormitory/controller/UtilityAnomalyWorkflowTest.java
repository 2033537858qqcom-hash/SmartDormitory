package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.common.BuildingScopeService;
import com.dormitory.entity.UtilityBill;
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
class UtilityAnomalyWorkflowTest {
    @Mock UtilityBillMapper bills;
    @Mock SmartUtilityService utility;
    @Mock SmartRepairService repair;
    @Mock DormRoomMapper rooms;
    @Mock DormBuildingMapper buildings;
    @Mock DormBedMapper beds;
    @Mock BuildingScopeService scope;
    @Mock UserMessageService messages;
    @Mock UtilityPaymentLogMapper logs;
    @InjectMocks UtilityBillController controller;

    @Test
    void alreadyConvertedAnomalyCannotCreateAnotherRepair() {
        UtilityBill bill = new UtilityBill(); bill.setId(1L); bill.setBuildingId(2L);
        bill.setAbnormalFlag(1); bill.setAnomalyRepairId(9L);
        when(bills.selectOne(any(Wrapper.class))).thenReturn(bill);
        assertNotEquals(200, controller.anomalyToRepair(Map.of("billId", 1), new MockHttpServletRequest()).getCode());
        verifyNoInteractions(repair);
    }

    @Test
    void resolutionRequiresAReasonAndIsSaved() {
        UtilityBill bill = new UtilityBill(); bill.setId(1L); bill.setBuildingId(2L); bill.setAbnormalFlag(1);
        when(bills.selectOne(any(Wrapper.class))).thenReturn(bill);
        assertNotEquals(200, controller.updateAnomaly(Map.of("billId", 1, "status", "RESOLVED", "note", ""), new MockHttpServletRequest()).getCode());
        assertEquals(200, controller.updateAnomaly(Map.of("billId", 1, "status", "RESOLVED", "note", "水管已修复"), new MockHttpServletRequest()).getCode());
        assertEquals("RESOLVED", bill.getAnomalyStatus());
        assertEquals("水管已修复", bill.getAnomalyNote());
        verify(bills).updateById(bill);
    }
}
