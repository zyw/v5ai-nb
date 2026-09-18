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
     * 按 tracking id 定位密钥行（唯一索引）。
     *
     * @param trackingId 明文 Key 内嵌的 UUID（36 位）
     * @return 匹配的密钥行，不存在时返回 null
     */
    default V5aiApiKeys selectByTrackingId(String trackingId) {
        return selectOne(new LambdaQueryWrapper<V5aiApiKeys>()
                .eq(V5aiApiKeys::getTrackingId, trackingId));
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