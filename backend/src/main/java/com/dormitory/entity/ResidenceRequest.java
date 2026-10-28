package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("residence_request")
public class ResidenceRequest {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private Long roomId;
    private String requestType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;
    private String status;
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
