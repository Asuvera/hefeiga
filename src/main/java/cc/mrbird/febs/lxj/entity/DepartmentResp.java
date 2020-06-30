package cc.mrbird.febs.lxj.entity;

import lombok.Data;

import java.util.List;

@Data
public class DepartmentResp {
    /**
     * 部门钉钉id
     */
    private String sourceId;
    /**
     * 部门钉钉父id
     */
    private String sourceParentId;


 
    private String name;
 
    private boolean flag=false;
 
    private List<DepartmentResp> childrenList;
 

}
