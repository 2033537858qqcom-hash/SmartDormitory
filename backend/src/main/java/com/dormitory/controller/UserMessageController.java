package com.dormitory.controller;

import com.dormitory.common.Result;
import com.dormitory.entity.UserMessage;
import com.dormitory.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class UserMessageController {

    private final UserMessageService messageService;

    @GetMapping
    public Result<List<UserMessage>> list(@RequestParam(required = false) Integer unreadOnly,
                                          @RequestParam(required = false, defaultValue = "30") Integer limit,
                                          HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("currentUserId");
        return Result.success(messageService.listForUser(uid, unreadOnly, limit != null ? limit : 30));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("currentUserId");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", messageService.unreadCount(uid));
        return Result.success(data);
    }

    @PostMapping("/read")
    public Result<String> markRead(@RequestBody(required = false) Map<String, Object> body,
                                   HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("currentUserId");
        Long id = null;
        if (body != null && body.get("id") != null) {
            id = Long.valueOf(body.get("id").toString());
        }
        messageService.markRead(uid, id);
        return Result.success("已标记已读", "OK");
    }
}
