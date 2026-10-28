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

    @Test
    void retriesReturnOriginalTransactionWithoutChargingAgain() {
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(room());
        when(rooms.updateById(any(DormRoom.class))).thenReturn(1);
        java.util.concurrent.atomic.AtomicReference<UtilityPaymentLog> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(logs.selectOne(any(Wrapper.class))).thenAnswer(invocation -> saved.get());
        doAnswer(invocation -> { saved.set(invocation.getArgument(0)); return 1; }).when(logs).insert(any(UtilityPaymentLog.class));
        var first = service.processPayment(1L, null, new BigDecimal("10"), BigDecimal.ZERO, 7L, "payment_request_006");
        var retry = service.processPayment(1L, null, new BigDecimal("10.00"), BigDecimal.ZERO, 7L, "payment_request_006");
        assertEquals(first.get("txnNo"), retry.get("txnNo"));
        verify(rooms, times(1)).updateById(any(DormRoom.class));
        verify(logs, times(1)).insert(any(UtilityPaymentLog.class));
        assertThrows(IllegalArgumentException.class, () -> service.processPayment(1L, null, new BigDecimal("20"), BigDecimal.ZERO, 7L, "payment_request_006"));
    }

    @Test
    void meterEntryCannotOverwritePaidBill() {
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(room());
        UtilityBill existing = new UtilityBill(); existing.setId(8L); existing.setRoomId(1L); existing.setPayStatus(1);
        when(bills.selectOne(any(Wrapper.class))).thenReturn(existing);
        UtilityBill reading = new UtilityBill(); reading.setRoomId(1L); reading.setBillMonth("2026-10");
        reading.setElectricUsed(new BigDecimal("100")); reading.setWaterUsed(new BigDecimal("10"));
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateMeterBill(reading));
        verify(bills, never()).updateById(any(UtilityBill.class));
    }
}
