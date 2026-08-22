package xin.v5ai.nb.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;

/**
 * <p>
 * Agent-Skill 绑定 Mapper 接口（复合主键，无自增列）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface AgentSkillBindingMapper extends BaseMapper<AgentSkillBinding> {
}
