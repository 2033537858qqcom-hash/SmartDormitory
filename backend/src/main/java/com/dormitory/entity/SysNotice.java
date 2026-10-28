package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_notice")
public class SysNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String noticeType;
    private String targetRole;
    private String publisherName;
    private Integer isTop;
    private Integer viewCount;
    private LocalDateTime createTime;
}
