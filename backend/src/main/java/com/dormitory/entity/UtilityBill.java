package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("utility_bill")
public class UtilityBill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billMonth;
    private Long buildingId;
    private Long roomId;
    private BigDecimal electricUsed;
    private BigDecimal electricCost;
    private BigDecimal waterUsed;
    private BigDecimal waterCost;
    private BigDecimal totalCost;
    private Integer abnormalFlag;
    private String abnormalReason;
    private String anomalyStatus;
    private String anomalyNote;
    private Long anomalyRepairId;
    private Long anomalyUpdatedBy;
    private LocalDateTime anomalyUpdatedAt;
    private Integer payStatus;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
}
