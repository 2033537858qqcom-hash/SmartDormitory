package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dorm_room")
public class DormRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long buildingId;
    private Integer floorNo;
    private String roomNo;
    private Integer capacity;
    private Integer occupiedCount;
    private BigDecimal electricBalance;
    private BigDecimal waterBalance;
    private BigDecimal harmonyScore;
    private BigDecimal civilizedScore;
    private Integer starLevel;
    private Integer status;
    private LocalDateTime createTime;
}
