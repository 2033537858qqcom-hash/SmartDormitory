package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("hygiene_inspection")
public class HygieneInspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String checkWeek;
    private LocalDate checkDate;
    private Long buildingId;
    private Long roomId;
    private Integer floorScore;
    private Integer bedScore;
    private Integer deskScore;
    private Integer safetyScore;
    private Integer totalScore;
    private String grade;
    private String remark;
    private Long inspectorId;
    private LocalDateTime createTime;
}
