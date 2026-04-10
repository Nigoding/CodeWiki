package com.codewiki.summary.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("code_element_summary")
public class SummaryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_name")
    private String projectName;

    @TableField("element_name")
    private String elementName;

    @TableField("element_name_hash")
    private String elementNameHash;

    @TableField("element_summary")
    private String elementSummary;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getElementName() {
        return elementName;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public String getElementNameHash() {
        return elementNameHash;
    }

    public void setElementNameHash(String elementNameHash) {
        this.elementNameHash = elementNameHash;
    }

    public String getElementSummary() {
        return elementSummary;
    }

    public void setElementSummary(String elementSummary) {
        this.elementSummary = elementSummary;
    }
}
