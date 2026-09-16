package xin.v5ai.nb.model.mapper;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.model.domain.V5aiModel;

import java.util.function.Predicate;

/**
 * 模型启用谓词：AgentDTO 只有在模型存在且启用时才能发布。
 * 由 {@code v5ai-agent.AgentManagementService} 按类型注入。
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Component
public class ModelEnabledPredicate implements Predicate<Long> {
    private final V5aiModelMapper modelMapper;

    public ModelEnabledPredicate(V5aiModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @Override
    public boolean test(Long modelId) {
        if (modelId == null) {
            return false;
        }
        V5aiModel entity = modelMapper.selectById(modelId);
        return entity != null && Boolean.TRUE.equals(entity.getEnabled());
    }
}
