package xin.v5ai.nb.skill.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.vo.AgentSkillBindingVo;

/**
 * <p>
 * AgentDTO-Skill 绑定 Mapper 接口（复合主键，无自增列）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface AgentSkillBindingMapper extends BaseMapperPlus<AgentSkillBinding, AgentSkillBindingVo> {
}
