package xin.v5ai.nb.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.OptionDTO;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.skill.core.SkillPackageParser;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.Skill;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.bo.SkillBo;
import xin.v5ai.nb.skill.domain.vo.SkillVersionVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;
import xin.v5ai.nb.skill.mapper.SkillVersionMapper;
import xin.v5ai.nb.skill.service.ISkillService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Skill 管理服务实现类。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements ISkillService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String VERSION_DRAFT = "DRAFT";
    private static final String VERSION_PUBLISHED = "PUBLISHED";

    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final SkillFileMapper fileMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final SkillPackageParser packageParser;

    @Override
    public PageResult<SkillVo> queryPageList(SkillBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Skill> lqw = buildQueryWrapper(bo);
        Page<Skill> page = pageQuery.build();
        List<Skill> list = skillMapper.selectList(page, lqw);
        List<SkillVo> vos = list.stream().map(this::toVo).toList();
        return PageResult.build(vos, page.getTotal());
    }

    @Override
    public List<SkillVo> queryList(SkillBo bo) {
        return skillMapper.selectList(buildQueryWrapper(bo)).stream().map(this::toVo).toList();
    }

    @Override
    public List<OptionDTO> queryOptionList() {
        List<Skill> skills = skillMapper.selectList(QueryBuilder.lambda(Skill.class)
                .eq(Skill::getStatus, STATUS_ACTIVE)
                .isNotNull(Skill::getCurrentVersionId)
                .orderByAsc(Skill::getId)
                .build());
        Map<Long, Long> versionById = versionNumbersById(skills.stream()
                .map(Skill::getCurrentVersionId)
                .filter(java.util.Objects::nonNull)
                .toList());
        return skills.stream()
                .filter(skill -> skill.getCurrentVersionId() != null)
                .map(skill -> new OptionDTO(skill.getId(),
                        skill.getName() + " (v" + versionById.getOrDefault(skill.getCurrentVersionId(), 0L) + ")"))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVo uploadSkill(byte[] packageBytes, String description) {
        var skillPackage = packageParser.parse(packageBytes);
        var skill = skillMapper.selectOne(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getName, skillPackage.name()));
        if (skill != null && !STATUS_ACTIVE.equals(skill.getStatus())) {
            throw new IllegalArgumentException("skill is disabled and cannot accept new versions: " + skill.getName());
        }
        if (skill == null) {
            skill = new Skill();
            skill.setName(skillPackage.name());
            skill.setDescription(skillPackage.description());
            skill.setStatus(STATUS_ACTIVE);
            skillMapper.insert(skill);
        }
        long nextVersion = versionMapper.nextVersion(skill.getId());
        var version = new SkillVersion();
        version.setSkillId(skill.getId());
        version.setVersion(nextVersion);
        version.setStatus(VERSION_DRAFT);
        version.setDescription(description);
        versionMapper.insert(version);
        final var savedSkill = skill;
        skillPackage.files().forEach((path, content) -> {
            var file = new SkillFile();
            file.setSkillId(savedSkill.getId());
            file.setVersionId(version.getId());
            file.setFilePath(path);
            file.setContent(content);
            fileMapper.insert(file);
        });
        return toVo(skill);
    }

    @Override
    public SkillVo getSkill(Long id) {
        return toVo(requireSkill(id));
    }

    @Override
    public void disableSkill(Long id) {
        requireSkill(id);
        var update = new Skill();
        update.setId(id);
        update.setStatus(STATUS_DISABLED);
        skillMapper.updateById(update);
    }

    @Override
    public List<SkillVersionVo> listVersions(Long skillId) {
        requireSkill(skillId);
        return versionMapper.selectList(QueryBuilder.lambda(SkillVersion.class)
                        .eq(SkillVersion::getSkillId, skillId)
                        .orderByDesc(SkillVersion::getVersion)
                        .build())
                .stream()
                .map(this::toVersionVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVo publishVersion(Long skillId, Long versionId) {
        var skill = requireActiveSkill(skillId);
        var version = requireVersion(skillId, versionId);
        if (!VERSION_DRAFT.equals(version.getStatus())) {
            throw new IllegalArgumentException("only draft versions can be published: " + versionId);
        }
        var versionUpdate = new SkillVersion();
        versionUpdate.setId(version.getId());
        versionUpdate.setStatus(VERSION_PUBLISHED);
        versionUpdate.setPublishedAt(OffsetDateTime.now());
        versionMapper.updateById(versionUpdate);

        var skillUpdate = new Skill();
        skillUpdate.setId(skill.getId());
        skillUpdate.setCurrentVersionId(version.getId());
        skillMapper.updateById(skillUpdate);
        skill.setCurrentVersionId(version.getId());
        return toVo(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVo rollback(Long skillId, Long versionId) {
        var skill = requireActiveSkill(skillId);
        var version = requireVersion(skillId, versionId);
        if (!VERSION_PUBLISHED.equals(version.getStatus())) {
            throw new IllegalArgumentException("only published versions can be rolled back to: " + versionId);
        }
        var skillUpdate = new Skill();
        skillUpdate.setId(skill.getId());
        skillUpdate.setCurrentVersionId(version.getId());
        skillMapper.updateById(skillUpdate);
        skill.setCurrentVersionId(version.getId());
        return toVo(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindSkills(String agentKey, List<Long> skillIds) {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey is required");
        }
        if (skillIds == null) {
            throw new IllegalArgumentException("skillIds is required");
        }
        for (Long skillId : skillIds) {
            requireBindableSkill(skillId);
        }
        bindingMapper.delete(new LambdaQueryWrapper<AgentSkillBinding>()
                .eq(AgentSkillBinding::getAgentKey, agentKey));
        for (Long skillId : skillIds) {
            var binding = new AgentSkillBinding();
            binding.setAgentKey(agentKey);
            binding.setSkillId(skillId);
            bindingMapper.insert(binding);
        }
    }

    @Override
    public List<Long> getSkillBindings(String agentKey) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentSkillBinding>()
                        .eq(AgentSkillBinding::getAgentKey, agentKey))
                .stream()
                .map(AgentSkillBinding::getSkillId)
                .toList();
    }

    /**
     * 构造 Skill 列表查询条件。
     */
    private LambdaQueryWrapper<Skill> buildQueryWrapper(SkillBo bo) {
        return QueryBuilder.lambda(Skill.class)
                .likeIfText(Skill::getName, bo == null ? null : bo.getName())
                .eqIfText(Skill::getStatus, bo == null ? null : bo.getStatus())
                .orderByAsc(Skill::getId)
                .build();
    }

    private Skill requireSkill(Long id) {
        var skill = id == null ? null : skillMapper.selectById(id);
        if (skill == null) {
            throw new IllegalArgumentException("skill does not exist: " + id);
        }
        return skill;
    }

    private Skill requireActiveSkill(Long id) {
        var skill = requireSkill(id);
        if (!STATUS_ACTIVE.equals(skill.getStatus())) {
            throw new IllegalArgumentException("skill is disabled: " + skill.getName());
        }
        return skill;
    }

    private SkillVersion requireVersion(Long skillId, Long versionId) {
        var version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null || !version.getSkillId().equals(skillId)) {
            throw new IllegalArgumentException("skill version does not exist: " + versionId);
        }
        return version;
    }

    private void requireBindableSkill(Long skillId) {
        var skill = requireActiveSkill(skillId);
        if (skill.getCurrentVersionId() == null) {
            throw new IllegalArgumentException("skill has no published version and cannot be bound: " + skill.getName());
        }
    }

    /**
     * 实体转 VO，并填充当前生效版本号。
     */
    private SkillVo toVo(Skill skill) {
        var vo = new SkillVo();
        vo.setId(skill.getId());
        vo.setName(skill.getName());
        vo.setDescription(skill.getDescription());
        vo.setStatus(skill.getStatus());
        vo.setCurrentVersionId(skill.getCurrentVersionId());
        vo.setCreatedAt(skill.getCreatedAt());
        vo.setUpdatedAt(skill.getUpdatedAt());
        if (skill.getCurrentVersionId() != null) {
            var current = versionMapper.selectById(skill.getCurrentVersionId());
            if (current != null) {
                vo.setCurrentVersion(current.getVersion());
            }
        }
        return vo;
    }

    private SkillVersionVo toVersionVo(SkillVersion version) {
        var vo = new SkillVersionVo();
        vo.setId(version.getId());
        vo.setSkillId(version.getSkillId());
        vo.setVersion(version.getVersion());
        vo.setStatus(version.getStatus());
        vo.setDescription(version.getDescription());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setCreatedAt(version.getCreatedAt());
        vo.setUpdatedAt(version.getUpdatedAt());
        return vo;
    }

    /**
     * 批量查询版本号映射（versionId → version）。
     */
    private Map<Long, Long> versionNumbersById(List<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Map.of();
        }
        return versionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(SkillVersion::getId, SkillVersion::getVersion));
    }
}
