package com.dormitory.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("业务参数校验不通过: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<String> handleIllegalState(IllegalStateException e) {
        log.warn("业务状态校验异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统发生未捕获异常: {}", e.getMessage(), e);
        String msg = e.getMessage();
        if (msg != null && (msg.contains("SQL") || msg.contains("nested exception") || msg.contains("MyBatis") || msg.contains("JDBC"))) {
            return Result.error("数据访问异常，请联系系统管理员");
        }
        return Result.error(msg != null ? msg : "系统内部错误");
    }
}
