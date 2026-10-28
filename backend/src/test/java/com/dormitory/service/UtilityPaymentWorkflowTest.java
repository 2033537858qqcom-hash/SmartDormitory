package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.entity.DormRoom;
import com.dormitory.entity.UtilityBill;
import com.dormitory.entity.UtilityPaymentLog;
import com.dormitory.mapper.DormRoomMapper;
import com.dormitory.mapper.UtilityBillMapper;
import com.dormitory.mapper.UtilityPaymentLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UtilityPaymentWorkflowTest {
    @Mock DormRoomMapper rooms;
    @Mock UtilityBillMapper bills;
    @Mock UtilityPaymentLogMapper logs;
    @InjectMocks SmartUtilityService service;

    private DormRoom room() {
        DormRoom room = new DormRoom(); room.setId(1L);
        room.setElectricBalance(new BigDecimal("30.00")); room.setWaterBalance(new BigDecimal("20.00")); return room;
    }

    @Test
    void rejectsCrossRoomAndAlreadyPaidBillsBeforeChangingBalance() {
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(room());
        UtilityBill bill = new UtilityBill(); bill.setId(8L); bill.setRoomId(2L); bill.setPayStatus(0);
        when(bills.selectById(8L)).thenReturn(bill);
        assertThrows(IllegalArgumentException.class, () -> service.processPayment(1L, 8L, BigDecimal.ZERO, BigDecimal.ZERO, 7L, "payment_request_001"));
        bill.setRoomId(1L); bill.setPayStatus(1);
        assertThrows(IllegalArgumentException.class, () -> service.processPayment(1L, 8L, BigDecimal.ZERO, BigDecimal.ZERO, 7L, "payment_request_002"));
        verify(rooms, never()).updateById(any(DormRoom.class));
        verify(logs, never()).insert(any(UtilityPaymentLog.class));
    }

    @Test
    void rejectsNegativeAndEmptyRecharge() {
        assertThrows(IllegalArgumentException.class, () -> service.processPayment(1L, null, new BigDecimal("-1"), BigDecimal.ZERO, 7L, "payment_request_003"));
        assertThrows(IllegalArgumentException.class, () -> service.processPayment(1L, null, BigDecimal.ZERO, BigDecimal.ZERO, 7L, "payment_request_004"));
        verifyNoInteractions(rooms, bills, logs);
    }

    @Test
    void validRechargeUpdatesBalanceAndCreatesOneAuditRecord() {
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(room());
        when(rooms.updateById(any(DormRoom.class))).thenReturn(1);
        var result = service.processPayment(1L, null, new BigDecimal("10.00"), BigDecimal.ZERO, 7L, "payment_request_005");
        assertEquals(new BigDecimal("40.00"), result.get("electricAfter"));
        verify(logs).insert(argThat((UtilityPaymentLog log) -> "DEMO-payment_request_005".equals(log.getTxnNo()) && new BigDecimal("10.00").compareTo(log.getElectricDelta()) == 0));
    }
}
