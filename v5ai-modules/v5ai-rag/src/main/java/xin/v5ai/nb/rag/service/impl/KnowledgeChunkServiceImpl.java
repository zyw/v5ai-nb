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
        return baseMapper.insertBatch(knowledgeChunks);
    }

    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
        baseMapper.delete(new QueryWrapper<KnowledgeChunk>().eq("knowledge_base_id", knowledgeBaseId).eq("document_id", documentId));
    }

    @Override
    public List<KnowledgeChunkDO> findChunkByBaseIds(String embed1, List<Long> ids, String embed2, int limit) {
        return baseMapper.findChunkByBaseIds(embed1, ids, embed2, limit);
    }

    @Override
    public List<KnowledgeChunkDO> findChunkByIdsAndLinkKeyword(List<Long> ids, String keyword, int limit) {
        return baseMapper.findChunkByIdsAndLinkKeyword(ids, keyword, limit);
    }
}
