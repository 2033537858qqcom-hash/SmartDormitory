package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("dorm_bed")
public class DormBed {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Integer bedNo;
    private Long studentId;
    private LocalDate checkInDate;
    /** 0-空闲, 1-已入住, 2-锁定 */
    private Integer status;
}
