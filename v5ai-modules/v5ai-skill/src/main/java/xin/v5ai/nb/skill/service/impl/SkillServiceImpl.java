package xin.v5ai.nb.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.skill.core.SkillAiGenerator;
import xin.v5ai.nb.skill.core.SkillFrontmatterParser;
import xin.v5ai.nb.skill.core.SkillPackageParser;
import xin.v5ai.nb.skill.core.SkillVersionLifecycle;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.Skill;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.bo.SkillBo;
import xin.v5ai.nb.skill.domain.enums.SkillStatus;
import xin.v5ai.nb.skill.domain.enums.SkillVersionStatus;
import xin.v5ai.nb.skill.domain.vo.SkillEditorVo;
import xin.v5ai.nb.skill.domain.vo.SkillUsageVo;
import xin.v5ai.nb.skill.domain.vo.SkillVersionVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;
import xin.v5ai.nb.skill.mapper.SkillVersionMapper;
import xin.v5ai.nb.skill.service.ISkillService;

import java.nio.charset.StandardCharsets;
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

    private static final String SKILL_MD = "SKILL.md";

    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final SkillFileMapper fileMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final SkillPackageParser packageParser;
    private final SkillAiGenerator skillAiGenerator;

    @Override
    public PageResult<SkillVo> queryPageList(SkillBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Skill> lqw = buildQueryWrapper(bo);
        Page<Skill> page = pageQuery.build();
        List<Skill> list = skillMapper.selectList(page, lqw);
        List<SkillVo> vos = toVos(list);
        return PageResult.build(vos, page.getTotal());
    }

    @Override
    public List<SkillVo> queryList(SkillBo bo) {
        return toVos(skillMapper.selectList(buildQueryWrapper(bo)));
    }

    @Override
    public List<OptionDTO> queryOptionList() {
        List<Skill> skills = skillMapper.selectList(QueryBuilder.lambda(Skill.class)
                .eq(Skill::getStatus, SkillStatus.ACTIVE)
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
        if (skill != null
                && skill.getStatus() != SkillStatus.ACTIVE) {
            throw new IllegalArgumentException("skill is disabled and cannot accept new versions: " + skill.getName());
        }
        if (skill == null) {
            skill = new Skill();
            skill.setName(skillPackage.name());
            skill.setDescription(skillPackage.description());
            skill.setStatus(SkillStatus.ACTIVE);
            skillMapper.insert(skill);
        }
        long nextVersion = versionMapper.nextVersion(skill.getId());
        var version = new SkillVersion();
        version.setSkillId(skill.getId());
        version.setVersion(nextVersion);
        version.setStatus(SkillVersionStatus.DRAFT);
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
    @Transactional(rollbackFor = Exception.class)
    public SkillVo createSkillOnline(String name, String description, String versionDescription) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("skill description is required");
        }
        name = name.trim();
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("skill name must not contain path separators: " + name);
        }
        var existing = skillMapper.selectOne(new LambdaQueryWrapper<Skill>().eq(Skill::getName, name));
        if (existing != null) {
            throw new IllegalArgumentException("skill name already exists: " + name);
        }
        var skill = new Skill();
        skill.setName(name);
        skill.setDescription(description.trim());
        skill.setStatus(SkillStatus.ACTIVE);
        skillMapper.insert(skill);
        var version = newDraftVersion(skill.getId(), trimToNull(versionDescription));
        var file = new SkillFile();
        file.setSkillId(skill.getId());
        file.setVersionId(version.getId());
        file.setFilePath(SKILL_MD);
        file.setContent(skillMdSkeleton(name, description.trim()));
        fileMapper.insert(file);
        return toVo(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillEditorVo getSkillEditor(Long skillId) {
        var skill = requireSkill(skillId);
        var version = ensureDraftVersion(skill);
        var files = fileMapper.selectVoList(QueryBuilder.lambda(SkillFile.class)
                        .eq(SkillFile::getVersionId, version.getId())
                        .orderByAsc(SkillFile::getFilePath)
                        .build());
        return new SkillEditorVo(toVo(skill), version.getId(), version.getVersion(), files);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSkillFile(Long skillId, String filePath, String content) {
        var skill = requireActiveSkill(skillId);
        var version = ensureDraftVersion(skill);
        var path = normalizeAndValidatePath(filePath);
        requireContentSize(path, content);
        if (fileMapper.selectCount(new LambdaQueryWrapper<SkillFile>()
                .eq(SkillFile::getVersionId, version.getId())
                .eq(SkillFile::getFilePath, path)) > 0) {
            throw new IllegalArgumentException("skill file already exists: " + path);
        }
        var file = new SkillFile();
        file.setSkillId(skillId);
        file.setVersionId(version.getId());
        file.setFilePath(path);
        file.setContent(content);
        fileMapper.insert(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkillFile(Long skillId, String filePath, String content) {
        var skill = requireActiveSkill(skillId);
        var version = ensureDraftVersion(skill);
        var path = normalizeAndValidatePath(filePath);
        requireContentSize(path, content);
        var file = requireDraftFile(version, path);
        file.setContent(content);
        fileMapper.updateById(file);
        if (SKILL_MD.equals(path)) {
            syncDescriptionFromSkillMd(skill, content);
        }
    }

    /**
     * 保存 SKILL.md 时，把 frontmatter 的 description 同步到 Skill 实体（name 保持不变）。
     */
    private void syncDescriptionFromSkillMd(Skill skill, String content) {
        String description = SkillFrontmatterParser.parseMarkdown(content).metadata().get("description");
        if (description == null || description.isBlank() || description.trim().equals(skill.getDescription())) {
            return;
        }
        var update = new Skill();
        update.setId(skill.getId());
        update.setDescription(description.trim());
        skillMapper.updateById(update);
        skill.setDescription(description.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSkillFile(Long skillId, String filePath) {
        var skill = requireActiveSkill(skillId);
        var version = ensureDraftVersion(skill);
        var path = normalizeAndValidatePath(filePath);
        if (SKILL_MD.equals(path)) {
            throw new IllegalArgumentException("SKILL.md is the skill entry and cannot be deleted");
        }
        requireDraftFile(version, path);
        fileMapper.delete(new LambdaQueryWrapper<SkillFile>()
                .eq(SkillFile::getVersionId, version.getId())
                .eq(SkillFile::getFilePath, path));
    }

    @Override
    public String generateSkillMd(Long skillId, Long modelId, String requirement) {
        var skill = requireSkill(skillId);
        return skillAiGenerator.generateSkillMd(skill.getName(), requirement, modelId);
    }

    @Override
    public String optimizeSkillFile(Long skillId, Long modelId, String filePath,
                                    String requirement, String direction) {
        var skill = requireSkill(skillId);
        var version = ensureDraftVersion(skill);
        var path = normalizeAndValidatePath(filePath);
        var file = requireDraftFile(version, path);
        return skillAiGenerator.optimizeFile(path, file.getContent(), requirement, direction,
                skill.getName(), modelId);
    }

    @Override
    public SkillVo getSkill(Long id) {
        return toVo(requireSkill(id));
    }

    @Override
    public boolean disableSkill(Long id) {
        var skill = requireSkill(id);
        if (!SkillVersionLifecycle.canDisableSkill(versionsOf(skill))) {
            throw new IllegalArgumentException(
                    "skill has a published version and cannot be disabled, take versions offline first: "
                            + skill.getName());
        }
        var update = new Skill();
        update.setId(id);
        update.setStatus(SkillStatus.DISABLED);
        return skillMapper.updateById(update) > 0;
    }

    @Override
    public boolean enableSkill(Long id) {
        requireSkill(id);
        var update = new Skill();
        update.setId(id);
        update.setStatus(SkillStatus.ACTIVE);
        return skillMapper.updateById(update) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSkill(Long id) {
        var skill = requireSkill(id);
        if (!SkillVersionLifecycle.canDeleteSkill(versionsOf(skill))) {
            throw new IllegalArgumentException(
                    "skill has a published version and cannot be deleted, take versions offline first: "
                            + skill.getName());
        }
        fileMapper.delete(new LambdaQueryWrapper<SkillFile>().eq(SkillFile::getSkillId, id));
        versionMapper.delete(new LambdaQueryWrapper<SkillVersion>().eq(SkillVersion::getSkillId, id));
        bindingMapper.delete(new LambdaQueryWrapper<AgentSkillBinding>().eq(AgentSkillBinding::getSkillId, id));
        return skillMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSkillVersion(Long skillId, Long versionId) {
        requireSkill(skillId);
        var version = requireVersion(skillId, versionId);
        if (!SkillVersionLifecycle.canDeleteVersion(version.getStatus())) {
            throw new IllegalArgumentException("published versions cannot be deleted: " + versionId);
        }
        fileMapper.delete(new LambdaQueryWrapper<SkillFile>().eq(SkillFile::getVersionId, versionId));
        return versionMapper.deleteById(versionId) > 0;
    }

    @Override
    public List<SkillVersionVo> listVersions(Long skillId) {
        requireSkill(skillId);
        return versionMapper.selectVoList(QueryBuilder.lambda(SkillVersion.class)
                        .eq(SkillVersion::getSkillId, skillId)
                        .orderByDesc(SkillVersion::getVersion)
                        .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVo publishVersion(Long skillId, Long versionId) {
        var skill = requireActiveSkill(skillId);
        var version = requireVersion(skillId, versionId);
        if (!SkillVersionLifecycle.canPublish(version.getStatus())) {
            throw new IllegalArgumentException("only draft or offline versions can be published: " + versionId);
        }
        var versionUpdate = new SkillVersion();
        versionUpdate.setId(version.getId());
        versionUpdate.setStatus(SkillVersionStatus.PUBLISHED);
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
    public SkillVo offlineVersion(Long skillId, Long versionId) {
        var skill = requireSkill(skillId);
        var version = requireVersion(skillId, versionId);
        if (!SkillVersionLifecycle.canOffline(version.getStatus())) {
            throw new IllegalArgumentException("only published versions can be taken offline: " + versionId);
        }
        var versionUpdate = new SkillVersion();
        versionUpdate.setId(version.getId());
        versionUpdate.setStatus(SkillVersionStatus.OFFLINE);
        versionMapper.updateById(versionUpdate);
        if (version.getId().equals(skill.getCurrentVersionId())) {
            // updateById 忽略 null 字段，清除当前版本指针需显式 set
            skillMapper.update(null, new LambdaUpdateWrapper<Skill>()
                    .eq(Skill::getId, skill.getId())
                    .set(Skill::getCurrentVersionId, null));
            skill.setCurrentVersionId(null);
        }
        return toVo(skill);
    }

    @Override
    public SkillUsageVo getSkillUsage(Long skillId) {
        requireSkill(skillId);
        long count = bindingMapper.selectCount(new LambdaQueryWrapper<AgentSkillBinding>()
                .eq(AgentSkillBinding::getSkillId, skillId));
        return new SkillUsageVo(count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVo rollback(Long skillId, Long versionId) {
        var skill = requireActiveSkill(skillId);
        var version = requireVersion(skillId, versionId);
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
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
    public boolean bindSkills(String agentKey, List<Long> skillIds) {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey is required");
        }
        if (skillIds == null) {
            throw new IllegalArgumentException("skillIds is required");
        }
        for (Long skillId : skillIds) {
            requireBindableSkill(skillId);
        }
        try {
            int delResult = bindingMapper.delete(new LambdaQueryWrapper<AgentSkillBinding>()
                    .eq(AgentSkillBinding::getAgentKey, agentKey));
            log.info("AgentSkillBinding表删除行数：{}", delResult);
            for (Long skillId : skillIds) {
                var binding = new AgentSkillBinding();
                binding.setAgentKey(agentKey);
                binding.setSkillId(skillId);
                bindingMapper.insert(binding);
            }
            return true;
        } catch (Exception e) {
            log.error("Agent绑定失败：{}",e.getMessage(),e);
            throw new ServiceException("Agent绑定失败: " + e.getMessage());
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
            throw new ServiceException("skill does not exist: " + id);
        }
        return skill;
    }

    private Skill requireActiveSkill(Long id) {
        var skill = requireSkill(id);
        if (skill.getStatus() != SkillStatus.ACTIVE) {
            throw new ServiceException("skill is disabled: " + skill.getName());
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

    /**
     * 该 Skill 的全部版本（供生命周期规则裁决与编辑器草稿选择）。
     */
    private List<SkillVersion> versionsOf(Skill skill) {
        return versionMapper.selectList(new LambdaQueryWrapper<SkillVersion>()
                .eq(SkillVersion::getSkillId, skill.getId()));
    }

    /**
     * 获取（或新建）该 Skill 的可编辑 DRAFT 版本：最新版本号的 DRAFT 直接复用（规则见
     * {@link SkillVersionLifecycle}）；没有则新建一个 DRAFT，并从当前生效版本拷贝文件作为编辑起点。
     */
    private SkillVersion ensureDraftVersion(Skill skill) {
        List<SkillVersion> versions = versionsOf(skill);
        var draft = SkillVersionLifecycle.latestDraft(versions);
        if (draft.isPresent()) {
            return draft.get();
        }
        var version = newDraftVersion(skill.getId(), null);
        if (skill.getCurrentVersionId() != null) {
            versions.stream()
                    .filter(v -> v.getId().equals(skill.getCurrentVersionId()))
                    .findFirst()
                    .ifPresent(current -> fileMapper.selectList(new LambdaQueryWrapper<SkillFile>()
                                    .eq(SkillFile::getVersionId, current.getId()))
                            .forEach(source -> {
                                var file = new SkillFile();
                                file.setSkillId(source.getSkillId());
                                file.setVersionId(version.getId());
                                file.setFilePath(source.getFilePath());
                                file.setContent(source.getContent());
                                fileMapper.insert(file);
                            }));
        }
        return version;
    }

    private SkillVersion newDraftVersion(Long skillId, String description) {
        var version = new SkillVersion();
        version.setSkillId(skillId);
        version.setVersion(versionMapper.nextVersion(skillId));
        version.setStatus(SkillVersionStatus.DRAFT);
        version.setDescription(description);
        versionMapper.insert(version);
        return version;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SkillFile requireDraftFile(SkillVersion version, String path) {
        var file = fileMapper.selectOne(new LambdaQueryWrapper<SkillFile>()
                .eq(SkillFile::getVersionId, version.getId())
                .eq(SkillFile::getFilePath, path));
        if (file == null) {
            throw new IllegalArgumentException("skill file does not exist: " + path);
        }
        return file;
    }

    private static String normalizeAndValidatePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("skill file path is required");
        }
        String path = SkillPackageParser.normalizePath(filePath);
        SkillPackageParser.validatePath(path);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("skill file path is required");
        }
        return path;
    }

    private static void requireContentSize(String path, String content) {
        if (content == null) {
            throw new IllegalArgumentException("skill file content is required: " + path);
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > SkillPackageParser.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("skill file too large (max "
                    + (SkillPackageParser.MAX_FILE_BYTES / 1024) + "KB): " + path);
        }
    }

    /**
     * 在线新建 Skill 的 SKILL.md 骨架（含合法 frontmatter：name/description 必填非空）。
     */
    private static String skillMdSkeleton(String name, String description) {
        return "---\n"
                + "name: " + toSingleLine(name) + "\n"
                + "description: " + toSingleLine(description) + "\n"
                + "---\n"
                + "\n"
                + "（在此编写该技能的作用与使用说明；可补充 prompts/ 与 resources/ 目录下的辅助文件。）\n";
    }

    private static String toSingleLine(String text) {
        return text.replace("\r", " ").replace("\n", " ");
    }

    private void requireBindableSkill(Long skillId) {
        var skill = requireActiveSkill(skillId);
        if (skill.getCurrentVersionId() == null) {
            throw new IllegalArgumentException("skill has no published version and cannot be bound: " + skill.getName());
        }
    }

    /**
     * 批量实体转 VO：一次 selectBatchIds 取全部当前版本号（避免分页 N+1）。
     */
    private List<SkillVo> toVos(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> versionNumberById = versionNumbersById(skills.stream()
                .map(Skill::getCurrentVersionId)
                .filter(java.util.Objects::nonNull)
                .toList());
        return skills.stream().map(skill -> toVo(skill, versionNumberById)).toList();
    }

    /**
     * 单个实体转 VO（低频单查路径，版本号按需单查）。
     */
    private SkillVo toVo(Skill skill) {
        return toVo(skill, null);
    }

    private SkillVo toVo(Skill skill, Map<Long, Long> versionNumberById) {
        var vo = MapstructUtils.convert(skill, SkillVo.class);
        assert vo != null;
        vo.setStatus(skill.getStatus().name());
        if (skill.getCurrentVersionId() != null) {
            Long versionNumber = versionNumberById == null
                    ? fetchVersionNumber(skill.getCurrentVersionId())
                    : versionNumberById.get(skill.getCurrentVersionId());
            vo.setCurrentVersion(versionNumber);
        }
        return vo;
    }

    private Long fetchVersionNumber(Long versionId) {
        var current = versionMapper.selectById(versionId);
        return current == null ? null : current.getVersion();
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
