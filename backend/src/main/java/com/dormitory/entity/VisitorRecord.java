package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("visitor_record")
public class VisitorRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String visitorName;
    private String visitorPhone;
    private String idCardMask;
    private Long visitStudentId;
    private Long buildingId;
    private String roomNo;
    private String relation;
    private String visitReason;
    private LocalDateTime enterTime;
    private LocalDateTime leaveTime;
    private Integer status;
}
