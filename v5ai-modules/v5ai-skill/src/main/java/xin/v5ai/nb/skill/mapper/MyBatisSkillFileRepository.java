package xin.v5ai.nb.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.dto.SkillFileDTO;
import xin.v5ai.nb.common.agentscope.core.service.SkillFileService;
import xin.v5ai.nb.skill.domain.SkillFile;

import java.util.List;

/**
 * {@link SkillFileService} 的 MyBatis-Plus 实现（运行时按版本读取 Skill 文件）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
public class MyBatisSkillFileRepository implements SkillFileService {

    private final SkillFileMapper fileMapper;

    public MyBatisSkillFileRepository(SkillFileMapper fileMapper) {
        this.fileMapper = fileMapper;
    }

    @Override
    public List<SkillFileDTO> selectList(Long versionId) {
        return fileMapper.selectList(new LambdaQueryWrapper<SkillFile>()
                        .eq(SkillFile::getVersionId, versionId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private SkillFileDTO toDomain(SkillFile entity) {
        var dto = new SkillFileDTO();
        dto.setId(entity.getId());
        dto.setSkillId(entity.getSkillId());
        dto.setVersionId(entity.getVersionId());
        dto.setFilePath(entity.getFilePath());
        dto.setContent(entity.getContent());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
