package xin.v5ai.nb.skill;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 管理服务：上传解析、版本发布、回滚、绑定、禁用与运行时文件解析。
 */
@Service
public class SkillManagementService {
    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final SkillFileRepository fileRepository;
    private final ApplicationSkillBindingRepository bindingRepository;
    private final SkillPackageParser packageParser;

    public SkillManagementService(
            SkillRepository skillRepository,
            SkillVersionRepository versionRepository,
            SkillFileRepository fileRepository,
            ApplicationSkillBindingRepository bindingRepository,
            SkillPackageParser packageParser
    ) {
        this.skillRepository = skillRepository;
        this.versionRepository = versionRepository;
        this.fileRepository = fileRepository;
        this.bindingRepository = bindingRepository;
        this.packageParser = packageParser;
    }

    // ---------- 上传与 Skill 管理 ----------

    /**
     * 上传 Skill 包：解析校验后，创建/复用 Skill 并生成一个 DRAFT 版本。
     */
    public Skill uploadSkill(byte[] packageBytes, String description) {
        var skillPackage = packageParser.parse(packageBytes);
        var skill = skillRepository.findByName(skillPackage.name());
        if (skill != null && !skill.isActive()) {
            throw new IllegalArgumentException("skill is disabled and cannot accept new versions: " + skill.name());
        }
        if (skill == null) {
            skill = skillRepository.save(new Skill(null, skillPackage.name(), skillPackage.description(),
                    SkillStatus.ACTIVE, null, null, Instant.now(), Instant.now()));
        }
        long nextVersion = versionRepository.nextVersion(skill.id());
        var version = versionRepository.save(new SkillVersion(
                null, skill.id(), nextVersion, SkillVersionStatus.DRAFT,
                description, null, Instant.now()));
        fileRepository.saveFiles(skill.id(), version.id(), skillPackage.files());
        return skill;
    }

    public List<Skill> listSkills() {
        return skillRepository.list();
    }

    public Skill getSkill(Long id) {
        return requireSkill(id);
    }

    public void disableSkill(Long id) {
        requireSkill(id);
        skillRepository.disable(id);
    }

    // ---------- 版本 ----------

    public List<SkillVersion> listVersions(Long skillId) {
        requireSkill(skillId);
        return versionRepository.findBySkillId(skillId);
    }

    public SkillVersion getVersion(Long skillId, Long versionId) {
        requireSkill(skillId);
        var version = versionRepository.findById(versionId);
        if (version == null || !version.skillId().equals(skillId)) {
            throw new IllegalArgumentException("skill version does not exist: " + versionId);
        }
        return version;
    }

    /**
     * 发布 DRAFT 版本：标记 PUBLISHED 并把 Skill 的当前版本指针指向它。
     */
    public Skill publishVersion(Long skillId, Long versionId) {
        var skill = requireActiveSkill(skillId);
        var version = getVersion(skillId, versionId);
        if (version.status() != SkillVersionStatus.DRAFT) {
            throw new IllegalArgumentException("only draft versions can be published: " + versionId);
        }
        versionRepository.updateStatus(version.id(), SkillVersionStatus.PUBLISHED, Instant.now());
        return skillRepository.update(skill.withCurrentVersion(version.id(), version.version()));
    }

    /**
     * 回滚：把 Skill 的当前版本指针指向某个已发布的旧版本。
     */
    public Skill rollback(Long skillId, Long versionId) {
        var skill = requireActiveSkill(skillId);
        var version = getVersion(skillId, versionId);
        if (version.status() != SkillVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("only published versions can be rolled back to: " + versionId);
        }
        return skillRepository.update(skill.withCurrentVersion(version.id(), version.version()));
    }

    // ---------- Application 绑定 ----------

    public void bindSkills(String appKey, List<Long> skillIds) {
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("appKey is required");
        }
        if (skillIds == null) {
            throw new IllegalArgumentException("skillIds is required");
        }
        for (Long skillId : skillIds) {
            requireBindableSkill(skillId);
        }
        bindingRepository.save(appKey, skillIds);
    }

    public List<Long> getSkillBindings(String appKey) {
        return bindingRepository.findByAppKey(appKey);
    }

    // ---------- 运行时解析 ----------

    /**
     * 返回 Skill 当前发布版本的完整文件（供运行时 Workspace 注入）。
     */
    public Map<String, String> publishedSkillFiles(Long skillId) {
        var skill = requireActiveSkill(skillId);
        if (skill.currentVersionId() == null) {
            throw new IllegalArgumentException("skill has no published version: " + skill.name());
        }
        Map<String, String> files = new LinkedHashMap<>();
        for (SkillFile file : fileRepository.findByVersionId(skill.currentVersionId())) {
            files.put(file.path(), file.content());
        }
        return files;
    }

    public SkillVersion getCurrentPublishedVersion(Long skillId) {
        var skill = requireActiveSkill(skillId);
        if (skill.currentVersionId() == null) {
            return null;
        }
        return versionRepository.findById(skill.currentVersionId());
    }

    // ---------- 内部 ----------

    private Skill requireSkill(Long id) {
        var skill = id == null ? null : skillRepository.findById(id);
        if (skill == null) {
            throw new IllegalArgumentException("skill does not exist: " + id);
        }
        return skill;
    }

    private Skill requireActiveSkill(Long id) {
        var skill = requireSkill(id);
        if (!skill.isActive()) {
            throw new IllegalArgumentException("skill is disabled: " + skill.name());
        }
        return skill;
    }

    private void requireBindableSkill(Long skillId) {
        var skill = requireActiveSkill(skillId);
        if (skill.currentVersionId() == null) {
            throw new IllegalArgumentException("skill has no published version and cannot be bound: " + skill.name());
        }
    }
}
