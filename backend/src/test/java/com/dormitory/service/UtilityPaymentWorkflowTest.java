package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dormitory.entity.DormRoom;
import com.dormitory.entity.UtilityBill;
import com.dormitory.entity.UtilityPaymentLog;
import com.dormitory.mapper.DormRoomMapper;
import com.dormitory.mapper.UtilityBillMapper;
import com.dormitory.mapper.UtilityPaymentLogMapper;
import com.dormitory.common.AlipayConfig;
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
    @Mock AlipayConfig alipayConfig;
    @InjectMocks SmartUtilityService service;

    @org.junit.jupiter.api.BeforeEach
    void initializeMapping() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), UtilityPaymentLog.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), DormRoom.class);
    }

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
    void rejectsBillPaymentWhenBalanceIsInsufficient() {
        DormRoom lowRoom = new DormRoom();
        lowRoom.setId(1L);
        lowRoom.setElectricBalance(new BigDecimal("10.00"));
        lowRoom.setWaterBalance(new BigDecimal("5.00"));
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(lowRoom);

        UtilityBill expensiveBill = new UtilityBill();
        expensiveBill.setId(9L);
        expensiveBill.setRoomId(1L);
        expensiveBill.setPayStatus(0);
        expensiveBill.setElectricCost(new BigDecimal("50.00"));
        expensiveBill.setWaterCost(new BigDecimal("20.00"));
        when(bills.selectById(9L)).thenReturn(expensiveBill);

        assertThrows(IllegalArgumentException.class, () ->
                service.processPayment(1L, 9L, BigDecimal.ZERO, BigDecimal.ZERO, 7L, "payment_request_low_bal"));
        verify(rooms, never()).updateById(any(DormRoom.class));
        verify(logs, never()).insert(any(UtilityPaymentLog.class));
    }

    @Test
    void allowsBillPaymentWhenBalanceIsSufficient() {
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(room());
        when(rooms.updateById(any(DormRoom.class))).thenReturn(1);

        UtilityBill affordableBill = new UtilityBill();
        affordableBill.setId(10L);
        affordableBill.setRoomId(1L);
        affordableBill.setPayStatus(0);
        affordableBill.setElectricCost(new BigDecimal("15.00"));
        affordableBill.setWaterCost(new BigDecimal("5.00"));
        when(bills.selectById(10L)).thenReturn(affordableBill);
        when(bills.updateById(any(UtilityBill.class))).thenReturn(1);

        var result = service.processPayment(1L, 10L, BigDecimal.ZERO, BigDecimal.ZERO, 7L, "payment_request_afford");
        assertEquals(new BigDecimal("15.00"), result.get("electricAfter"));
        assertEquals(new BigDecimal("15.00"), result.get("waterAfter"));
        verify(rooms).updateById(any(DormRoom.class));
        verify(bills).updateById(argThat((UtilityBill b) -> Integer.valueOf(1).equals(b.getPayStatus())));
        verify(logs).insert(any(UtilityPaymentLog.class));
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
    @Test
    void alipayOrderRejectsMixedNegativeRecharge() {
        when(rooms.selectById(1L)).thenReturn(room());
        assertThrows(IllegalArgumentException.class, () -> service.createAlipayOrder(1L, null,
                new BigDecimal("-10"), new BigDecimal("20"), 7L));
        verify(logs, never()).insert(any(UtilityPaymentLog.class));
    }

    private UtilityPaymentLog pendingRecharge() {
        UtilityPaymentLog log = new UtilityPaymentLog(); log.setId(1L); log.setTxnNo("ALI-1");
        log.setRoomId(1L); log.setActionType("RECHARGE"); log.setChannel("ALIPAY_PENDING");
        log.setElectricDelta(new BigDecimal("10.00")); log.setWaterDelta(BigDecimal.ZERO);
        log.setRemark("RECHARGE:10.00:0|AMOUNT=10.00"); return log;
    }

    @Test
    void alipayCallbackRejectsMismatchedAmount() throws Exception {
        when(alipayConfig.isEnabled()).thenReturn(true);
        lenient().when(alipayConfig.getAppId()).thenReturn("test-app");
        var client = mock(com.alipay.easysdk.payment.common.Client.class);
        when(client.verifyNotify(anyMap())).thenReturn(true);
        try (var payment = org.mockito.Mockito.mockStatic(com.alipay.easysdk.factory.Factory.Payment.class)) {
        payment.when(com.alipay.easysdk.factory.Factory.Payment::Common).thenReturn(client);
        when(logs.selectOne(any(Wrapper.class))).thenReturn(pendingRecharge());
        lenient().when(rooms.selectOne(any(Wrapper.class))).thenReturn(room());
        assertEquals("failure", service.handleAlipayNotify(java.util.Map.of("trade_status", "TRADE_SUCCESS", "out_trade_no", "ALI-1", "app_id", "test-app", "total_amount", "1.00")));
        verify(rooms, never()).updateById(any(DormRoom.class));
        }
    }

    @Test
    void alipayCallbackLocksOrderBeforeApplyingAndDoesNotApplyTwice() throws Exception {
        when(alipayConfig.isEnabled()).thenReturn(true);
        lenient().when(alipayConfig.getAppId()).thenReturn("test-app");
        var client = mock(com.alipay.easysdk.payment.common.Client.class);
        when(client.verifyNotify(anyMap())).thenReturn(true);
        try (var payment = org.mockito.Mockito.mockStatic(com.alipay.easysdk.factory.Factory.Payment.class)) {
        payment.when(com.alipay.easysdk.factory.Factory.Payment::Common).thenReturn(client);
        UtilityPaymentLog pending = pendingRecharge();
        when(logs.selectOne(any(Wrapper.class))).thenReturn(pending);
        when(rooms.selectOne(any(Wrapper.class))).thenReturn(room());
        var callback = java.util.Map.of("trade_status", "TRADE_SUCCESS", "out_trade_no", "ALI-1", "app_id", "test-app", "total_amount", "10.00");
        assertEquals("success", service.handleAlipayNotify(callback));
        assertEquals("success", service.handleAlipayNotify(callback));
        verify(rooms, times(1)).updateById(any(DormRoom.class));
        verify(logs, times(2)).selectOne(argThat(wrapper -> wrapper.getSqlSegment().contains("FOR UPDATE")));
        }
    }

}
