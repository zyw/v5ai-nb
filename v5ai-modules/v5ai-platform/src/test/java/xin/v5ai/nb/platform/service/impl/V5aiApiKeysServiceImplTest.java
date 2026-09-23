package xin.v5ai.nb.platform.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.StaticApplicationContext;
import xin.v5ai.nb.agent.api.AgentCatalogService;
import xin.v5ai.nb.common.core.domain.model.Tuple2;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.encrypt.apikeys.AgentApiKeysVerifier;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.platform.domain.V5aiApiKeys;
import xin.v5ai.nb.platform.domain.V5aiApiKeysAgent;
import xin.v5ai.nb.platform.mapper.V5aiApiKeysAgentMapper;
import xin.v5ai.nb.platform.mapper.V5aiApiKeysMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link V5aiApiKeysServiceImpl} 的 Mockito 单测。
 *
 * <p>覆盖三条必须守住的契约：</p>
 * <ol>
 *   <li>只能绑定已发布（PUBLISHED）的 Agent——服务端权威校验，不信任前端列表；</li>
 *   <li>写操作只能作用于当前用户自己的 Key（越权改删必须 404 语义拒绝）；</li>
 *   <li>运行期鉴权：明文 Key 定位行 + 密文校验 + 返回可访问 Agent 并刷新最新使用时间。</li>
 * </ol>
 *
 * @author ZYW
 * @since 2026-09-25
 */
class V5aiApiKeysServiceImplTest {

    /** 明文 Key：v5ai- + 32 位大小写字母与数字（不含跟踪信息）。 */
    private static final String PLAINTEXT_KEY = "v5ai-abcdefghijklmnopqrstuvwxyz012345";
    /** tracking id 是与明文无关的独立 UUID，仅展示/审计用。 */
    private static final String TRACKING_ID = "2462f9cb-a292-44da-b33d-dcc9a9737ad6";
    /** 替身校验器对约定明文返回的 SHA-256 摘要（真实实现是 DigestUtil.sha256Hex）。 */
    private static final String KEY_HASH = "stub-key-hash";

    private V5aiApiKeysMapper baseMapper;
    private V5aiApiKeysAgentMapper bindingMapper;
    private AgentCatalogService agentCatalogService;
    private V5aiApiKeysServiceImpl service;

    @BeforeEach
    void setUp() {
        // CipherUtils 的静态字段在类加载时通过 SpringUtils 取 Bean，
        // 因此在第一次调用 CipherUtils 之前注册替身：加密器占位 + 可控的 ApiKey 校验器。
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("credentialCipher", mock(CredentialCipher.class));
        context.getBeanFactory().registerSingleton("agentApiKeyVerifier", new StubApiKeyVerifier());
        context.refresh();
        new SpringUtil().setApplicationContext(context);

        baseMapper = mock(V5aiApiKeysMapper.class);
        bindingMapper = mock(V5aiApiKeysAgentMapper.class);
        agentCatalogService = mock(AgentCatalogService.class);
        service = new V5aiApiKeysServiceImpl(baseMapper, bindingMapper, agentCatalogService);
    }

    @Test
    void insertApiKeyRejectsNotPublishedAgent() {
        when(agentCatalogService.filterPublished(List.of("a1", "a2"))).thenReturn(Set.of("a1"));

        assertThatThrownBy(() -> service.insertApiKey(7L, "生产环境", List.of("a1", "a2")))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("已发布");

        verify(baseMapper, never()).insert(any(V5aiApiKeys.class));
        verifyNoInteractions(bindingMapper);
    }

    @Test
    void insertApiKeyRejectsEmptyAgentSelection() {
        assertThatThrownBy(() -> service.insertApiKey(7L, "空绑定", List.of("  ")))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("至少选择一个");
    }

    @Test
    void insertApiKeyPersistsTrackingIdAndBindings() {
        when(agentCatalogService.filterPublished(List.of("a1", "a2"))).thenReturn(Set.of("a1", "a2"));
        // 模拟数据库回填自增主键
        when(baseMapper.insert(any(V5aiApiKeys.class))).thenAnswer(invocation -> {
            invocation.<V5aiApiKeys>getArgument(0).setId(11L);
            return 1;
        });

        var created = service.insertApiKey(7L, "  生产环境  ", List.of("a2", "a1", "a1"));

        assertThat(created.getApiKey()).isEqualTo(PLAINTEXT_KEY);
        assertThat(created.getTrackingId())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        // 名称去空格、agentKey 去重排序后落库
        assertThat(created.getAgentKeys()).containsExactly("a1", "a2");

        var captured = ArgumentCaptor.forClass(V5aiApiKeys.class);
        verify(baseMapper).insert(captured.capture());
        assertThat(captured.getValue().getUserId()).isEqualTo(7L);
        assertThat(captured.getValue().getName()).isEqualTo("生产环境");
        assertThat(captured.getValue().getTrackingId()).isEqualTo(created.getTrackingId());
        assertThat(captured.getValue().getKeyHash()).isEqualTo(KEY_HASH);
        assertThat(captured.getValue().getEnabled()).isTrue();
        verify(bindingMapper).deleteByApiKeyId(11L);
        verify(bindingMapper, times(2)).insert(any(V5aiApiKeysAgent.class));
    }

    @Test
    void updateApiKeyRejectsKeyOwnedByAnotherUser() {
        when(baseMapper.selectById(1L)).thenReturn(apiKey(1L, 999L, true));

        assertThatThrownBy(() -> service.updateApiKey(1L, 7L, "改名", List.of("a1"), null))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("不存在");

        verify(baseMapper, never()).updateById(any(V5aiApiKeys.class));
        verifyNoInteractions(bindingMapper);
    }

    @Test
    void updateApiKeyRebindsAgents() {
        when(baseMapper.selectById(1L)).thenReturn(apiKey(1L, 7L, true));
        when(agentCatalogService.filterPublished(List.of("a2"))).thenReturn(Set.of("a2"));

        service.updateApiKey(1L, 7L, "新名字", List.of("a2"), false);

        var captured = ArgumentCaptor.forClass(V5aiApiKeys.class);
        verify(baseMapper).updateById(captured.capture());
        assertThat(captured.getValue().getName()).isEqualTo("新名字");
        assertThat(captured.getValue().getEnabled()).isFalse();
        verify(bindingMapper).deleteByApiKeyId(1L);
        verify(bindingMapper).insert(any(V5aiApiKeysAgent.class));
    }

    @Test
    void authenticateRejectsMalformedKeyWithoutTouchingDatabase() {
        assertThat(service.authenticate("not-a-platform-key")).isNull();
        assertThat(service.authenticate(null)).isNull();

        verifyNoInteractions(baseMapper);
    }

    @Test
    void authenticateRejectsDisabledKey() {
        when(baseMapper.selectByKeyHash(KEY_HASH)).thenReturn(apiKey(1L, 7L, false));

        assertThat(service.authenticate(PLAINTEXT_KEY)).isNull();
        verify(baseMapper, never()).touchLastUsed(any());
    }

    @Test
    void authenticateReturnsBoundAgentsAndRefreshesLastUsedAt() {
        when(baseMapper.selectByKeyHash(KEY_HASH)).thenReturn(apiKey(1L, 7L, true));
        when(bindingMapper.selectAgentKeysByApiKeyId(1L)).thenReturn(List.of("a1", "a2"));

        var auth = service.authenticate(PLAINTEXT_KEY);

        assertThat(auth).isNotNull();
        assertThat(auth.userId()).isEqualTo(7L);
        assertThat(auth.trackingId()).isEqualTo(TRACKING_ID);
        assertThat(auth.allows("a1")).isTrue();
        assertThat(auth.allows("a3")).isFalse();
        verify(baseMapper).touchLastUsed(1L);
    }

    @Test
    void authenticateRejectsWrongSecret() {
        // 密文与替身校验器约定的不一致 → 校验失败
        when(baseMapper.selectByKeyHash(KEY_HASH)).thenReturn(wrongSecretApiKey());

        assertThat(service.authenticate(PLAINTEXT_KEY)).isNull();
        verify(baseMapper, never()).touchLastUsed(any());
    }

    /**
     * 批量删除：任一条不属于当前用户就整体拒绝，不能只删掉属于自己的那部分——
     * 否则越权者能通过「部分成功」反推哪些 id 存在。
     */
    @Test
    void deleteApiKeysRejectsBatchContainingAnotherUsersKey() {
        when(baseMapper.selectByIds(List.of(1L, 2L)))
                .thenReturn(List.of(apiKey(1L, 7L, true), apiKey(2L, 999L, true)));

        assertThatThrownBy(() -> service.deleteApiKeys(List.of(1L, 2L), 7L))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("不存在");

        verify(baseMapper, never()).deleteByIds(any());
    }

    /** 全部属于当前用户时按 id 批量删除（绑定关系由外键级联删除）。 */
    @Test
    void deleteApiKeysRemovesOwnedKeys() {
        when(baseMapper.selectByIds(List.of(1L, 2L)))
                .thenReturn(List.of(apiKey(1L, 7L, true), apiKey(2L, 7L, false)));

        service.deleteApiKeys(List.of(1L, 2L, 2L), 7L);

        verify(baseMapper).deleteByIds(List.of(1L, 2L));
    }

    /** 同一用户下重名必须拒绝（不同用户可以同名，列表本就按用户隔离）。 */
    @Test
    void insertApiKeyRejectsDuplicateName() {
        when(baseMapper.existsByUserIdAndName(7L, "生产环境", null)).thenReturn(true);

        assertThatThrownBy(() -> service.insertApiKey(7L, "生产环境", List.of("a1")))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("名称已存在");

        verify(baseMapper, never()).insert(any(V5aiApiKeys.class));
    }

    /** 编辑时排除自己：名称没改（仍是自己的名字）应当放行。 */
    @Test
    void updateApiKeyAllowsKeepingOwnName() {
        when(baseMapper.selectById(1L)).thenReturn(apiKey(1L, 7L, true));
        when(baseMapper.existsByUserIdAndName(7L, "生产环境", 1L)).thenReturn(false);
        when(agentCatalogService.filterPublished(List.of("a2"))).thenReturn(Set.of("a2"));

        service.updateApiKey(1L, 7L, "生产环境", List.of("a2"), true);

        verify(baseMapper).updateById(any(V5aiApiKeys.class));
    }

    /** 改成别的 Key 已占用的名称必须拒绝。 */
    @Test
    void updateApiKeyRejectsNameTakenByAnotherKey() {
        when(baseMapper.selectById(1L)).thenReturn(apiKey(1L, 7L, true));
        when(baseMapper.existsByUserIdAndName(7L, "生产环境-B", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateApiKey(1L, 7L, "生产环境-B", List.of("a1"), null))
                .isInstanceOf(V5aiException.class)
                .hasMessageContaining("名称已存在");

        verify(baseMapper, never()).updateById(any(V5aiApiKeys.class));
        verifyNoInteractions(bindingMapper);
    }

    private V5aiApiKeys apiKey(Long id, Long userId, boolean enabled) {
        var entity = new V5aiApiKeys();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName("生产环境");
        entity.setTrackingId(TRACKING_ID);
        entity.setSecretHash(StubApiKeyVerifier.HASH);
        entity.setEnabled(enabled);
        return entity;
    }

    private V5aiApiKeys wrongSecretApiKey() {
        var entity = apiKey(1L, 7L, true);
        entity.setSecretHash("another-hash");
        return entity;
    }

    /**
     * 可控的 AgentApiKeysVerifier 替身：避开 BCrypt 计算，用固定摘要代替 sha256 做定位。
     */
    private static final class StubApiKeyVerifier implements AgentApiKeysVerifier {

        private static final String HASH = "stub-hash";

        @Override
        public boolean verify(String apiKey, String secretHash) {
            return HASH.equals(secretHash) && PLAINTEXT_KEY.equals(apiKey);
        }

        @Override
        public Tuple2<String, String> generateApiKey() {
            return Tuple2.<String, String>builder().first(PLAINTEXT_KEY).second(HASH).build();
        }

        @Override
        public String keyHash(String apiKey) {
            // 真实实现按「v5ai- + 32 位字母数字」做 sha256；替身只认约定明文
            return PLAINTEXT_KEY.equals(apiKey) ? KEY_HASH : null;
        }
    }
}