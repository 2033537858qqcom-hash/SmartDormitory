package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dorm_building")
public class DormBuilding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String buildingCode;
    private String buildingName;
    /** 1-男生公寓, 2-女生公寓 */
    private Integer genderType;
    private Integer totalFloors;
    private Long managerId;
    private String locationDesc;
    private String remark;
    private LocalDateTime createTime;
}
