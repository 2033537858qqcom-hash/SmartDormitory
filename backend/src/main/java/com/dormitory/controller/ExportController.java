package com.dormitory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dormitory.entity.*;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartEvaluationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel / CSV 报表一键导出控制器 (带 UTF-8 BOM 头，支持 Excel / WPS 直接双击打开)
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final SysUserMapper userMapper;
    private final StudentHabitProfileMapper habitProfileMapper;
    private final DormBedMapper bedMapper;
    private final DormRoomMapper roomMapper;
    private final DormBuildingMapper buildingMapper;
    private final UtilityBillMapper utilityBillMapper;
    private final SmartEvaluationService smartEvaluationService;

    @GetMapping("/students")
    public void exportStudents(HttpServletResponse response) throws IOException {
        List<SysUser> students = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "STUDENT").orderByAsc(SysUser::getUsername)
        );
        Map<Long, StudentHabitProfile> profileMap = habitProfileMapper.selectList(null).stream()
                .collect(Collectors.toMap(StudentHabitProfile::getStudentId, p -> p, (a, b) -> a));
        Map<Long, DormBed> bedMap = bedMapper.selectList(new LambdaQueryWrapper<DormBed>().eq(DormBed::getStatus, 1))
                .stream().filter(b -> b.getStudentId() != null)
                .collect(Collectors.toMap(DormBed::getStudentId, b -> b, (a, b) -> a));
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b));

        StringBuilder sb = new StringBuilder();
        sb.append("学号,姓名,性别,学院,专业,班级,年级,当前住宿床位,作息分(1-5),睡眠分(1-5),空调分(1-5),卫生分(1-5),氛围分(1-5),异味敏感分(1-5),兴趣标签\n");
        for (SysUser s : students) {
            DormBed bed = bedMap.get(s.getId());
            String roomInfo = "待分配";
            if (bed != null) {
                DormRoom r = roomMap.get(bed.getRoomId());
                DormBuilding b = r != null ? buildingMap.get(r.getBuildingId()) : null;
                roomInfo = (b != null ? b.getBuildingName() : "") + "-" + (r != null ? r.getRoomNo() : "") + "室" + bed.getBedNo() + "号床";
            }
            StudentHabitProfile hp = profileMap.get(s.getId());
            sb.append(csvSafe(s.getUsername())).append(",")
              .append(csvSafe(s.getRealName())).append(",")
              .append(s.getGender() != null && s.getGender() == 2 ? "女" : "男").append(",")
              .append(csvSafe(s.getCollege())).append(",")
              .append(csvSafe(s.getMajor())).append(",")
              .append(csvSafe(s.getClassName())).append(",")
              .append(csvSafe(s.getGrade())).append(",")
              .append(csvSafe(roomInfo)).append(",")
              .append(hp != null ? hp.getScheduleScore() : 3).append(",")
              .append(hp != null ? hp.getSleepDepthScore() : 3).append(",")
              .append(hp != null ? hp.getAcTempScore() : 3).append(",")
              .append(hp != null ? hp.getCleanlinessScore() : 3).append(",")
              .append(hp != null ? hp.getAtmosphereScore() : 3).append(",")
              .append(hp != null ? hp.getOdorSensitiveScore() : 5).append(",")
              .append(csvSafe(hp != null ? hp.getHobbyTags() : "")).append("\n");
        }
        writeCsvResponse(response, "学生住宿与六维画像档案表.csv", sb.toString());
    }

    @GetMapping("/utility-bills")
    public void exportUtilityBills(HttpServletResponse response) throws IOException {
        List<UtilityBill> bills = utilityBillMapper.selectList(
                new LambdaQueryWrapper<UtilityBill>().orderByDesc(UtilityBill::getBillMonth)
        );
        Map<Long, DormRoom> roomMap = roomMapper.selectList(null).stream().collect(Collectors.toMap(DormRoom::getId, r -> r));
        Map<Long, DormBuilding> buildingMap = buildingMapper.selectList(null).stream().collect(Collectors.toMap(DormBuilding::getId, b -> b));

        StringBuilder sb = new StringBuilder();
        sb.append("账单月份,公寓楼栋,房间号,本月用电(kWh),阶梯电费(元),本月用水(吨),阶梯水费(元),应缴总额(元),能耗异常预警,预警原因,缴费状态\n");
        for (UtilityBill b : bills) {
            DormRoom r = roomMap.get(b.getRoomId());
            DormBuilding bd = buildingMap.get(b.getBuildingId());
            sb.append(csvSafe(b.getBillMonth())).append(",")
              .append(csvSafe(bd != null ? bd.getBuildingName() : "")).append(",")
              .append(csvSafe(r != null ? r.getRoomNo() : "")).append(",")
              .append(b.getElectricUsed()).append(",")
              .append(b.getElectricCost()).append(",")
              .append(b.getWaterUsed()).append(",")
              .append(b.getWaterCost()).append(",")
              .append(b.getTotalCost()).append(",")
              .append(b.getAbnormalFlag() != null && b.getAbnormalFlag() == 1 ? "突增异常预警" : "正常").append(",")
              .append(csvSafe(b.getAbnormalReason())).append(",")
              .append(b.getPayStatus() != null && b.getPayStatus() == 1 ? "已缴清" : "未缴纳").append("\n");
        }
        writeCsvResponse(response, "园区月度阶梯水电与能耗预警报表.csv", sb.toString());
    }

    @GetMapping("/civilized-rooms")
    public void exportCivilizedRooms(HttpServletResponse response) throws IOException {
        List<Map<String, Object>> evals = smartEvaluationService.evaluateAllRooms();
        StringBuilder sb = new StringBuilder();
        sb.append("排名,公寓楼栋,房间号,在住人数,卫生均分(50%),纪律表现分(30%),水电节能分(20%),综合量化得分,星级,荣誉称号\n");
        int rank = 1;
        for (Map<String, Object> row : evals) {
            sb.append(rank++).append(",")
              .append(csvSafe(String.valueOf(row.get("buildingName")))).append(",")
              .append(csvSafe(String.valueOf(row.get("roomNo")))).append(",")
              .append(row.get("occupiedCount")).append(",")
              .append(row.get("hygieneAvg")).append(",")
              .append(row.get("disciplineScore")).append(",")
              .append(row.get("energyScore")).append(",")
              .append(row.get("civilizedScore")).append(",")
              .append(row.get("starLevel")).append("星,")
              .append(csvSafe(String.valueOf(row.get("honorTitle")))).append("\n");
        }
        writeCsvResponse(response, "多维量化文明寝室评比排行榜.csv", sb.toString());
    }

    private String csvSafe(String val) {
        if (val == null) return "";
        String cleaned = val.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ");
        return "\"" + cleaned + "\"";
    }

    private void writeCsvResponse(HttpServletResponse response, String filename, String content) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        String encodedName = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
        OutputStream out = response.getOutputStream();
        // Write UTF-8 BOM so Microsoft Excel / WPS opens Chinese characters properly
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
