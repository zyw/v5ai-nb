package xin.v5ai.nb.skill.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.vo.SkillVersionVo;

/**
 * <p>
 * Skill 版本 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface SkillVersionMapper extends BaseMapperPlus<SkillVersion, SkillVersionVo> {

    /**
     * 计算某 Skill 的下一个版本号。
     *
     * @param skillId Skill ID
     * @return 下一个版本号（1 起）
     */
    Long nextVersion(@Param("skillId") Long skillId);
}
