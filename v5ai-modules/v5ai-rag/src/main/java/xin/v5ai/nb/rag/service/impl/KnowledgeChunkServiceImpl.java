package xin.v5ai.nb.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.domain.KnowledgeChunkDO;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;
import xin.v5ai.nb.rag.service.IKnowledgeChunkService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements IKnowledgeChunkService {

    private final KnowledgeChunkMapper baseMapper;

    @Override
    public boolean saveBatch(List<KnowledgeChunk> knowledgeChunks) {
        return baseMapper.batchSaveChunks(knowledgeChunks);
    }

    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
        baseMapper.delete(new QueryWrapper<KnowledgeChunk>().eq("knowledge_base_id", knowledgeBaseId).eq("document_id", documentId));
    }

    @Override
    public List<KnowledgeChunkDO> selectByVectorIds(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectByVectorIds(vectorIds);
    }

    @Override
    public List<KnowledgeChunkDO> findChunkByIdsAndLinkKeyword(List<Long> ids, String keyword, int limit) {
        return baseMapper.findChunkByIdsAndLinkKeyword(ids, keyword, limit);
    }
}
