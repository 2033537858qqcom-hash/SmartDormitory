package com.dormitory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    /** ADMIN, DORM_MANAGER, REPAIRMAN, STUDENT */
    private String role;
    /** 1-男, 2-女 */
    private Integer gender;
    private String phone;
    private String email;
    private String avatar;
    private String college;
    private String major;
    private String className;
    private String grade;
    /** ELECTRIC, WATER, WOOD, NETWORK */
    private String repairSpecialty;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
