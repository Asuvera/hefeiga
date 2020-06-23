package cc.mrbird.febs.lxj.entity;

import lombok.Data;

@Data
public class ReportFormCondition {
    private String startDate;
    private String endDate;
    private String position;
    private String[] departmentIds;
    private String departmentName;
    private String[] includeColumn;

}
