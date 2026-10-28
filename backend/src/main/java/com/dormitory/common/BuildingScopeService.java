package com.dormitory.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.DormBuilding;
import com.dormitory.mapper.DormBuildingMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 宿管楼栋数据范围：DORM_MANAGER 仅可访问 manager_id 指向自己的楼栋；其他角色返回 null 表示不限制。
 */
@Component
@RequiredArgsConstructor
public class BuildingScopeService {
    private final DormBuildingMapper buildingMapper;

    /** @return null=不限制；非 null=允许的楼栋 ID 集合（可能为空） */
    public Set<Long> resolve(HttpServletRequest request) {
        if (request == null) return null;
        Object role = request.getAttribute("currentUserRole");
        if (!"DORM_MANAGER".equals(role)) return null;
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) return Collections.emptySet();
        return buildingMapper.selectList(
                new LambdaQueryWrapper<DormBuilding>().eq(DormBuilding::getManagerId, userId)
        ).stream().map(DormBuilding::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean allows(Set<Long> scope, Long buildingId) {
        return scope == null || (buildingId != null && scope.contains(buildingId));
    }

    public Result<?> forbiddenIfOutOfScope(Set<Long> scope, Long buildingId) {
        if (allows(scope, buildingId)) return null;
        return Result.error(403, "只能操作管辖楼栋内的数据");
    }
}
