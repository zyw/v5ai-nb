package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.V5aiApiKeys;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeysVo;

import java.time.OffsetDateTime;

/**
 * API Key（v5ai_api_keys）Mapper。
 */
@Mapper
public interface V5aiApiKeysMapper extends BaseMapperPlus<V5aiApiKeys, V5aiApiKeysVo> {

    /**
     * 按明文 Key 的 SHA-256 摘要定位密钥行（唯一索引）。
     *
     * @param keyHash 明文 Key 的 SHA-256 十六进制摘要
     * @return 匹配的密钥行，不存在时返回 null
     */
    default V5aiApiKeys selectByKeyHash(String keyHash) {
        return selectOne(new LambdaQueryWrapper<V5aiApiKeys>()
                .eq(V5aiApiKeys::getKeyHash, keyHash));
    }

    /**
     * 同一用户下是否已存在同名 Key（名称在用户维度唯一）。
     *
     * @param userId    归属用户
     * @param name      Key 名称
     * @param excludeId 排除的主键（编辑时排除自己，可为 null）
     * @return 已存在同名时返回 true
     */
    default boolean existsByUserIdAndName(Long userId, String name, Long excludeId) {
        return selectCount(new LambdaQueryWrapper<V5aiApiKeys>()
                .eq(V5aiApiKeys::getUserId, userId)
                .eq(V5aiApiKeys::getName, name)
                .ne(excludeId != null, V5aiApiKeys::getId, excludeId)) > 0;
    }

    /**
     * 刷新最新使用时间（鉴权成功时调用；不改 updated_at）。
     *
     * @param id 密钥主键
     * @return 影响行数
     */
    default int touchLastUsed(Long id) {
        return update(null, new LambdaUpdateWrapper<V5aiApiKeys>()
                .eq(V5aiApiKeys::getId, id)
                .set(V5aiApiKeys::getLastUsedAt, OffsetDateTime.now()));
    }
}