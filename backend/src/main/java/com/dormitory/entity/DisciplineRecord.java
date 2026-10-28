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
    /** 申诉证明类型，如 实验室证明/医院证明/其他 */
    private String appealProofType;
    /** 证明人姓名 */
    private String appealWitness;
    /** 附件占位：JSON 数组字符串，如 ["/demo/proof/lab.png"] 或 data URL 列表 */
    private String appealAttachments;
    private LocalDateTime createTime;
}
