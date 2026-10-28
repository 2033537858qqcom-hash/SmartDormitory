package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.UserMessage;
import com.dormitory.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserMessageService {

    private final UserMessageMapper messageMapper;

    public UserMessage push(Long userId, String title, String content, String msgType, String bizId) {
        if (userId == null) return null;
        UserMessage m = new UserMessage();
        m.setUserId(userId);
        m.setTitle(title);
        m.setContent(content);
        m.setMsgType(msgType != null ? msgType : "SYSTEM");
        m.setBizId(bizId);
        m.setReadFlag(0);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
        return m;
    }

    public List<UserMessage> listForUser(Long userId, Integer unreadOnly, int limit) {
        LambdaQueryWrapper<UserMessage> q = new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .orderByDesc(UserMessage::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        if (unreadOnly != null && unreadOnly == 1) {
            q.eq(UserMessage::getReadFlag, 0);
        }
        return messageMapper.selectList(q);
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getReadFlag, 0));
    }

    public void markRead(Long userId, Long messageId) {
        if (messageId == null) {
            UserMessage patch = new UserMessage();
            patch.setReadFlag(1);
            messageMapper.update(patch, new LambdaQueryWrapper<UserMessage>()
                    .eq(UserMessage::getUserId, userId)
                    .eq(UserMessage::getReadFlag, 0));
            return;
        }
        UserMessage m = messageMapper.selectById(messageId);
        if (m != null && userId.equals(m.getUserId())) {
            m.setReadFlag(1);
            messageMapper.updateById(m);
        }
    }
}
