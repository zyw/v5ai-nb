package xin.v5ai.nb.skill.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.skill.domain.Skill;
import xin.v5ai.nb.skill.domain.vo.SkillVo;

/**
 * <p>
 * Skill Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface SkillMapper extends BaseMapperPlus<Skill, SkillVo> {
}
