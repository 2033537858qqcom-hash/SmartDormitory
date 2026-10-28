package com.dormitory.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心亮点一：基于六维生活习惯画像的智能分寝算法引擎
 * 采用加权欧氏距离相似度 + 启发式凝聚聚类算法
 */
@Service
@RequiredArgsConstructor
public class SmartAllocationService {

    private final SysUserMapper sysUserMapper;
    private final StudentHabitProfileMapper habitProfileMapper;
    private final DormBuildingMapper buildingMapper;
    private final DormRoomMapper roomMapper;
    private final DormBedMapper bedMapper;
    private final DormAllocationLogMapper allocationLogMapper;

    // 默认六维特征权重：作息时间(0.30)、睡眠质量(0.20)、空调偏好(0.10)、卫生习惯(0.15)、氛围偏好(0.20)、异味敏感度(0.05)
    private static final double[] DEFAULT_WEIGHTS = {0.30, 0.20, 0.10, 0.15, 0.20, 0.05};

    /**
     * 计算两名学生六维生活习惯向量之间的契合度得分 [0, 100]
     */
    public double calculatePairCompatibility(StudentHabitProfile p1, StudentHabitProfile p2) {
        return calculatePairCompatibility(p1, p2, DEFAULT_WEIGHTS);
    }

    public double calculatePairCompatibility(StudentHabitProfile p1, StudentHabitProfile p2, double[] weights) {
        double[] w = (weights != null && weights.length == 6) ? weights : DEFAULT_WEIGHTS;
        double[] v1 = toVector(p1);
        double[] v2 = toVector(p2);
        double weightedDiffSum = 0.0;
        double maxPossibleDiffSum = 0.0;
        for (int i = 0; i < 6; i++) {
            double weight = Math.max(0.01, w[i]);
            double diff = v1[i] - v2[i];
            weightedDiffSum += weight * diff * diff;
            maxPossibleDiffSum += weight * 16.0; // 最大分差为 (5-1)^2 = 16
        }
        double normalizedDistance = Math.sqrt(weightedDiffSum) / Math.sqrt(maxPossibleDiffSum);
        double score = (1.0 - normalizedDistance) * 100.0;
        return Math.max(0.0, Math.min(100.0, score));
    }

    /**
     * 计算一个寝室集群内所有成员的平均两两契合度
     */
    public double calculateGroupHarmony(List<StudentHabitProfile> group) {
        return calculateGroupHarmony(group, DEFAULT_WEIGHTS);
    }

    public double calculateGroupHarmony(List<StudentHabitProfile> group, double[] weights) {
        if (group == null || group.size() <= 1) {
            return 100.0;
        }
        double total = 0.0;
        int pairs = 0;
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                total += calculatePairCompatibility(group.get(i), group.get(j), weights);
                pairs++;
            }
        }
        return BigDecimal.valueOf(total / pairs).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public String explainGroup(List<StudentHabitProfile> group) {
        if (group == null || group.size() < 2) return "单人分组暂无可比较的室友习惯";
        String[] names = {"作息", "睡眠", "空调", "卫生", "氛围", "异味敏感度"};
        int maxIndex = 0;
        double maxRange = -1;
        for (int dimension = 0; dimension < names.length; dimension++) {
            final int index = dimension;
            double min = group.stream().mapToDouble(p -> toVector(p)[index]).min().orElse(0);
            double max = group.stream().mapToDouble(p -> toVector(p)[index]).max().orElse(0);
            if (max - min > maxRange) { maxRange = max - min; maxIndex = dimension; }
        }
        return maxRange == 0 ? "六维习惯均一致" : "组内差异最大：" + names[maxIndex] + "，相差 " + (int) maxRange + " 档";
    }

    private double[] toVector(StudentHabitProfile p) {
        if (p == null) {
            return new double[]{3, 3, 3, 3, 3, 5};
        }
        return new double[]{
                p.getScheduleScore() != null ? p.getScheduleScore() : 3,
                p.getSleepDepthScore() != null ? p.getSleepDepthScore() : 3,
                p.getAcTempScore() != null ? p.getAcTempScore() : 3,
                p.getCleanlinessScore() != null ? p.getCleanlinessScore() : 3,
                p.getAtmosphereScore() != null ? p.getAtmosphereScore() : 3,
                p.getOdorSensitiveScore() != null ? p.getOdorSensitiveScore() : 5
        };
    }

    /**
     * 智能分寝预览/执行（默认权重与纯习惯优先策略）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runSmartAllocation(Long buildingId, boolean commit) {
        return runSmartAllocation(buildingId, commit, DEFAULT_WEIGHTS, "HABIT_FIRST", 1L);
    }

    /**
     * 智能分寝预览/执行（支持自定义六维权重调节、班级同寝策略沙盘对比与真实操作人）
     * @param buildingId 目标楼栋ID
     * @param commit 是否正式写入数据库
     * @param customWeights 六维权重数组 [作息, 睡眠, 空调, 卫生, 氛围, 异味]
     * @param strategy 策略: HABIT_FIRST(纯习惯聚类) / CLASS_MIX(同专业班级优先+习惯聚类)
     * @param operatorId 执行操作员ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runSmartAllocation(Long buildingId, boolean commit, double[] customWeights, String strategy, Long operatorId) {
        double[] weights = (customWeights != null && customWeights.length == 6) ? customWeights : DEFAULT_WEIGHTS;
        boolean classPriority = "CLASS_MIX".equalsIgnoreCase(strategy);
        DormBuilding building = buildingMapper.selectById(buildingId);
        if (building == null) {
            throw new RuntimeException("目标楼栋不存在");
        }

        // 1. 查询符合该楼栋性别的、未分配床位的学生
        List<DormBed> occupiedBeds = bedMapper.selectList(new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1));
        Set<Long> occupiedStudentIds = occupiedBeds.stream()
                .map(DormBed::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<SysUser> candidateStudents = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRole, "STUDENT")
                        .eq(SysUser::getGender, building.getGenderType())
                        .orderByAsc(SysUser::getUsername)
        ).stream().filter(s -> !occupiedStudentIds.contains(s.getId())).collect(Collectors.toList());

        if (candidateStudents.isEmpty()) {
            throw new RuntimeException("当前没有符合该楼栋性别的待分配学生");
        }

        // 获取所有候选学生的画像
        Map<Long, StudentHabitProfile> profileMap = new HashMap<>();
        for (SysUser stu : candidateStudents) {
            StudentHabitProfile p = habitProfileMapper.selectOne(
                    new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, stu.getId())
            );
            if (p == null) {
                p = new StudentHabitProfile();
                p.setStudentId(stu.getId());
                p.setScheduleScore(3);
                p.setSleepDepthScore(3);
                p.setAcTempScore(3);
                p.setCleanlinessScore(3);
                p.setAtmosphereScore(3);
                p.setOdorSensitiveScore(5);
                p.setHobbyTags("待完善画像");
            }
            profileMap.put(stu.getId(), p);
        }

        // 2. 查询该楼栋有空余床位的房间
        List<DormRoom> availableRooms = roomMapper.selectList(
                new LambdaQueryWrapper<DormRoom>()
                        .eq(DormRoom::getBuildingId, buildingId)
                        .eq(DormRoom::getStatus, 1)
                        .orderByAsc(DormRoom::getFloorNo)
                        .orderByAsc(DormRoom::getRoomNo)
        ).stream().filter(r -> r.getOccupiedCount() < r.getCapacity()).collect(Collectors.toList());

        if (availableRooms.isEmpty()) {
            throw new RuntimeException("该楼栋暂无空余床位可供分配");
        }

        // 3. 测算如果采用【传统按学号顺序硬塞】的平均契合度（用于论文对比实验展示！）
        List<Double> traditionalRoomScores = new ArrayList<>();
        int idx = 0;
        for (DormRoom room : availableRooms) {
            int freeSlots = room.getCapacity() - room.getOccupiedCount();
            List<StudentHabitProfile> seqGroup = new ArrayList<>();
            for (int k = 0; k < freeSlots && idx < candidateStudents.size(); k++, idx++) {
                seqGroup.add(profileMap.get(candidateStudents.get(idx).getId()));
            }
            if (!seqGroup.isEmpty()) {
                traditionalRoomScores.add(calculateGroupHarmony(seqGroup, weights));
            }
        }
        double traditionalAvgScore = traditionalRoomScores.stream().mapToDouble(Double::doubleValue).average().orElse(60.0);

        // 4. 执行【启发式贪心凝聚聚类分寝算法】
        List<SysUser> remaining = new ArrayList<>(candidateStudents);
        // 按作息+氛围主成分排序以选取高区分度种子点
        remaining.sort(Comparator.comparingInt(u -> {
            StudentHabitProfile p = profileMap.get(u.getId());
            return p.getScheduleScore() * 10 + p.getAtmosphereScore();
        }));

        List<Map<String, Object>> allocatedRoomDetails = new ArrayList<>();
        int totalAllocatedStudents = 0;
        double sumSmartScore = 0.0;

        for (DormRoom room : availableRooms) {
            if (remaining.isEmpty()) {
                break;
            }
            List<DormBed> emptyBeds = bedMapper.selectList(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getRoomId, room.getId())
                            .eq(DormBed::getStatus, 0)
                            .orderByAsc(DormBed::getBedNo)
            );
            if (emptyBeds.isEmpty()) {
                continue;
            }

            List<SysUser> chosenStudents = new ArrayList<>();
            List<StudentHabitProfile> chosenProfiles = new ArrayList<>();

            // 选取第一个种子学生
            SysUser seed = remaining.remove(0);
            chosenStudents.add(seed);
            chosenProfiles.add(profileMap.get(seed.getId()));

            // 贪心吸纳与当前簇平均契合度最高的学生，直到填满该寝室空床位
            while (chosenStudents.size() < emptyBeds.size() && !remaining.isEmpty()) {
                SysUser bestCandidate = null;
                double bestScore = -1.0;

                for (SysUser candidate : remaining) {
                    StudentHabitProfile cp = profileMap.get(candidate.getId());
                    double sumSim = 0.0;
                    for (int mIdx = 0; mIdx < chosenProfiles.size(); mIdx++) {
                        StudentHabitProfile existing = chosenProfiles.get(mIdx);
                        SysUser existingStu = chosenStudents.get(mIdx);
                        double sim = calculatePairCompatibility(existing, cp, weights);
                        if (classPriority && Objects.equals(existingStu.getClassName(), candidate.getClassName())) {
                            sim += 8.0; // 同班级优先聚拢加权激励
                        }
                        sumSim += sim;
                    }
                    double avgSim = sumSim / chosenProfiles.size();
                    if (avgSim > bestScore) {
                        bestScore = avgSim;
                        bestCandidate = candidate;
                    }
                }
                if (bestCandidate != null) {
                    remaining.remove(bestCandidate);
                    chosenStudents.add(bestCandidate);
                    chosenProfiles.add(profileMap.get(bestCandidate.getId()));
                }
            }

            double harmonyScore = calculateGroupHarmony(chosenProfiles, weights);
            sumSmartScore += harmonyScore;
            totalAllocatedStudents += chosenStudents.size();

            // 组装寝室分配详情与六维均值向量
            List<Map<String, Object>> studentItems = new ArrayList<>();
            for (int i = 0; i < chosenStudents.size(); i++) {
                SysUser s = chosenStudents.get(i);
                StudentHabitProfile hp = chosenProfiles.get(i);
                DormBed targetBed = emptyBeds.get(i);

                if (commit) {
                    targetBed.setStudentId(s.getId());
                    targetBed.setCheckInDate(LocalDate.now());
                    targetBed.setStatus(1);
                    bedMapper.updateById(targetBed);

                    hp.setIsAllocated(1);
                    if (hp.getId() != null) {
                        habitProfileMapper.updateById(hp);
                    } else {
                        habitProfileMapper.insert(hp);
                    }
                }

                Map<String, Object> sMap = new LinkedHashMap<>();
                sMap.put("studentId", s.getId());
                sMap.put("studentNo", s.getUsername());
                sMap.put("realName", s.getRealName());
                sMap.put("className", s.getClassName());
                sMap.put("bedNo", targetBed.getBedNo());
                sMap.put("scheduleScore", hp.getScheduleScore());
                sMap.put("sleepDepthScore", hp.getSleepDepthScore());
                sMap.put("acTempScore", hp.getAcTempScore());
                sMap.put("cleanlinessScore", hp.getCleanlinessScore());
                sMap.put("atmosphereScore", hp.getAtmosphereScore());
                sMap.put("odorSensitiveScore", hp.getOdorSensitiveScore());
                sMap.put("hobbyTags", hp.getHobbyTags());
                studentItems.add(sMap);
            }

            if (commit) {
                room.setOccupiedCount(room.getOccupiedCount() + chosenStudents.size());
                room.setHarmonyScore(BigDecimal.valueOf(harmonyScore));
                roomMapper.updateById(room);
            }

            Map<String, Object> roomDetail = new LinkedHashMap<>();
            roomDetail.put("roomId", room.getId());
            roomDetail.put("buildingName", building.getBuildingName());
            roomDetail.put("roomNo", room.getRoomNo());
            roomDetail.put("harmonyScore", harmonyScore);
            roomDetail.put("groupStyleTag", inferGroupStyleTag(chosenProfiles));
            roomDetail.put("explanation", explainGroup(chosenProfiles));
            roomDetail.put("students", studentItems);
            allocatedRoomDetails.add(roomDetail);
        }

        double smartAvgScore = allocatedRoomDetails.isEmpty() ? 0.0 :
                BigDecimal.valueOf(sumSmartScore / allocatedRoomDetails.size()).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double traditionalFormatted = BigDecimal.valueOf(traditionalAvgScore).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double improvement = BigDecimal.valueOf(smartAvgScore - traditionalFormatted).setScale(2, RoundingMode.HALF_UP).doubleValue();

        String strategyLabel = classPriority ? "同班聚拢约束+六维加权贪心聚类算法" : "六维加权欧氏距离+贪心凝聚聚类算法";
        String batchNo = "BATCH-" + DateUtil.format(new Date(), "yyyyMMddHHmmss");
        if (commit && !allocatedRoomDetails.isEmpty()) {
            DormAllocationLog log = new DormAllocationLog();
            log.setBatchNo(batchNo);
            log.setBuildingId(buildingId);
            log.setAllocatedStudentCount(totalAllocatedStudents);
            log.setAllocatedRoomCount(allocatedRoomDetails.size());
            log.setAvgHarmonyScore(BigDecimal.valueOf(smartAvgScore));
            log.setAlgorithmStrategy(strategyLabel);
            log.setDetailJson(JSONUtil.toJsonStr(allocatedRoomDetails));
            log.setOperatorId(operatorId != null ? operatorId : 1L);
            allocationLogMapper.insert(log);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("committed", commit);
        result.put("strategyLabel", strategyLabel);
        result.put("buildingName", building.getBuildingName());
        result.put("allocatedStudentCount", totalAllocatedStudents);
        result.put("allocatedRoomCount", allocatedRoomDetails.size());
        result.put("smartAvgHarmonyScore", smartAvgScore);
        result.put("traditionalAvgHarmonyScore", traditionalFormatted);
        result.put("scoreImprovement", improvement);
        result.put("roomAllocations", allocatedRoomDetails);
        result.put("scoreNote", "契合度仅比较本次新分配学生，未计入原住室友；未填写问卷按默认画像计算。单人组为 100 分，不代表已有室友匹配度。");
        result.put("unallocatedStudents", remaining.stream().map(student -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", student.getId()); item.put("studentNo", student.getUsername());
            item.put("realName", student.getRealName()); item.put("reason", "可用空床不足或床位数据不一致");
            return item;
        }).collect(Collectors.toList()));
        return result;
    }

    /**
     * 动态重算并同步更新指定房间的入住人数与室友契合度 harmony_score（供调宿/退宿/手动分床联动调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public double recalculateRoomHarmony(Long roomId) {
        if (roomId == null) return 90.0;
        DormRoom room = roomMapper.selectById(roomId);
        if (room == null) return 90.0;

        List<DormBed> occupiedBeds = bedMapper.selectList(
                new LambdaQueryWrapper<DormBed>()
                        .eq(DormBed::getRoomId, roomId)
                        .eq(DormBed::getStatus, 1)
        );
        List<StudentHabitProfile> profiles = new ArrayList<>();
        for (DormBed bed : occupiedBeds) {
            if (bed.getStudentId() != null) {
                StudentHabitProfile hp = habitProfileMapper.selectOne(
                        new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, bed.getStudentId())
                );
                if (hp != null) {
                    profiles.add(hp);
                }
            }
        }
        room.setOccupiedCount(occupiedBeds.size());
        double harmony = occupiedBeds.isEmpty() ? 0.0 : calculateGroupHarmony(profiles);
        room.setHarmonyScore(BigDecimal.valueOf(harmony));
        roomMapper.updateById(room);
        return harmony;
    }

    /**
     * 智能调宿推荐引擎：计算指定学生与所有同性别有空床寝室的六维生活习惯契合度，按匹配度降序推荐
     */
    public List<Map<String, Object>> recommendTransferRooms(Long studentId) {
        SysUser student = sysUserMapper.selectById(studentId);
        if (student == null) {
            throw new RuntimeException("未找到该学生信息");
        }
        StudentHabitProfile myProfile = habitProfileMapper.selectOne(
                new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, studentId)
        );
        DormBed myBed = bedMapper.selectOne(
                new LambdaQueryWrapper<DormBed>()
                        .eq(DormBed::getStudentId, studentId)
                        .eq(DormBed::getStatus, 1)
        );
        Long currentRoomId = myBed != null ? myBed.getRoomId() : -1L;

        // 查询同性别楼栋
        List<DormBuilding> sameGenderBuildings = buildingMapper.selectList(
                new LambdaQueryWrapper<DormBuilding>().eq(DormBuilding::getGenderType, student.getGender())
        );
        Map<Long, DormBuilding> buildingMap = sameGenderBuildings.stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b));
        if (buildingMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<DormRoom> candidateRooms = roomMapper.selectList(
                new LambdaQueryWrapper<DormRoom>()
                        .in(DormRoom::getBuildingId, buildingMap.keySet())
                        .eq(DormRoom::getStatus, 1)
                        .orderByAsc(DormRoom::getBuildingId)
                        .orderByAsc(DormRoom::getRoomNo)
        ).stream().filter(r -> !Objects.equals(r.getId(), currentRoomId) && r.getOccupiedCount() < r.getCapacity())
                .collect(Collectors.toList());

        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (DormRoom room : candidateRooms) {
            DormBuilding b = buildingMap.get(room.getBuildingId());
            List<DormBed> beds = bedMapper.selectList(
                    new LambdaQueryWrapper<DormBed>()
                            .eq(DormBed::getRoomId, room.getId())
                            .orderByAsc(DormBed::getBedNo)
            );
            List<Integer> freeBedNos = new ArrayList<>();
            List<String> roommateNames = new ArrayList<>();
            List<StudentHabitProfile> roommateProfiles = new ArrayList<>();

            for (DormBed bed : beds) {
                if (bed.getStatus() == 0) {
                    freeBedNos.add(bed.getBedNo());
                } else if (bed.getStatus() == 1 && bed.getStudentId() != null) {
                    SysUser rm = sysUserMapper.selectById(bed.getStudentId());
                    if (rm != null) {
                        roommateNames.add(rm.getRealName());
                    }
                    StudentHabitProfile rp = habitProfileMapper.selectOne(
                            new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, bed.getStudentId())
                    );
                    if (rp != null) {
                        roommateProfiles.add(rp);
                    }
                }
            }
            if (freeBedNos.isEmpty()) continue;

            double matchScore;
            double predictedHarmony;
            String styleTag;
            if (roommateProfiles.isEmpty()) {
                matchScore = 92.0; // 空寝室基准推荐分
                predictedHarmony = 100.0;
                styleTag = "全新空寝·自由塑风";
            } else {
                double sumPair = 0.0;
                for (StudentHabitProfile rp : roommateProfiles) {
                    sumPair += calculatePairCompatibility(myProfile, rp);
                }
                matchScore = BigDecimal.valueOf(sumPair / roommateProfiles.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();
                List<StudentHabitProfile> merged = new ArrayList<>(roommateProfiles);
                merged.add(myProfile);
                predictedHarmony = calculateGroupHarmony(merged);
                styleTag = inferGroupStyleTag(roommateProfiles);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roomId", room.getId());
            item.put("buildingId", room.getBuildingId());
            item.put("buildingName", b != null ? b.getBuildingName() : "");
            item.put("roomNo", room.getRoomNo());
            item.put("floorNo", room.getFloorNo());
            item.put("capacity", room.getCapacity());
            item.put("occupiedCount", room.getOccupiedCount());
            item.put("freeBedNos", freeBedNos);
            item.put("recommendedBedNo", freeBedNos.get(0));
            item.put("roommateNames", roommateNames);
            item.put("currentHarmonyScore", room.getHarmonyScore());
            item.put("matchScore", matchScore);
            item.put("predictedHarmonyScore", predictedHarmony);
            item.put("styleTag", styleTag);
            recommendations.add(item);
        }

        recommendations.sort((a, b) -> Double.compare((Double) b.get("matchScore"), (Double) a.get("matchScore")));
        return recommendations;
    }

    /**
     * 一键重置 2026 级待分寝新生演示数据（方便答辩现场无限次重复演示智能分寝对比实验）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetDemoFreshmen() {
        List<SysUser> freshmen = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRole, "STUDENT")
                        .eq(SysUser::getGrade, "2026级")
        );
        int releasedCount = 0;
        Set<Long> affectedRoomIds = new HashSet<>();

        for (SysUser stu : freshmen) {
            List<DormBed> beds = bedMapper.selectList(
                    new LambdaQueryWrapper<DormBed>().eq(DormBed::getStudentId, stu.getId())
            );
            for (DormBed bed : beds) {
                affectedRoomIds.add(bed.getRoomId());
                bed.setStudentId(null);
                bed.setCheckInDate(null);
                bed.setStatus(0);
                bedMapper.updateById(bed);
                releasedCount++;
            }
            StudentHabitProfile hp = habitProfileMapper.selectOne(
                    new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, stu.getId())
            );
            if (hp != null && hp.getIsAllocated() != null && hp.getIsAllocated() == 1) {
                hp.setIsAllocated(0);
                habitProfileMapper.updateById(hp);
            }
        }

        for (Long rId : affectedRoomIds) {
            recalculateRoomHarmony(rId);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("freshmenCount", freshmen.size());
        res.put("releasedBedCount", releasedCount);
        return res;
    }

    private String inferGroupStyleTag(List<StudentHabitProfile> profiles) {
        double avgSchedule = profiles.stream().mapToInt(StudentHabitProfile::getScheduleScore).average().orElse(3.0);
        double avgAtmos = profiles.stream().mapToInt(StudentHabitProfile::getAtmosphereScore).average().orElse(3.0);
        if (avgSchedule <= 2.2 && avgAtmos <= 2.2) {
            return "早睡自律·学术深造型寝室";
        } else if (avgSchedule >= 4.0 && avgAtmos >= 4.0) {
            return "晚睡活跃·电竞社交型寝室";
        } else {
            return "作息均衡·综合融洽型寝室";
        }
    }

    /**
     * 获取指定房间的室友六维画像与契合度雷达图数据
     */
    public Map<String, Object> getRoomRadarAnalysis(Long roomId) {
        DormRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        DormBuilding building = buildingMapper.selectById(room.getBuildingId());
        List<DormBed> beds = bedMapper.selectList(
                new LambdaQueryWrapper<DormBed>()
                        .eq(DormBed::getRoomId, roomId)
                        .orderByAsc(DormBed::getBedNo)
        );

        List<Map<String, Object>> roommates = new ArrayList<>();
        List<StudentHabitProfile> profiles = new ArrayList<>();

        for (DormBed bed : beds) {
            if (bed.getStudentId() != null && bed.getStatus() == 1) {
                SysUser stu = sysUserMapper.selectById(bed.getStudentId());
                if (stu != null) {
                    StudentHabitProfile hp = habitProfileMapper.selectOne(
                            new LambdaQueryWrapper<StudentHabitProfile>().eq(StudentHabitProfile::getStudentId, stu.getId())
                    );
                    if (hp != null) {
                        profiles.add(hp);
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("bedNo", bed.getBedNo());
                    item.put("studentId", stu.getId());
                    item.put("studentNo", stu.getUsername());
                    item.put("realName", stu.getRealName());
                    item.put("className", stu.getClassName());
                    item.put("phone", stu.getPhone());
                    item.put("profile", hp);
                    item.put("vector", toVector(hp));
                    roommates.add(item);
                }
            }
        }

        double harmony = profiles.isEmpty() ? 90.0 : calculateGroupHarmony(profiles);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", room);
        data.put("buildingName", building != null ? building.getBuildingName() : "");
        data.put("harmonyScore", harmony);
        data.put("styleTag", profiles.isEmpty() ? "温馨公寓" : inferGroupStyleTag(profiles));
        data.put("roommates", roommates);
        return data;
    }
}
