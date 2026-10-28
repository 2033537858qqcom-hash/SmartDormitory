package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("student_habit_profile")
public class StudentHabitProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    /** 作息时间(1:早睡 ~ 5:晚睡) */
    private Integer scheduleScore;
    /** 睡眠质量(1:深睡 ~ 5:浅眠) */
    private Integer sleepDepthScore;
    /** 空调偏好(1:喜冷 ~ 5:怕冷) */
    private Integer acTempScore;
    /** 卫生习惯(1:随性 ~ 5:洁癖) */
    private Integer cleanlinessScore;
    /** 氛围偏好(1:安静自习 ~ 5:电竞社交) */
    private Integer atmosphereScore;
    /** 异味敏感度(1:不敏感 ~ 5:极度敏感) */
    private Integer odorSensitiveScore;
    private String hobbyTags;
    private Integer isAllocated;
    private LocalDateTime updateTime;
}
