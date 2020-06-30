package cc.mrbird.febs.lxj.entity;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReportFormUserInfo {
    //@ExcelProperty("姓名")
    private String name;
    //@ExcelProperty("职位")
    private String duty;

    //@ExcelProperty("出勤天数")
    private BigDecimal attendance_days;//出勤天数
    //@ExcelProperty("旷工天数")
    private int absenteeism_days;//旷工天数
    //@ExcelProperty("缺卡次数")
    private int work_lack_card_times;//缺卡次数
    //@ExcelProperty("迟到次数")
    private int late_times;//迟到次数
    //@ExcelProperty("早退次数")
    private int leave_early_times;//早退次数
    //@ExcelProperty("补卡次数")
    private int making_up_lack_times;//补卡次数
    private BigDecimal attendance_work_time;//工作时长
    private BigDecimal extra_work_time;     //加班时长
    private int out_times;                  //外出次数



}
