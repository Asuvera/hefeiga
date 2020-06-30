package cc.mrbird.febs.lxj.controller;


import cc.mrbird.febs.common.entity.DeptTree;
import cc.mrbird.febs.common.utils.TreeUtil;
import cc.mrbird.febs.common.utils.TreeUtilByZsh;
import cc.mrbird.febs.lxj.entity.*;
import cc.mrbird.febs.lxj.mapper.DepartmentMapper;
import cc.mrbird.febs.lxj.service.AttendanceService;
import cc.mrbird.febs.lxj.service.DepartmentService;
import cc.mrbird.febs.lxj.service.IOrgUserService;
import cc.mrbird.febs.lxj.utils.DateRange;
import cc.mrbird.febs.lxj.utils.ExportExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiAttendanceGetcolumnvalRequest;
import com.dingtalk.api.request.OapiDepartmentListRequest;
import com.dingtalk.api.response.OapiAttendanceGetcolumnvalResponse;
import com.dingtalk.api.response.OapiDepartmentListResponse;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.StatementType;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.formula.functions.T;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@Slf4j
@RequestMapping("/reportForm")
public class ReportFormController {
    @Autowired
    IOrgUserService OrgUserService;
    @Autowired
    AttendanceService attendanceService;
    @Autowired
    DepartmentService departmentService;
    @Autowired
    DepartmentMapper departmentMapper;

    /**
     * @param reportFormCondition"2018-08-04 13:05:06""2019-06-05 12:25:36"
     * @return
     * @throws ParseException
     */

    @RequestMapping(value = "/openForm", method = RequestMethod.POST)
    @ResponseBody

    public Object open(@RequestBody ReportFormCondition reportFormCondition) throws ParseException, ApiException {
        String startDate = reportFormCondition.getStartDate();
        String endDate = reportFormCondition.getEndDate();

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateRange d = new DateRange(df.parse(startDate), df.parse(endDate));
        List<DateRange> list = d.splitByMonth();
        //list.forEach(x -> System.out.println("start=" + df.format(x.getStart()) + ", end=" + df.format(x.getEnd())));

        String position = reportFormCondition.getPosition();
        String[] departmentIds = reportFormCondition.getDepartmentIds();

        String sourceParentId = departmentIds[0];
        String deptString = departmentMapper.selectAllChildDept(Integer.parseInt(sourceParentId));
        System.out.println(deptString);
        String[] split = deptString.split(",");
        List<String> deptList = new ArrayList<>();
        Collections.addAll(deptList, split);
        deptList.remove(0);
        List<OrgUserByPlus> orgUserByPluses = OrgUserService.selectUsersBydeptIdAndPosition(deptList, position);
        String accessToken = attendanceService.getAccessToken();
        List<ReportFormUserInfo> userInfoList = new ArrayList<>();
        int num = orgUserByPluses.size();
        System.out.println("查询" + num + "人考勤报表");
        for (OrgUserByPlus orgUserByPlus : orgUserByPluses) {
            ReportFormUserInfo reportFormUserInfo = new ReportFormUserInfo();
            reportFormUserInfo.setName(orgUserByPlus.getName());
            reportFormUserInfo.setDuty(orgUserByPlus.getPosition());

            for (int k = 0; k < list.size(); k++) {
                String startDateString = list.get(k).getStart().toString();
                System.out.println("拆分后开始时间" + startDateString);
                String endDateString = list.get(k).getEnd().toString();
                System.out.println("拆分后结束时间" + endDateString);
                DingTalkClient client3 = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/attendance/getcolumnval");
                OapiAttendanceGetcolumnvalRequest req3 = new OapiAttendanceGetcolumnvalRequest();
                req3.setUserid(orgUserByPlus.getSourceid());
                //                  出勤天数,  旷工天数 上班缺卡次数 下班缺卡次数 迟到 早退    工作时长  加班时长    外出次数
                req3.setColumnIdList("7255015,7255027,7255025,7255026,7255018,7255023,7255017,115051078,115429873");
                req3.setFromDate(list.get(k).getStart());
                req3.setToDate(list.get(k).getEnd());
                OapiAttendanceGetcolumnvalResponse rsp3 = client3.execute(req3, accessToken);
                JSONObject jsonObject = JSON.parseObject(rsp3.getBody());
                JSONObject result = jsonObject.getJSONObject("result");

                JSONArray column_vals = result.getJSONArray("column_vals");
                System.out.println(column_vals);
                for (int i = 0; i < column_vals.size(); i++) {
                    Object o = column_vals.getJSONObject(i).getJSONObject("column_vo").get("id");
                    if ("7255015".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i1 = 0; i1 < column_vals_details.size(); i1++) {
                            if (i1 == 0) {
                                reportFormUserInfo.setAttendance_days(new BigDecimal(column_vals_details.getJSONObject(i1).getDoubleValue("value")));
                            } else {
                                BigDecimal value = reportFormUserInfo.getAttendance_days().add(new BigDecimal(column_vals_details.getJSONObject(i1).getDoubleValue("value")));
                                reportFormUserInfo.setAttendance_days(value);
                            }

                        }
                    }
                    if ("7255027".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i2 = 0; i2 < column_vals_details.size(); i2++) {
                            int value = reportFormUserInfo.getAbsenteeism_days() + column_vals_details.getJSONObject(i2).getIntValue("value");
                            reportFormUserInfo.setAbsenteeism_days(value);
                        }
                    }
                    //上班缺卡次数
                    if ("7255025".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i3 = 0; i3 < column_vals_details.size(); i3++) {
                            int value = reportFormUserInfo.getWork_lack_card_times() + column_vals_details.getJSONObject(i3).getIntValue("value");
                            reportFormUserInfo.setWork_lack_card_times(value);
                        }
                    }
                    //下班缺卡次数
                    if ("7255026".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getWork_lack_card_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setWork_lack_card_times(value);
                        }
                    }
                    //迟到次数
                    if ("7255018".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getLate_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setLate_times(value);
                        }
                    }
                    //早退次数
                    if ("7255023".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getLeave_early_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setLeave_early_times(value);
                        }
                    }
                    //补卡次数
                    if ("22732321".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getMaking_up_lack_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setMaking_up_lack_times(value);
                        }
                    }
                    //工作时长
                    if ("7255017".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            if (i4 == 0) {
                                reportFormUserInfo.setAttendance_work_time(new BigDecimal(column_vals_details.getJSONObject(i4).getDoubleValue("value")));
                            } else {
                                BigDecimal value = reportFormUserInfo.getAttendance_work_time().add(new BigDecimal(column_vals_details.getJSONObject(i4).getDoubleValue("value")));
                                reportFormUserInfo.setAttendance_work_time(value);
                            }
                        }
                    }
                    //加班时长
                    if ("115051078".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            if (i4 == 0) {
                                reportFormUserInfo.setExtra_work_time(new BigDecimal(column_vals_details.getJSONObject(i4).getDoubleValue("value")));
                            } else {
                                BigDecimal value = reportFormUserInfo.getExtra_work_time().add(new BigDecimal(column_vals_details.getJSONObject(i4).getDoubleValue("value")));
                                reportFormUserInfo.setExtra_work_time(value);
                            }
                        }
                    }
                    //外出次数
                    if ("115429873".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getOut_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setOut_times(value);
                        }
                    }


                }
            }
            userInfoList.add(reportFormUserInfo);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("data", userInfoList);
        map.put("num", num);

        Object o = JSONObject.toJSON(map);
        return o;
    }


    @RequestMapping(value = "/departmentTree")
    @ResponseBody
    public Object createDepartmentTree() throws ParseException, ApiException {
        String accessToken = attendanceService.getAccessToken();
        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/department/list");
        OapiDepartmentListRequest request = new OapiDepartmentListRequest();
        request.setId("1");
        request.setHttpMethod("GET");
        OapiDepartmentListResponse response = client.execute(request, accessToken);
        System.out.println(response.getBody());
        // List<DeptTree<T>> nodes =  departmentService.findAll();
        List<OrgDepartment> allDepartment = departmentService.getAllDepartment();
        List<DeptTree<T>> deptTrees = new ArrayList<>();
        for (int i = 0; i < allDepartment.size(); i++) {
            DeptTree<T> objectDeptTree = new DeptTree<T>();
            objectDeptTree.setId(allDepartment.get(i).getSourceId());
            objectDeptTree.setParentId(allDepartment.get(i).getSourceParentId());
            objectDeptTree.setName(allDepartment.get(i).getName());
            deptTrees.add(objectDeptTree);
        }
        List<DeptTree<T>> deptTrees1 = TreeUtilByZsh.buildDeptTree(deptTrees);
        System.out.println(deptTrees1);
        Object o = JSONObject.toJSON(deptTrees1);
        System.out.println(o);

        return o;
    }

    @RequestMapping(value = "/positionList")
    @ResponseBody
    public List<String> createPositionList() {
        List<String> list = OrgUserService.selectPositionList();
        for (String s : list) {
            if ("".equals(s) || s == null) {
                list.remove(s);
                break;
            }
        }
        return list;
    }

    @RequestMapping(value = "/positionListByDept")
    @ResponseBody
    public List<String> createPositionListByDept(String[] departmentIds) {

        String sourceParentId = departmentIds[0];
        String deptString = departmentMapper.selectAllChildDept(Integer.parseInt(sourceParentId));
        System.out.println(deptString);
        String[] split = deptString.split(",");
        List<String> deptList = new ArrayList<>();
        Collections.addAll(deptList, split);
        deptList.remove(0);
        List<String> positionListByDept = OrgUserService.selectPositionListByDept(deptList);
        List<String> result = new ArrayList<>();
        for (String s : positionListByDept) {
            if (!"".equals(s) && s != null && s.length() != 0) {
                result.add(s);
            }

        }
        return result;
    }


    @RequestMapping(value = "/displayFields")
    @ResponseBody
    public List<ReportFormDisplayField> selectDisplayFields() {
        List<ReportFormDisplayField> result = new ArrayList<>();
        ReportFormDisplayField reportFormDisplayField = new ReportFormDisplayField();
        reportFormDisplayField.setName("出勤天数");
        reportFormDisplayField.setAlias("attendance_days");
        result.add(reportFormDisplayField);
        ReportFormDisplayField reportFormDisplayField1 = new ReportFormDisplayField();
        reportFormDisplayField1.setName("旷工天数");
        reportFormDisplayField1.setAlias("absenteeism_days");
        result.add(reportFormDisplayField1);
        ReportFormDisplayField reportFormDisplayField2 = new ReportFormDisplayField();
        reportFormDisplayField2.setName("缺卡次数");
        reportFormDisplayField2.setAlias("work_lack_card_times");
        result.add(reportFormDisplayField2);
        ReportFormDisplayField reportFormDisplayField3 = new ReportFormDisplayField();
        reportFormDisplayField3.setName("迟到次数");
        reportFormDisplayField3.setAlias("late_times");
        result.add(reportFormDisplayField3);
        ReportFormDisplayField reportFormDisplayField4 = new ReportFormDisplayField();
        reportFormDisplayField4.setName("早退次数");
        reportFormDisplayField4.setAlias("leave_early_times");
        result.add(reportFormDisplayField4);
        ReportFormDisplayField reportFormDisplayField5 = new ReportFormDisplayField();
        reportFormDisplayField5.setName("补卡次数");
        reportFormDisplayField5.setAlias("making_up_lack_times");
        result.add(reportFormDisplayField5);
        ReportFormDisplayField reportFormDisplayField6 = new ReportFormDisplayField();
        reportFormDisplayField6.setName("工作时长");
        reportFormDisplayField6.setAlias("attendance_work_time");
        result.add(reportFormDisplayField6);
        ReportFormDisplayField reportFormDisplayField7 = new ReportFormDisplayField();
        reportFormDisplayField7.setName("加班时长");
        reportFormDisplayField7.setAlias("extra_work_time");
        result.add(reportFormDisplayField7);
        ReportFormDisplayField reportFormDisplayField8 = new ReportFormDisplayField();
        reportFormDisplayField8.setName("外出次数");
        reportFormDisplayField8.setAlias("out_times");
        result.add(reportFormDisplayField8);


        return result;
    }

    /**
     * 导出表单功能
     *
     * @param response
     * @param reportFormCondition
     * @throws ParseException
     * @throws ApiException
     * @throws IOException
     */
    @RequestMapping(value = "/exportForm", method = RequestMethod.POST)
    @ResponseBody

    public void exportFormWithHeader(HttpServletResponse response, @RequestBody ReportFormCondition reportFormCondition) throws ParseException, ApiException, IOException {
        String startDate = reportFormCondition.getStartDate();
        String endDate = reportFormCondition.getEndDate();

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateRange d = new DateRange(df.parse(startDate), df.parse(endDate));
        List<DateRange> list = d.splitByMonth();
        list.forEach(x -> System.out.println("start=" + df.format(x.getStart()) + ", end=" + df.format(x.getEnd())));

        String position = reportFormCondition.getPosition();
        String[] departmentIds = reportFormCondition.getDepartmentIds();
        List<String> deptIds = new ArrayList<String>();
        Collections.addAll(deptIds, departmentIds);
        List<OrgUserByPlus> orgUserByPluses = OrgUserService.selectUsersBydeptIdAndPosition(deptIds, position);
        String accessToken = attendanceService.getAccessToken();
        List<ReportFormUserInfo> userInfoList = new ArrayList<>();
        int num = orgUserByPluses.size();
        System.out.println("查询" + num + "人考勤报表");
        for (OrgUserByPlus orgUserByPlus : orgUserByPluses) {
            ReportFormUserInfo reportFormUserInfo = new ReportFormUserInfo();
            reportFormUserInfo.setName(orgUserByPlus.getName());
            reportFormUserInfo.setDuty(orgUserByPlus.getDepartmentid());

            for (int k = 0; k < list.size(); k++) {
                String startDateString = list.get(k).getStart().toString();
                System.out.println("拆分后开始时间" + startDateString);
                System.out.println(list.get(k).getStart());
                String endDateString = list.get(k).getEnd().toString();
                System.out.println("拆分后结束时间" + endDateString);
                DingTalkClient client3 = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/attendance/getcolumnval");
                OapiAttendanceGetcolumnvalRequest req3 = new OapiAttendanceGetcolumnvalRequest();
                req3.setUserid("15729343961277397");
                //                  出勤天数,  旷工天数 上班缺卡次数 下班缺卡次数 迟到 早退
                req3.setColumnIdList("7255015,7255027,7255025,7255026,7255018,7255023");
                req3.setFromDate(list.get(k).getStart());
                req3.setToDate(list.get(k).getEnd());
                OapiAttendanceGetcolumnvalResponse rsp3 = client3.execute(req3, accessToken);
                JSONObject jsonObject = JSON.parseObject(rsp3.getBody());
                JSONObject result = jsonObject.getJSONObject("result");
                JSONArray column_vals = result.getJSONArray("column_vals");
                System.out.println(column_vals);
                for (int i = 0; i < column_vals.size(); i++) {
                    Object o = column_vals.getJSONObject(i).getJSONObject("column_vo").get("id");
                    if ("7255015".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
//                        for (int i1 = 0; i1 < column_vals_details.size(); i1++) {
//                            double value = reportFormUserInfo.getAttendance_days() + column_vals_details.getJSONObject(i1).getDoubleValue("value");
//                            reportFormUserInfo.setAttendance_days(value);
//                        }
                    }
                    if ("7255027".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i2 = 0; i2 < column_vals_details.size(); i2++) {
                            int value = reportFormUserInfo.getAbsenteeism_days() + column_vals_details.getJSONObject(i2).getIntValue("value");
                            reportFormUserInfo.setAbsenteeism_days(value);
                        }
                    }
                    //上班缺卡次数
                    if ("7255025".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i3 = 0; i3 < column_vals_details.size(); i3++) {
                            int value = reportFormUserInfo.getWork_lack_card_times() + column_vals_details.getJSONObject(i3).getIntValue("value");
                            reportFormUserInfo.setWork_lack_card_times(value);
                        }
                    }
                    //下班缺卡次数
                    if ("7255026".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getWork_lack_card_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setWork_lack_card_times(value);
                        }
                    }
                    //迟到次数
                    if ("7255018".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getLate_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setLate_times(value);
                        }
                    }
                    //早退次数
                    if ("7255023".equals(o.toString())) {
                        JSONArray column_vals_details = column_vals.getJSONObject(i).getJSONArray("column_vals");
                        for (int i4 = 0; i4 < column_vals_details.size(); i4++) {
                            int value = reportFormUserInfo.getLeave_early_times() + column_vals_details.getJSONObject(i4).getIntValue("value");
                            reportFormUserInfo.setLeave_early_times(value);
                        }
                    }
                }


            }

            userInfoList.add(reportFormUserInfo);

        }
        String[] includeColumn = reportFormCondition.getIncludeColumn();
        List<String> includeColumnList = Arrays.asList(includeColumn);
        ArrayList headers = new ArrayList();
        headers.add("姓名");
        headers.add("职务");
        for (String s : includeColumnList) {
            headers.add(s);
        }
        List<Map<String, Object>> exportDatas = new ArrayList<Map<String, Object>>();
        for (ReportFormUserInfo reportFormUserInfo : userInfoList) {
            Map<String, Object> map = new HashMap<>();
            map.put("姓名", reportFormUserInfo.getName());
            map.put("职务", reportFormUserInfo.getDuty());
            map.put("出勤天数", reportFormUserInfo.getAttendance_days());
            map.put("旷工天数", reportFormUserInfo.getAbsenteeism_days());
            map.put("缺卡次数", reportFormUserInfo.getWork_lack_card_times());
            map.put("迟到次数", reportFormUserInfo.getLate_times());
            map.put("早退次数", reportFormUserInfo.getLeave_early_times());
            map.put("补卡次数", reportFormUserInfo.getMaking_up_lack_times());
            exportDatas.add(map);
        }
        exportExcel(response, headers, exportDatas, reportFormCondition);
    }


    public void exportExcel(HttpServletResponse resp, List<String> headers, List<Map<String, Object>> exportDatas) throws IOException {
        // 创建一个工作薄
        SXSSFWorkbook wb = new SXSSFWorkbook();
        //创建sheet
        Sheet sh = wb.createSheet("考勤");
        //设置表头字体
        Font headFont = wb.createFont();
        headFont.setFontName("宋体");
        headFont.setColor(HSSFColor.WHITE.index);
        headFont.setFontHeightInPoints((short) 10);// 字体大小
        //headFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);// 加粗

        // 设置表头样式
        CellStyle headStyle = wb.createCellStyle();
        headStyle.setFont(headFont);
        //headStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);// 左右居中
        //headStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_CENTER);// 上下居中
        headStyle.setLocked(true);
        headStyle.setWrapText(true);// 自动换行
        //headStyle.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
        headStyle.setFillForegroundColor(HSSFColor.GREEN.index);

        // 设置普通单元格字体
        Font font = wb.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 9);

        // 设置普通单元格样式
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        //style.setAlignment(HSSFCellStyle.ALIGN_CENTER);// 左右居中
        //style.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);// 上下居中
        style.setWrapText(true);
        style.setLeftBorderColor(HSSFColor.BLACK.index);
        // style.setBorderLeft((short) 1);
        style.setRightBorderColor(HSSFColor.BLACK.index);
        //style.setBorderRight((short) 1);
        //style.setBorderBottom(HSSFCellStyle.BORDER_THIN); // 设置单元格的边框为粗体
        style.setBottomBorderColor(HSSFColor.BLACK.index); // 设置单元格的边框颜色．
        style.setFillForegroundColor(HSSFColor.WHITE.index);// 设置单元格的背景颜色．

        //设置单位格样式为文本
        DataFormat dataFormat = wb.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("@"));

        /**
         * 设置列名
         */
        Row sheetRow = sh.createRow(0);
        Cell cell = null;
        for (int i = 0; i < headers.size(); i++) {
            cell = sheetRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headStyle);
        }

        /**
         * 设置列值
         */
        int rows = 1;
        for (Map<String, Object> data : exportDatas) {
            Row row = sh.createRow(rows++);
            int initCellNo = 0;
            int titleSize = headers.size();
            for (int i = 0; i < titleSize; i++) {
                String key = headers.get(i);
                Object object = data.get(key);
                if (object == null) {
                    row.createCell(initCellNo).setCellValue("");
                } else {
                    row.createCell(initCellNo).setCellValue(String.valueOf(object));
                }
                initCellNo++;
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String fileName = "订单信息" + sdf.format(new Date()) + ".xlsx";
        resp.setContentType("application/octet-stream;charset=utf-8");
        resp.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
        ServletOutputStream out = resp.getOutputStream();
        wb.write(out);
        out.close();
    }


    public void exportExcel(HttpServletResponse response, List<String> headers, List<Map<String, Object>> exportDatas, ReportFormCondition reportFormCondition) throws IOException, ParseException {
        String id = UUID.randomUUID().toString().replace("-", "");
        String fileName = id + "测试.xls";
        StringBuilder stringBuilder = new StringBuilder();
        String realPath = stringBuilder.append("D://upload//").append(fileName).toString();
        File file = new File(realPath);
        // 不需要标题
        if (!file.exists()) {
            file.createNewFile();
        }
        String position = "";
        if (null != reportFormCondition.getPosition()) {
            position = reportFormCondition.getPosition();
        } else {
            position = "人员";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date start = sdf.parse(reportFormCondition.getStartDate());
        Date end = sdf.parse(reportFormCondition.getEndDate());
        SimpleDateFormat format = new SimpleDateFormat("yyyy年MM月dd日");
        String startString = format.format(start);
        String endString = format.format(end);
        String title = startString + "至" + endString + reportFormCondition.getDepartmentIds()[0] + "全体" + position + "考勤报表";
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("测试");
        /** 第三步，设置样式以及字体样式*/
        CellStyle titleStyle = ExportExcel.createTitleCellStyle(workbook);
        CellStyle headerStyle = ExportExcel.createHeadCellStyle(workbook);
        int rowNum = 0;
        // 创建第一页的第一行，索引从0开始
        Row row0 = sheet.createRow(rowNum++);
        row0.setHeight((short) 800);// 设置行高
        Cell c00 = row0.createCell(0);
        c00.setCellValue(title);
        c00.setCellStyle(titleStyle);
        // 合并单元格，参数依次为起始行，结束行，起始列，结束列 （索引0开始）
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.size()));//标题合并单元格操作，6为总列数
        Row row1 = sheet.createRow(rowNum++);
        row1.setHeight((short) 500);
        Cell cell = null;
        for (int i = 0; i < headers.size(); i++) {
            cell = row1.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
        /**
         * 设置列值
         */
        int rows = 2;
        for (Map<String, Object> data : exportDatas) {
            Row row = sheet.createRow(rows++);
            int initCellNo = 0;
            int titleSize = headers.size();
            for (int i = 0; i < titleSize; i++) {
                String key = headers.get(i);
                Object object = data.get(key);
                if (object == null) {
                    row.createCell(initCellNo).setCellValue("");
                } else {
                    row.createCell(initCellNo).setCellValue(String.valueOf(object));
                }
                initCellNo++;
            }
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            workbook.write(fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            fos.close();
        }
        ExportExcel.outputToWeb(realPath, response, workbook);


    }


}







