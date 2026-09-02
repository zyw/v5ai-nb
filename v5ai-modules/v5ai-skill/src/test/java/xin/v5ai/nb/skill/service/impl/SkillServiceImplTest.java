package xin.v5ai.nb.skill.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.skill.core.SkillAiGenerator;
import xin.v5ai.nb.skill.core.SkillPackageParser;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.Skill;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.enums.SkillStatus;
import xin.v5ai.nb.skill.domain.enums.SkillVersionStatus;
import xin.v5ai.nb.skill.domain.vo.SkillFileVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;
import xin.v5ai.nb.skill.mapper.SkillVersionMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceImplTest {

    // 单测环境无 Spring：手动初始化实体 TableInfo（Lambda 查询解析列名），
    // 并注册属性名拷贝 Converter（MapstructUtils 类加载时经 SpringUtil 取 Converter bean）。
    static {
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", new CopyConverter());
        new SpringUtil().setApplicationContext(context);

        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Skill.class);
        TableInfoHelper.initTableInfo(assistant, SkillVersion.class);
        TableInfoHelper.initTableInfo(assistant, SkillFile.class);
        TableInfoHelper.initTableInfo(assistant, AgentSkillBinding.class);
    }

    /** 按属性名拷贝的 Converter：单测中替代 mapstruct-plus 的 Spring 装配（同 v5ai-rag StoreInstanceServiceImplTest）。 */
    private static class CopyConverter extends Converter {
        @Override
        public <S, T> T convert(S source, Class<T> desc) {
            if (source == null) {
                return null;
            }
            try {
                T target = desc.getDeclaredConstructor().newInstance();
                BeanUtil.copyProperties(source, target);
                return target;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private final List<Skill> skills = new ArrayList<>();
    private final List<SkillVersion> versions = new ArrayList<>();
    private final List<SkillFile> files = new ArrayList<>();
    private final List<AgentSkillBinding> bindings = new ArrayList<>();

    private SkillServiceImpl service;
    private SkillAiGenerator skillAiGenerator;

    @BeforeEach
    void setUp() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillVersionMapper versionMapper = mock(SkillVersionMapper.class);
        SkillFileMapper fileMapper = mock(SkillFileMapper.class);
        AgentSkillBindingMapper bindingMapper = mock(AgentSkillBindingMapper.class);
        skillAiGenerator = mock(SkillAiGenerator.class);

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
                    .filter(v -> v.getSkillId().equals(skillId) && SkillVersionStatus.DRAFT.equals(v.getStatus()))
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
        // 编辑器读取文件（getSkillEditor 走 selectVoList 自动映射）
        when(fileMapper.selectVoList(any(Wrapper.class))).thenAnswer(inv -> files.stream().map(f -> {
            var vo = new SkillFileVo();
            vo.setId(f.getId());
            vo.setSkillId(f.getSkillId());
            vo.setVersionId(f.getVersionId());
            vo.setFilePath(f.getFilePath());
            vo.setContent(f.getContent());
            vo.setCreatedAt(f.getCreatedAt());
            return vo;
        }).toList());
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
            Map<String, Object> cols = wrapperColumns(inv.getArgument(0));
            if (cols.containsKey("file_path")) {
                files.removeIf(f -> f.getVersionId().equals(cols.get("version_id"))
                        && f.getFilePath().equals(cols.get("file_path")));
            } else if (cols.containsKey("version_id")) {
                files.removeIf(f -> f.getVersionId().equals(cols.get("version_id")));
            } else if (cols.containsKey("skill_id")) {
                files.removeIf(f -> f.getSkillId().equals(cols.get("skill_id")));
            }
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
            Map<String, Object> cols = wrapperColumns(inv.getArgument(0));
            if (cols.containsKey("agent_key")) {
                bindings.removeIf(b -> b.getAgentKey().equals(cols.get("agent_key")));
            } else if (cols.containsKey("skill_id")) {
                bindings.removeIf(b -> b.getSkillId().equals(cols.get("skill_id")));
            }
            return 1;
        }).when(bindingMapper).delete(any(Wrapper.class));
        // 删除版本/技能：按 skill_id 删除版本、按 id 删除实体
        doAnswer(inv -> {
            Map<String, Object> cols = wrapperColumns(inv.getArgument(0));
            versions.removeIf(v -> v.getSkillId().equals(cols.get("skill_id")));
            return 1;
        }).when(versionMapper).delete(any(Wrapper.class));
        doAnswer(inv -> {
            versions.removeIf(v -> v.getId().equals(inv.getArgument(0)));
            return 1;
        }).when(versionMapper).deleteById(anyLong());
        doAnswer(inv -> {
            skills.removeIf(s -> s.getId().equals(inv.getArgument(0)));
            return 1;
        }).when(skillMapper).deleteById(anyLong());

        service = new SkillServiceImpl(skillMapper, versionMapper, fileMapper, bindingMapper,
                new SkillPackageParser(), skillAiGenerator);
    }

    @Test
    void uploadCreatesSkillDraftVersionAndFiles() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");

        assertThat(vo.getName()).isEqualTo("weather");
        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(skills).hasSize(1);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getStatus()).isEqualTo(SkillVersionStatus.DRAFT);
        assertThat(versions.get(0).getVersion()).isEqualTo(1L);
        assertThat(files).extracting(SkillFile::getFilePath).contains("SKILL.md");
    }

    @Test
    void publishMarksVersionPublishedAndPointsSkillToIt() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        var versionId = versions.get(0).getId();

        var published = service.publishVersion(vo.getId(), versionId);

        assertThat(versions.get(0).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
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
                .hasMessageContaining("only draft or offline versions can be published");
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
                .isInstanceOf(ServiceException.class)
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
        assertThat(skills.get(0).getStatus()).isEqualTo(SkillStatus.DISABLED);
    }

    @Test
    void offlineCurrentVersionClearsCurrentPointer() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        assertThat(service.getSkill(vo.getId()).getCurrentVersion()).isEqualTo(1L);

        var result = service.offlineVersion(vo.getId(), versions.get(0).getId());

        assertThat(result.getCurrentVersionId()).isNull();
        assertThat(service.getSkill(vo.getId()).getCurrentVersion()).isNull();
        assertThat(versions.get(0).getStatus()).isEqualTo(SkillVersionStatus.OFFLINE);
    }

    @Test
    void offlineNonCurrentPublishedVersionKeepsCurrent() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        service.uploadSkill(packageBytes("weather"), "v2");
        service.publishVersion(vo.getId(), versions.get(1).getId());

        service.offlineVersion(vo.getId(), versions.get(0).getId());

        assertThat(versions.get(0).getStatus()).isEqualTo(SkillVersionStatus.OFFLINE);
        assertThat(service.getSkill(vo.getId()).getCurrentVersionId()).isEqualTo(versions.get(1).getId());
    }

    @Test
    void cannotOfflineNonPublishedVersion() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

        assertThatThrownBy(() -> service.offlineVersion(vo.getId(), versions.get(0).getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only published versions");
    }

    @Test
    void disableStillRejectedWhileLegacyPublishedVersionExists() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        service.uploadSkill(packageBytes("weather"), "v2");
        service.publishVersion(vo.getId(), versions.get(1).getId());
        // 下线当前版本 v2：current 指针清空，但 v1 仍是 PUBLISHED
        service.offlineVersion(vo.getId(), versions.get(1).getId());
        assertThat(service.getSkill(vo.getId()).getCurrentVersionId()).isNull();

        assertThatThrownBy(() -> service.disableSkill(vo.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be disabled");
    }

    @Test
    void offlineVersionCanBePublishedAgain() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        service.offlineVersion(vo.getId(), versions.get(0).getId());
        assertThat(versions.get(0).getStatus()).isEqualTo(SkillVersionStatus.OFFLINE);

        var republished = service.publishVersion(vo.getId(), versions.get(0).getId());

        assertThat(versions.get(0).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(republished.getCurrentVersion()).isEqualTo(1L);
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
    void deleteSkillRejectedWhenPublishedAndCascadesOtherwise() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        service.bindSkills("demo", List.of(vo.getId()));

        assertThatThrownBy(() -> service.deleteSkill(vo.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be deleted");

        // 下线当前版本后允许删除，且级联删除版本、文件与绑定
        service.offlineVersion(vo.getId(), versions.get(0).getId());
        assertThat(service.deleteSkill(vo.getId())).isTrue();
        assertThat(skills).isEmpty();
        assertThat(versions).isEmpty();
        assertThat(files).isEmpty();
        assertThat(bindings).isEmpty();
    }

    @Test
    void deleteSkillVersionRejectedWhenPublished() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());

        assertThatThrownBy(() -> service.deleteSkillVersion(vo.getId(), versions.get(0).getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("published versions cannot be deleted");
    }

    @Test
    void deleteDraftVersionRemovesVersionAndItsFiles() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());
        service.uploadSkill(packageBytes("weather"), "v2"); // v2 DRAFT
        Long v2Id = versions.get(1).getId();

        assertThat(service.deleteSkillVersion(vo.getId(), v2Id)).isTrue();

        assertThat(versions).extracting(SkillVersion::getId).doesNotContain(v2Id);
        // v1 的 SKILL.md 仍在，当前版本不受影响
        assertThat(files).extracting(SkillFile::getFilePath).contains("SKILL.md");
        assertThat(service.getSkill(vo.getId()).getCurrentVersion()).isEqualTo(1L);
    }

    @Test
    void createSkillOnlineCreatesSkillDraftAndSkillMdSkeleton() {
        var vo = service.createSkillOnline("weather", "查询天气", "v1 初版");

        assertThat(vo.getName()).isEqualTo("weather");
        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(skills).hasSize(1);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getStatus()).isEqualTo(SkillVersionStatus.DRAFT);
        assertThat(versions.get(0).getVersion()).isEqualTo(1L);
        // 版本描述可选：传入则写入 DRAFT v1（与上传弹窗语义一致）；技能描述只进 skill.description/SKILL.md
        assertThat(versions.get(0).getDescription()).isEqualTo("v1 初版");
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFilePath()).isEqualTo("SKILL.md");
        assertThat(files.get(0).getContent()).contains("name: weather").contains("description: 查询天气");
    }

    @Test
    void createSkillOnlineLeavesVersionDescriptionEmptyWhenOmitted() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

        assertThat(versions.get(0).getDescription()).isNull();
    }

    @Test
    void createSkillOnlineRejectsDuplicateName() {
        service.createSkillOnline("weather", "查询天气", null);

        assertThatThrownBy(() -> service.createSkillOnline("weather", "again", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getSkillEditorReusesExistingDraft() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

        var editor = service.getSkillEditor(vo.getId());

        assertThat(editor.versionId()).isEqualTo(versions.get(0).getId());
        assertThat(editor.version()).isEqualTo(1L);
        assertThat(editor.files()).extracting(SkillFileVo::getFilePath).containsExactly("SKILL.md");
        assertThat(versions).hasSize(1); // 复用已有 DRAFT，不新建
    }

    @Test
    void getSkillEditorCreatesDraftSeededFromPublishedVersion() {
        var vo = service.uploadSkill(packageBytes("weather"), "v1");
        service.publishVersion(vo.getId(), versions.get(0).getId());

        var editor = service.getSkillEditor(vo.getId());

        assertThat(versions).hasSize(2);
        assertThat(versions.get(1).getStatus()).isEqualTo(SkillVersionStatus.DRAFT);
        assertThat(editor.version()).isEqualTo(2L);
        assertThat(editor.files()).extracting(SkillFileVo::getFilePath).containsOnly("SKILL.md");
    }

    @Test
    void createSkillFileAddsToDraftAndRejectsDuplicate() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

        service.createSkillFile(vo.getId(), "prompts/guide.md", "# 指南");

        assertThat(files).extracting(SkillFile::getFilePath).contains("prompts/guide.md");
        assertThatThrownBy(() -> service.createSkillFile(vo.getId(), "prompts/guide.md", "dup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateSkillFileChangesContentAndRejectsMissing() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

        service.updateSkillFile(vo.getId(), "SKILL.md", "---\nname: weather\ndescription: 查询天气\n---\n新内容");

        var updated = files.stream().filter(f -> f.getFilePath().equals("SKILL.md")).findFirst().orElseThrow();
        assertThat(updated.getContent()).contains("新内容");
        assertThatThrownBy(() -> service.updateSkillFile(vo.getId(), "prompts/nope.md", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void savingSkillMdSyncsDescriptionToEntity() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

        service.updateSkillFile(vo.getId(), "SKILL.md",
                "---\nname: weather\ndescription: 新的描述\n---\n正文");

        assertThat(service.getSkill(vo.getId()).getDescription()).isEqualTo("新的描述");
    }

    @Test
    void generateSkillMdDelegatesToAiGenerator() {
        var vo = service.createSkillOnline("weather", "查询天气", null);
        when(skillAiGenerator.generateSkillMd("weather", "查询 5 天天气预报", 1L))
                .thenReturn("---\nname: weather\ndescription: 查询 5 天天气预报\n---\n正文");

        String content = service.generateSkillMd(vo.getId(), 1L, "查询 5 天天气预报");

        assertThat(content).contains("查询 5 天天气预报");
    }

    @Test
    void optimizeSkillFileReadsDraftFileAndPassesContext() {
        var vo = service.createSkillOnline("weather", "查询天气", null);
        service.createSkillFile(vo.getId(), "prompts/guide.md", "# 指南");
        when(skillAiGenerator.optimizeFile(eq("prompts/guide.md"), eq("# 指南"), eq("更简洁"),
                eq("中文"), eq("weather"), eq(1L)))
                .thenReturn("# 更简洁的指南");

        String content = service.optimizeSkillFile(vo.getId(), 1L, "prompts/guide.md", "更简洁", "中文");

        assertThat(content).isEqualTo("# 更简洁的指南");
    }

    @Test
    void optimizeSkillFileRejectsMissingFile() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

        assertThatThrownBy(() -> service.optimizeSkillFile(vo.getId(), 1L, "prompts/nope.md", "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void deleteSkillFileRemovesFileButProtectsSkillMd() {
        var vo = service.createSkillOnline("weather", "查询天气", null);
        service.createSkillFile(vo.getId(), "prompts/guide.md", "# 指南");

        assertThatThrownBy(() -> service.deleteSkillFile(vo.getId(), "SKILL.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be deleted");

        service.deleteSkillFile(vo.getId(), "prompts/guide.md");

        assertThat(files).extracting(SkillFile::getFilePath).doesNotContain("prompts/guide.md");
    }

    @Test
    void fileOpsRejectPathTraversalAndMissingArgs() {
        var vo = service.createSkillOnline("weather", "查询天气", null);

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

    /**
     * 解析 Wrapper 的等值条件：列名 → 参数值。
     * SQL 中占位符为 #{ew.paramNameValuePairs.MPGENVALn}（按渲染顺序编号），
     * 与 orderedWrapperParams 的排序结果按出现顺序配对。
     */
    private static Map<String, Object> wrapperColumns(Object wrapper) {
        var abstractWrapper = (AbstractWrapper<?, ?, ?>) wrapper;
        String sql = abstractWrapper.getSqlSegment();
        List<Object> values = orderedWrapperParams(abstractWrapper);
        Map<String, Object> cols = new HashMap<>();
        var matcher = java.util.regex.Pattern.compile("([a-z_]+)\\s*=\\s*(?:\\?|#\\{[^}]*\\})").matcher(sql);
        int index = 0;
        while (matcher.find() && index < values.size()) {
            cols.put(matcher.group(1), values.get(index++));
        }
        return cols;
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
