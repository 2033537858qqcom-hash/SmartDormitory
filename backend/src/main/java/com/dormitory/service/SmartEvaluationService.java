package com.dormitory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 核心亮点四：多维量化“文明寝室”动态评比引擎
 * 公式：综合得分 = 0.5 * 卫生检查均分 + 0.3 * (100 - 违纪晚归累计扣分) + 0.2 * 水电节能表现分
 */
@Service
@RequiredArgsConstructor
public class SmartEvaluationService {

    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final HygieneInspectionMapper hygieneMapper;
    private final DisciplineRecordMapper disciplineMapper;
    private final UtilityBillMapper utilityBillMapper;
    private final SysNoticeMapper noticeMapper;

    public List<Map<String, Object>> evaluateAllRooms() {
        return evaluateAllRooms(false);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> evaluateAllRooms(boolean persist) {
        List<DormRoom> rooms = roomMapper.selectList(
                new LambdaQueryWrapper<DormRoom>().orderByAsc(DormRoom::getBuildingId).orderByAsc(DormRoom::getRoomNo)
        );
        Map<Long, String> buildingNames = new HashMap<>();
        for (DormBuilding b : buildingMapper.selectList(null)) {
            buildingNames.put(b.getId(), b.getBuildingName());
        }

        List<Map<String, Object>> evaluationList = new ArrayList<>();
        for (DormRoom room : rooms) {
            // 1. 卫生均分 (50%权重)
            List<HygieneInspection> checks = hygieneMapper.selectList(
                    new LambdaQueryWrapper<HygieneInspection>().eq(HygieneInspection::getRoomId, room.getId())
            );
            double hygieneAvg = checks.stream().mapToInt(HygieneInspection::getTotalScore).average().orElse(90.0);

            // 2. 纪律表现分 (30%权重，status=2申诉撤销的记录不计入扣分)
            List<DisciplineRecord> records = disciplineMapper.selectList(
                    new LambdaQueryWrapper<DisciplineRecord>()
                            .eq(DisciplineRecord::getRoomId, room.getId())
                            .in(DisciplineRecord::getStatus, Arrays.asList(1, 3))
            );
            int totalDeduct = records.stream().mapToInt(DisciplineRecord::getDeductScore).sum();
            double disciplineScore = Math.max(40.0, 100.0 - totalDeduct * 2.0);

            // 3. 水电节能表现分 (20%权重)
            List<UtilityBill> bills = utilityBillMapper.selectList(
                    new LambdaQueryWrapper<UtilityBill>().eq(UtilityBill::getRoomId, room.getId())
            );
            long abnormalCount = bills.stream().filter(b -> b.getAbnormalFlag() != null && b.getAbnormalFlag() == 1).count();
            double energyScore = Math.max(50.0, 96.0 - abnormalCount * 15.0);

            double totalScore = 0.50 * hygieneAvg + 0.30 * disciplineScore + 0.20 * energyScore;
            BigDecimal formattedScore = BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP);

            int starLevel;
            String honorTitle;
            if (totalScore >= 92.0) {
                starLevel = 5;
                honorTitle = "五星文明示范寝室";
            } else if (totalScore >= 82.0) {
                starLevel = 4;
                honorTitle = "四星优良寝室";
            } else if (totalScore >= 70.0) {
                starLevel = 3;
                honorTitle = "三星合格寝室";
            } else {
                starLevel = 2;
                honorTitle = "重点督导整改寝室";
            }

            if (persist) {
                room.setCivilizedScore(formattedScore);
                room.setStarLevel(starLevel);
                roomMapper.updateById(room);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roomId", room.getId());
            item.put("buildingId", room.getBuildingId());
            item.put("buildingName", buildingNames.getOrDefault(room.getBuildingId(), ""));
            item.put("roomNo", room.getRoomNo());
            item.put("occupiedCount", room.getOccupiedCount());
            item.put("electricBalance", room.getElectricBalance());
            item.put("hygieneAvg", BigDecimal.valueOf(hygieneAvg).setScale(1, RoundingMode.HALF_UP));
            item.put("disciplineScore", BigDecimal.valueOf(disciplineScore).setScale(1, RoundingMode.HALF_UP));
            item.put("energyScore", BigDecimal.valueOf(energyScore).setScale(1, RoundingMode.HALF_UP));
            item.put("civilizedScore", formattedScore);
            item.put("starLevel", starLevel);
            item.put("honorTitle", honorTitle);
            evaluationList.add(item);
        }

        evaluationList.sort((a, b) -> ((BigDecimal) b.get("civilizedScore")).compareTo((BigDecimal) a.get("civilizedScore")));
        return evaluationList;
    }

    /**
     * 一键为所有五星级文明示范寝室发放电费奖励补贴，并自动同步发布表彰公告
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rewardFiveStarRooms(BigDecimal subsidyAmount) {
        BigDecimal reward = (subsidyAmount != null && subsidyAmount.compareTo(BigDecimal.ZERO) > 0)
                ? subsidyAmount : BigDecimal.valueOf(30.00);
        List<Map<String, Object>> evalList = evaluateAllRooms(true);
        List<String> rewardedNames = new ArrayList<>();

        for (Map<String, Object> row : evalList) {
            Integer star = (Integer) row.get("starLevel");
            Integer occ = (Integer) row.get("occupiedCount");
            if (star != null && star >= 5 && occ != null && occ > 0) {
                Long roomId = (Long) row.get("roomId");
                DormRoom room = roomMapper.selectById(roomId);
                if (room != null) {
                    room.setElectricBalance(room.getElectricBalance().add(reward));
                    roomMapper.updateById(room);
                    rewardedNames.add(row.get("buildingName") + "-" + row.get("roomNo") + "室");
                }
            }
        }

        if (!rewardedNames.isEmpty()) {
            SysNotice notice = new SysNotice();
            notice.setTitle("【文明寝室奖励】五星文明示范寝室电费补贴发放公示（" + String.join("、", rewardedNames) + "）");
            notice.setContent("经园区多维量化评比核算，恭喜 " + String.join("、", rewardedNames)
                    + " 获评本阶段“五星文明示范寝室”！后勤中心已向上述寝室电费账户自动发放 ¥"
                    + reward.setScale(2, RoundingMode.HALF_UP) + " 元（折合50度免费电费额度）文明奖励补贴，望全体寝室以先进为榜样！");
            notice.setNoticeType("HYGIENE");
            notice.setTargetRole("ALL");
            notice.setPublisherName("校后勤保障部·宿管中心");
            notice.setIsTop(1);
            notice.setViewCount(128);
            notice.setCreateTime(LocalDateTime.now());
            noticeMapper.insert(notice);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("rewardedCount", rewardedNames.size());
        res.put("rewardAmount", reward);
        res.put("rewardedRooms", rewardedNames);
        return res;
    }
}
