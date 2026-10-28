package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("repair_order")
public class RepairOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long studentId;
    private Long buildingId;
    private Long roomId;
    /** ELECTRIC, WATER, WOOD, NETWORK */
    private String repairType;
    private String title;
    private String description;
    /** HIGH, NORMAL, LOW */
    private String priority;
    private Long assignedWorkerId;
    private String dispatchRemark;
    /** PENDING, ASSIGNED, PROCESSING, COMPLETED, RATED */
    private String status;
    private String repairResult;
    private Integer rating;
    private String ratingComment;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    /** JSON 数组：本地上传路径或 dataURL（演示用） */
    private String photoUrls;
}
