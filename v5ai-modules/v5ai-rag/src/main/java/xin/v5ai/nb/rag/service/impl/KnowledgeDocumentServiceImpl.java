package xin.v5ai.nb.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.rag.core.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.service.IKnowledgeDocumentService;

import java.util.List;

/**
 * {@link IKnowledgeDocumentService} / {@link KnowledgeDocumentContentStore} 的
 * MyBatis-Plus 实现（Worker 索引链路与检索上下文消费）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements IKnowledgeDocumentService, KnowledgeDocumentContentStore {

    private final KnowledgeDocumentMapper baseMapper;
    private final ResourceContentPort resourceContentPort;

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        if (document.getId() == null) {
            baseMapper.insert(document);
        } else {
            baseMapper.updateById(document);
        }
        return document;
    }

    @Override
    public KnowledgeDocument findById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId) {
        return baseMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocument::getId));
    }

    @Override
    public void delete(Long id) {
        baseMapper.deleteById(id);
    }

    @Override
    public byte[] loadContent(Long documentId) {
        var document = baseMapper.selectById(documentId);
        if (document == null) {
            return new byte[0];
        }
        // 新上传/URL 导入的源文件统一存资源存储（resource_id 指向）；BYTEA 列仅作存量兜底
        if (document.getResourceId() != null) {
            return resourceContentPort.read(document.getResourceId()).bytes();
        }
        return document.getContent();
    }

    @Override
    public void saveContent(Long documentId, byte[] content) {
        var update = new KnowledgeDocument();
        update.setId(documentId);
        update.setContent(content);
        baseMapper.updateById(update);
    }
}
