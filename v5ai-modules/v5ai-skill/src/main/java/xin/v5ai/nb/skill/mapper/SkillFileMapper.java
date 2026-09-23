package xin.v5ai.nb.skill.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.domain.vo.SkillFileVo;

/**
 * <p>
 * Skill 文件 Mapper 接口（文件仅供运行时注入，无对外 VO，使用普通 BaseMapper）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface SkillFileMapper extends BaseMapperPlus<SkillFile, SkillFileVo> {
}
