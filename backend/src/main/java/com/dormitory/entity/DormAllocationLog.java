package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dorm_allocation_log")
public class DormAllocationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private Long buildingId;
    private Integer allocatedStudentCount;
    private Integer allocatedRoomCount;
    private BigDecimal avgHarmonyScore;
    private String algorithmStrategy;
    private String detailJson;
    private Long operatorId;
    private LocalDateTime createTime;
}
