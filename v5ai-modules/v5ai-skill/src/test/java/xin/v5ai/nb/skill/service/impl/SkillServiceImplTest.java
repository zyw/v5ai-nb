package xin.v5ai.nb.skill.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.skill.core.SkillPackageParser;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.Skill;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.vo.SkillFileVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;
import xin.v5ai.nb.skill.mapper.SkillVersionMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceImplTest {

    // 单测环境无 Spring，Lambda 查询包装器解析列名需要 TableInfo 缓存，这里手动初始化。
    static {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Skill.class);
        TableInfoHelper.initTableInfo(assistant, SkillVersion.class);
        TableInfoHelper.initTableInfo(assistant, SkillFile.class);
    }

    private final List<Skill> skills = new ArrayList<>();
    private final List<SkillVersion> versions = new ArrayList<>();
    private final List<SkillFile> files = new ArrayList<>();
    private final List<AgentSkillBinding> bindings = new ArrayList<>();

    private SkillServiceImpl service;

    @BeforeEach
    void setUp() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillVersionMapper versionMapper = mock(SkillVersionMapper.class);
        SkillFileMapper fileMapper = mock(SkillFileMapper.class);
        AgentSkillBindingMapper bindingMapper = mock(AgentSkillBindingMapper.class);

        when(skillMapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> skills.stream().findFirst().orElse(null));
        when(skillMapper.selectById(anyLong())).thenAnswer(inv -> findById(skills, inv.getArgument(0)));
        when(skillMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(skills));
        when(skillMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<SkillVo> page = inv.getArgument(0);
            page.setRecords(new ArrayList<>());
            return page;
        });
        doAnswer(inv -> {
            var skill = inv.getArgument(0, Skill.class);
            skill.setId((long) (skills.size() + 1));
            skills.add(skill);
            return 1;
        }).when(skillMapper).insert(any(Skill.class));

        when(versionMapper.nextVersion(anyLong())).thenAnswer(inv -> versions.stream()
                .filter(v -> v.getSkillId().equals(inv.getArgument(0)))
                .mapToLong(SkillVersion::getVersion).max().orElse(0L) + 1);
        when(versionMapper.selectById(anyLong())).thenAnswer(inv -> findById(versions, inv.getArgument(0)));
        // 在线编辑器的 DRAFT 查找：按 (skillId, DRAFT) 过滤，取最新版本
        when(versionMapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> {
            List<Object> values = orderedWrapperParams(inv.getArgument(0));
            Long skillId = (Long) values.get(0);
            return versions.stream()
                    .filter(v -> v.getSkillId().equals(skillId) && "DRAFT".equals(v.getStatus()))
                    .max(Comparator.comparing(SkillVersion::getVersion))
                    .orElse(null);
        });
        when(versionMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(versions));
        when(versionMapper.selectBatchIds(anyCollection())).thenAnswer(inv -> new ArrayList<>(versions));
        doAnswer(inv -> {
            var version = inv.getArgument(0, SkillVersion.class);
            version.setId((long) (versions.size() + 1));
            versions.add(version);
            return 1;
        }).when(versionMapper).insert(any(SkillVersion.class));
        doAnswer(inv -> {
            SkillVersion update = inv.getArgument(0, SkillVersion.class);
            versions.stream().filter(v -> v.getId().equals(update.getId())).forEach(v -> {
                if (update.getStatus() != null) {
                    v.setStatus(update.getStatus());
                }
                if (update.getPublishedAt() != null) {
                    v.setPublishedAt(update.getPublishedAt());
                }
            });
            return 1;
        }).when(versionMapper).updateById(any(SkillVersion.class));

        doAnswer(inv -> {
            Skill update = inv.getArgument(0, Skill.class);
            skills.stream().filter(s -> s.getId().equals(update.getId())).forEach(s -> {
                if (update.getStatus() != null) {
                    s.setStatus(update.getStatus());
                }
                if (update.getCurrentVersionId() != null) {
                    s.setCurrentVersionId(update.getCurrentVersionId());
                }
            });
            return 1;
        }).when(skillMapper).updateById(any(Skill.class));
        // 下线当前版本：显式清除 current_version_id
        doAnswer(inv -> {
            List<Object> values = orderedWrapperParams(inv.getArgument(1));
            Long skillId = (Long) values.get(0);
            skills.stream().filter(s -> s.getId().equals(skillId))
                    .forEach(s -> s.setCurrentVersionId(null));
            return 1;
        }).when(skillMapper).update(any(), any(Wrapper.class));

        doAnswer(inv -> {
            var file = inv.getArgument(0, SkillFile.class);
            file.setId((long) (files.size() + 1));
            files.add(file);
            return 1;
        }).when(fileMapper).insert(any(SkillFile.class));
        when(fileMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(files));
        // 文件查询：服务内统一按 (versionId, filePath) 过滤
        when(fileMapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> {
            List<Object> values = orderedWrapperParams(inv.getArgument(0));
            Long versionId = (Long) values.get(0);
            String path = (String) values.get(1);
            return files.stream()
                    .filter(f -> f.getVersionId().equals(versionId) && f.getFilePath().equals(path))
                    .findFirst().orElse(null);
        });
        when(fileMapper.selectCount(any(Wrapper.class))).thenAnswer(inv -> {
            List<Object> values = orderedWrapperParams(inv.getArgument(0));
            Long versionId = (Long) values.get(0);
            String path = (String) values.get(1);
            return files.stream()
                    .filter(f -> f.getVersionId().equals(versionId) && f.getFilePath().equals(path))
                    .count();
        });
        doAnswer(inv -> {
            SkillFile update = inv.getArgument(0, SkillFile.class);
            files.stream().filter(f -> f.getId().equals(update.getId()))
                    .forEach(f -> f.setContent(update.getContent()));
            return 1;
        }).when(fileMapper).updateById(any(SkillFile.class));
        doAnswer(inv -> {
            List<Object> values = orderedWrapperParams(inv.getArgument(0));
            Long versionId = (Long) values.get(0);
            String path = (String) values.get(1);
            files.removeIf(f -> f.getVersionId().equals(versionId) && f.getFilePath().equals(path));
            return 1;
        }).when(fileMapper).delete(any(Wrapper.class));

        when(bindingMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(bindings));
        when(bindingMapper.selectCount(any(Wrapper.class))).thenAnswer(inv -> {
            List<Object> values = orderedWrapperParams(inv.getArgument(0));
            Long skillId = (Long) values.get(0);
            return bindings.stream().filter(b -> b.getSkillId().equals(skillId)).count();
        });
        doAnswer(inv -> {
            var binding = inv.getArgument(0, AgentSkillBinding.class);
            bindings.add(binding);
            return 1;
        }).when(bindingMapper).insert(any(AgentSkillBinding.class));
        doAnswer(inv -> {
            bindings.removeIf(b -> b.getAgentKey().equals(inv.getArgument(0, String.class)));
            return 1;
        }).when(bindingMapper).delete(any(Wrapper.class));

        service = new SkillServiceImpl(skillMapper, versionMapper, fileMapper, bindingMapper, new SkillPackageParser());
    }

    @Test
    void uploadCreatesSkillDraftVersionAndFiles() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");

        assertThat(vo.getName()).isEqualTo("weather");
        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(skills).hasSize(1);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(versions.get(0).getVersion()).isEqualTo(1L);
        assertThat(files).extracting(SkillFile::getFilePath).contains("SKILL.md");
    }

    @Test
    void publishMarksVersionPublishedAndPointsSkillToIt() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        var versionId = versions.get(0).getId();

        var published = service.publishVersion(vo.getId(), versionId);

        assertThat(versions.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(versions.get(0).getPublishedAt()).isNotNull();
        assertThat(published.getCurrentVersion()).isEqualTo(1L);
    }

    @Test
    void cannotPublishNonDraftVersion() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        var versionId = versions.get(0).getId();
        service.publishVersion(vo.getId(), versionId);

        assertThatThrownBy(() -> service.publishVersion(vo.getId(), versionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only draft versions can be published");
    }

    @Test
    void rollbackPointsBackToPreviousPublishedVersion() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        var v1 = versions.get(0).getId();
        service.publishVersion(vo.getId(), v1);

        service.uploadSkill(packageBytes("weather"), "v2");
        var v2 = versions.get(1).getId();
        service.publishVersion(vo.getId(), v2);
        assertThat(service.getSkill(vo.getId()).getCurrentVersion()).isEqualTo(2L);

        var rolledBack = service.rollback(vo.getId(), v1);
        assertThat(rolledBack.getCurrentVersion()).isEqualTo(1L);
    }

    @Test
    void optionsOnlyIncludeBindableSkills() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        // DRAFT 版本不可绑定
        assertThat(service.queryOptionList()).isEmpty();

        service.publishVersion(vo.getId(), versions.get(0).getId());

        var options = service.queryOptionList();
        assertThat(options).extracting(option -> option.value()).containsExactly(vo.getId());
        assertThat(options).extracting(option -> option.label()).containsExactly("weather (v1)");
    }

    @Test
    void bindRequiresPublishedVersionAndReplacesBindings() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        assertThatThrownBy(() -> service.bindSkills("demo", List.of(vo.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be bound");

        service.publishVersion(vo.getId(), versions.get(0).getId());
        service.bindSkills("demo", List.of(vo.getId()));
        assertThat(service.getSkillBindings("demo")).containsExactly(vo.getId());
    }

    @Test
    void disableAndPublishOnDisabledSkillRejected() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.disableSkill(vo.getId());

        assertThatThrownBy(() -> service.publishVersion(vo.getId(), versions.get(0).getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skill is disabled");
    }

    @Test
    void disableRejectedWhenSkillHasPublishedVersion() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());

        assertThatThrownBy(() -> service.disableSkill(vo.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be disabled");

        // 下线当前版本后可禁用
        service.offlineVersion(vo.getId(), versions.get(0).getId());
        assertThat(service.disableSkill(vo.getId())).isTrue();
        assertThat(skills.get(0).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void offlineCurrentVersionClearsCurrentPointer() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        assertThat(service.getSkill(vo.getId()).getCurrentVersion()).isEqualTo(1L);

        var result = service.offlineVersion(vo.getId(), versions.get(0).getId());

        assertThat(result.getCurrentVersionId()).isNull();
        assertThat(service.getSkill(vo.getId()).getCurrentVersion()).isNull();
        assertThat(versions.get(0).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void offlineNonCurrentPublishedVersionKeepsCurrent() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        service.uploadSkill(packageBytes("weather"), "v2");
        service.publishVersion(vo.getId(), versions.get(1).getId());

        service.offlineVersion(vo.getId(), versions.get(0).getId());

        assertThat(versions.get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(service.getSkill(vo.getId()).getCurrentVersionId()).isEqualTo(versions.get(1).getId());
    }

    @Test
    void cannotOfflineNonPublishedVersion() {
        var vo = service.createSkillOnline("weather", "查询天气");

        assertThatThrownBy(() -> service.offlineVersion(vo.getId(), versions.get(0).getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only published versions");
    }

    @Test
    void usageCountsAgentBindings() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());

        assertThat(service.getSkillUsage(vo.getId()).count()).isZero();

        service.bindSkills("demo", List.of(vo.getId()));
        assertThat(service.getSkillUsage(vo.getId()).count()).isEqualTo(1);
    }

    @Test
    void createSkillOnlineCreatesSkillDraftAndSkillMdSkeleton() {
        var vo = service.createSkillOnline("weather", "查询天气");

        assertThat(vo.getName()).isEqualTo("weather");
        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(skills).hasSize(1);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(versions.get(0).getVersion()).isEqualTo(1L);
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFilePath()).isEqualTo("SKILL.md");
        assertThat(files.get(0).getContent()).contains("name: weather").contains("description: 查询天气");
    }

    @Test
    void createSkillOnlineRejectsDuplicateName() {
        service.createSkillOnline("weather", "查询天气");

        assertThatThrownBy(() -> service.createSkillOnline("weather", "again"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getSkillEditorReusesExistingDraft() {
        var vo = service.createSkillOnline("weather", "查询天气");

        var editor = service.getSkillEditor(vo.getId());

        assertThat(editor.versionId()).isEqualTo(versions.get(0).getId());
        assertThat(editor.version()).isEqualTo(1L);
        assertThat(editor.files()).extracting(SkillFileVo::filePath).containsExactly("SKILL.md");
        assertThat(versions).hasSize(1); // 复用已有 DRAFT，不新建
    }

    @Test
    void getSkillEditorCreatesDraftSeededFromPublishedVersion() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());

        var editor = service.getSkillEditor(vo.getId());

        assertThat(versions).hasSize(2);
        assertThat(versions.get(1).getStatus()).isEqualTo("DRAFT");
        assertThat(editor.version()).isEqualTo(2L);
        assertThat(editor.files()).extracting(SkillFileVo::filePath).containsOnly("SKILL.md");
    }

    @Test
    void createSkillFileAddsToDraftAndRejectsDuplicate() {
        var vo = service.createSkillOnline("weather", "查询天气");

        service.createSkillFile(vo.getId(), "prompts/guide.md", "# 指南");

        assertThat(files).extracting(SkillFile::getFilePath).contains("prompts/guide.md");
        assertThatThrownBy(() -> service.createSkillFile(vo.getId(), "prompts/guide.md", "dup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateSkillFileChangesContentAndRejectsMissing() {
        var vo = service.createSkillOnline("weather", "查询天气");

        service.updateSkillFile(vo.getId(), "SKILL.md", "---\nname: weather\ndescription: 查询天气\n---\n新内容");

        var updated = files.stream().filter(f -> f.getFilePath().equals("SKILL.md")).findFirst().orElseThrow();
        assertThat(updated.getContent()).contains("新内容");
        assertThatThrownBy(() -> service.updateSkillFile(vo.getId(), "prompts/nope.md", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void deleteSkillFileRemovesFileButProtectsSkillMd() {
        var vo = service.createSkillOnline("weather", "查询天气");
        service.createSkillFile(vo.getId(), "prompts/guide.md", "# 指南");

        assertThatThrownBy(() -> service.deleteSkillFile(vo.getId(), "SKILL.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be deleted");

        service.deleteSkillFile(vo.getId(), "prompts/guide.md");

        assertThat(files).extracting(SkillFile::getFilePath).doesNotContain("prompts/guide.md");
    }

    @Test
    void fileOpsRejectPathTraversalAndMissingArgs() {
        var vo = service.createSkillOnline("weather", "查询天气");

        assertThatThrownBy(() -> service.createSkillFile(vo.getId(), "../evil.md", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
        assertThatThrownBy(() -> service.createSkillFile(vo.getId(), null, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path is required");
    }

    private static <T> T findById(List<T> list, Object id) {
        if (list.isEmpty()) {
            return null;
        }
        if (list.get(0) instanceof Skill skill) {
            return (T) list.stream().filter(s -> ((Skill) s).getId().equals(id)).findFirst().orElse(null);
        }
        if (list.get(0) instanceof SkillVersion version) {
            return (T) list.stream().filter(v -> ((SkillVersion) v).getId().equals(id)).findFirst().orElse(null);
        }
        return null;
    }

    /**
     * 取 Wrapper 中 eq 条件的参数值（按渲染顺序，即 SQL 中 ? 的出现顺序）。
     * paramNameValuePairs 是 HashMap 且参数惰性渲染，需先触发 getSqlSegment()，
     * 再按键 "MPGENVAL<n>" 的序号排序还原顺序。
     */
    private static List<Object> orderedWrapperParams(Object wrapper) {
        var abstractWrapper = (AbstractWrapper<?, ?, ?>) wrapper;
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> paramSeq(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static int paramSeq(String key) {
        int i = key.length();
        while (i > 0 && Character.isDigit(key.charAt(i - 1))) {
            i--;
        }
        return Integer.parseInt(key.substring(i));
    }

    private static byte[] packageBytes(String name) {
        var baos = new java.io.ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(("---\nname: " + name + "\ndescription: " + name + " skill\n---\ncontent").getBytes());
            zip.closeEntry();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return baos.toByteArray();
    }
}
