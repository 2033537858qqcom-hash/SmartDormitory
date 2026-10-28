package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_message")
public class UserMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    /** REPAIR / APPEAL / ADJUST / UTILITY / VISITOR / SYSTEM */
    private String msgType;
    private String bizId;
    /** 0 unread, 1 read */
    private Integer readFlag;
    private LocalDateTime createTime;
}
