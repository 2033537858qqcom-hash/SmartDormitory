package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("utility_payment_log")
public class UtilityPaymentLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String txnNo;
    private Long roomId;
    private Long billId;
    private Long operatorId;
    /** RECHARGE / PAY_BILL */
    private String actionType;
    private BigDecimal electricDelta;
    private BigDecimal waterDelta;
    private BigDecimal electricBefore;
    private BigDecimal waterBefore;
    private BigDecimal electricAfter;
    private BigDecimal waterAfter;
    /** 固定 DEMO：演示模拟扣款，非真实支付 */
    private String channel;
    private String remark;
    private LocalDateTime createTime;
}
