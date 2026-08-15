package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_application_skill")
public class ApplicationSkillBindingEntity {
    @TableId(value = "app_key")
    private String appKey;
    @TableField("skill_id")
    private Long skillId;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
}
