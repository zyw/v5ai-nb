package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.agent.api.AgentCatalogService;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.encrypt.utils.CipherUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.api.ApiKeysService;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;
import xin.v5ai.nb.platform.domain.V5aiApiKeys;
import xin.v5ai.nb.platform.domain.V5aiApiKeysAgent;
import xin.v5ai.nb.platform.domain.bo.V5aiApiKeysBo;
import xin.v5ai.nb.platform.domain.vo.ApiKeysRespVo;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeysVo;
import xin.v5ai.nb.platform.mapper.V5aiApiKeysAgentMapper;
import xin.v5ai.nb.platform.mapper.V5aiApiKeysMapper;
import xin.v5ai.nb.platform.service.IV5aiApiKeysService;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * API Key 管理与运行期鉴权实现。
 *
 * <p>Key 归属创建用户：列表恒按当前登录用户过滤，写操作先校验归属，
 * 避免越权改删他人的 Key。可访问 Agent 存 {@code v5ai_api_keys_agent}，
 * 新建/重绑时经 {@link AgentCatalogService} 服务端校验「只能绑定已发布 Agent」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class V5aiApiKeysServiceImpl implements IV5aiApiKeysService, ApiKeysService {

    /**
     * 单个 Key 可绑定的 Agent 数上限（防止一次请求塞入过量绑定）。
     */
    private static final int MAX_BOUND_AGENTS = 100;

    /**
     * Key 名称长度上限（与 v5ai_api_keys.name VARCHAR(100) 对齐）。
     */
    private static final int MAX_NAME_LENGTH = 100;

    private final V5aiApiKeysMapper baseMapper;
    private final V5aiApiKeysAgentMapper bindingMapper;
    private final AgentCatalogService agentCatalogService;

    @Override
    public PageResult<V5aiApiKeysVo> selectPageList(V5aiApiKeysBo bo, PageQuery pageQuery, Long userId) {
        Page<V5aiApiKeysVo> page = baseMapper.selectVoPage(pageQuery.build(),
                buildQueryWrapper(bo, userId));
        fillAgentKeys(page.getRecords());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeysRespVo insertApiKey(Long userId, String name, List<String> agentKeys) {
        String keyName = normalizeName(name);
        requireNameAvailable(userId, keyName, null);
        List<String> bound = requirePublished(agentKeys);

        var generated = CipherUtils.generateApiKey();
        String plaintext = generated.getFirst();
        // tracking id 与明文 Key 无关：独立的 UUID，仅用于页面展示与日志/审计对账；
        // 运行时定位密钥行靠明文 Key 的 SHA-256 摘要（key_hash 唯一索引）。
        String trackingId = UUID.randomUUID().toString();
        var entity = new V5aiApiKeys();
        entity.setUserId(userId);
        entity.setName(keyName);
        entity.setTrackingId(trackingId);
        entity.setKeyHash(CipherUtils.keyHash(plaintext));
        entity.setSecretHash(generated.getSecond());
        entity.setEnabled(true);
        baseMapper.insert(entity);
        replaceBindings(entity.getId(), bound);

        return ApiKeysRespVo.builder()
                .id(entity.getId())
                .name(keyName)
                .trackingId(trackingId)
                .apiKey(plaintext)
                .agentKeys(bound)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApiKey(Long id, Long userId, String name, List<String> agentKeys, Boolean enabled) {
        var entity = requireOwned(id, userId);
        String keyName = normalizeName(name);
        // 编辑时排除自己，允许「不改名直接保存」
        requireNameAvailable(userId, keyName, entity.getId());
        List<String> bound = requirePublished(agentKeys);

        entity.setName(keyName);
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        baseMapper.updateById(entity);
        replaceBindings(entity.getId(), bound);
    }

    @Override
    public void changeEnabled(Long id, Long userId, Boolean enabled) {
        if (enabled == null) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "启用状态不能为空");
        }
        var entity = requireOwned(id, userId);
        entity.setEnabled(enabled);
        baseMapper.updateById(entity);
    }

    /**
     * 批量删除：删除前逐条校验归属（与角色管理的「批量删除」一致，但这里不允许删他人的 Key），
     * 任一条不存在或不属于当前用户就整体拒绝，避免部分删除后被越权探测出哪些 id 存在。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKeys(List<Long> ids, Long userId) {
        List<Long> targets = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "请选择要删除的 API Key");
        }
        var owned = baseMapper.selectByIds(targets);
        if (owned.size() != targets.size()
                || owned.stream().anyMatch(key -> !Objects.equals(key.getUserId(), userId))) {
            throw new V5aiException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        // 绑定关系由 v5ai_api_keys_agent.api_key_id 外键级联删除
        baseMapper.deleteByIds(targets);
    }

    /**
     * 运行期鉴权：按明文 Key 的 SHA-256 摘要（key_hash 唯一索引）定位密钥行 →
     * 校验启用状态与 BCrypt 密文 → 取可访问 Agent 并刷新最新使用时间。
     * tracking_id 只是展示/审计用的独立 UUID，不参与定位。
     */
    @Override
    public ApiKeysAuthDTO authenticate(String apiKey) {
        String keyHash = CipherUtils.keyHash(apiKey);
        if (keyHash == null) {
            return null;
        }
        var entity = baseMapper.selectByKeyHash(keyHash);
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
            return null;
        }
        if (!CipherUtils.verify(apiKey, entity.getSecretHash())) {
            return null;
        }
        var agentKeys = bindingMapper.selectAgentKeysByApiKeyId(entity.getId());
        baseMapper.touchLastUsed(entity.getId());
        return new ApiKeysAuthDTO(entity.getId(), entity.getUserId(), entity.getName(),
                entity.getTrackingId(), true, Set.copyOf(agentKeys));
    }

    /**
     * 构造列表查询条件：恒限定当前登录用户，可选按名称、状态、绑定 Agent 过滤。
     */
    private LambdaQueryWrapper<V5aiApiKeys> buildQueryWrapper(V5aiApiKeysBo bo, Long userId) {
        var wrapper = QueryBuilder.lambda(V5aiApiKeys.class)
                .eq(V5aiApiKeys::getUserId, userId)
                .likeIfText(V5aiApiKeys::getName, bo.getName())
                .eq(bo.getEnabled() != null, V5aiApiKeys::getEnabled, bo.getEnabled())
                .orderByDesc(V5aiApiKeys::getCreatedAt)
                .build();
        if (StringUtils.isNotBlank(bo.getAgentKey())) {
            wrapper.exists("select 1 from v5ai_api_keys_agent b where b.api_key_id = v5ai_api_keys.id and b.agent_key = {0}",
                    bo.getAgentKey().trim());
        }
        return wrapper;
    }

    /**
     * 列表页批量补齐每把 Key 的可访问 Agent（一次查询，非逐行查询）。
     */
    private void fillAgentKeys(List<V5aiApiKeysVo> records) {
        if (CollUtil.isEmpty(records)) {
            return;
        }
        var ids = records.stream().map(V5aiApiKeysVo::getId).filter(Objects::nonNull).toList();
        var grouped = bindingMapper.selectAgentKeysByApiKeyIds(ids);
        records.forEach(vo -> vo.setAgentKeys(grouped.getOrDefault(vo.getId(), List.of())));
    }

    /**
     * 覆盖式重绑：先清空该 Key 的全部绑定，再按入参写入。
     */
    private void replaceBindings(Long apiKeyId, List<String> agentKeys) {
        bindingMapper.deleteByApiKeyId(apiKeyId);
        for (String agentKey : agentKeys) {
            var binding = new V5aiApiKeysAgent();
            binding.setApiKeyId(apiKeyId);
            binding.setAgentKey(agentKey);
            bindingMapper.insert(binding);
        }
    }

    /**
     * 校验 Key 归属：只允许操作指定用户自己创建的 Key。
     */
    private V5aiApiKeys requireOwned(Long id, Long userId) {
        var entity = id == null ? null : baseMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            throw new V5aiException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        return entity;
    }

    /**
     * 归一化并校验 Key 名称。
     */
    private String normalizeName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "Key 名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "Key 名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return trimmed;
    }

    /**
     * 校验名称唯一：同一用户下不允许重名（不同用户可以同名，列表本就是按用户隔离的）。
     * 数据库侧还有 (user_id, name) 唯一索引兜底，这里负责给出可读的错误信息。
     *
     * @param userId    归属用户
     * @param name      归一化后的名称
     * @param excludeId 编辑时排除自己，新增传 null
     */
    private void requireNameAvailable(Long userId, String name, Long excludeId) {
        if (baseMapper.existsByUserIdAndName(userId, name, excludeId)) {
            throw new V5aiException(ErrorCode.CONFLICT, "Key 名称已存在，请换一个名称");
        }
    }

    /**
     * 校验可访问 Agent：去重、非空、数量受限，且必须全部处于 PUBLISHED 状态（服务端权威校验）。
     */
    private List<String> requirePublished(List<String> agentKeys) {
        List<String> keys = agentKeys == null ? List.of() : agentKeys.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (keys.isEmpty()) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT, "请至少选择一个可访问的 Agent");
        }
        if (keys.size() > MAX_BOUND_AGENTS) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT,
                    "单个 Key 最多绑定 " + MAX_BOUND_AGENTS + " 个 Agent");
        }
        var published = agentCatalogService.filterPublished(keys);
        var invalid = keys.stream().filter(key -> !published.contains(key)).toList();
        if (!invalid.isEmpty()) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT,
                    "只能绑定已发布的 Agent：" + String.join("、", invalid));
        }
        return keys;
    }
}