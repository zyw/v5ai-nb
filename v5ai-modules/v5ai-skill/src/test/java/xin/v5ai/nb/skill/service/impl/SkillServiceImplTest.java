package xin.v5ai.nb.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.skill.core.SkillPackageParser;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.Skill;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.domain.SkillVersion;
import xin.v5ai.nb.skill.domain.vo.SkillVo;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;
import xin.v5ai.nb.skill.mapper.SkillVersionMapper;

import java.util.ArrayList;
import java.util.List;
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

        doAnswer(inv -> {
            var file = inv.getArgument(0, SkillFile.class);
            file.setId((long) (files.size() + 1));
            files.add(file);
            return 1;
        }).when(fileMapper).insert(any(SkillFile.class));
        when(fileMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(files));

        when(bindingMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(bindings));
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
