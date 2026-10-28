package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dorm_adjust_apply")
public class DormAdjustApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    /** ADJUST-调宿, CHECKOUT-退宿 */
    private String applyType;
    private Long oldRoomId;
    private Integer oldBedNo;
    private Long targetRoomId;
    private Integer targetBedNo;
    private String reason;
    /** PENDING, APPROVED, REJECTED */
    private String status;
    private Long reviewerId;
    private String reviewComment;
    private String checkoutChecklist;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
