package xin.v5ai.nb.rag.core.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;

/**
 * {@link KnowledgeBaseRepository} 的 MyBatis-Plus 实现。
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Repository
@RequiredArgsConstructor
public class MyBatisKnowledgeBaseRepository implements KnowledgeBaseRepository {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public KnowledgeBase findById(Long id) {
        return knowledgeBaseMapper.selectById(id);
    }
}
