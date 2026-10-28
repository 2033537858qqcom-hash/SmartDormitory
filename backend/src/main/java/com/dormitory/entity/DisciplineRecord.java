package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("discipline_record")
public class DisciplineRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long roomId;
    /** LATE_RETURN, NO_RETURN, ILLEGAL_APPLIANCE, NOISE */
    private String recordType;
    private Integer deductScore;
    private LocalDateTime occurTime;
    private String description;
    private Integer status;
    private Long recorderId;
    private LocalDateTime createTime;
}
