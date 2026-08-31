package xin.v5ai.nb.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.domain.bo.StoreInstanceBo;
import xin.v5ai.nb.rag.domain.vo.StoreInstanceVo;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;
import xin.v5ai.nb.rag.service.IStoreInstanceService;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 存储实例服务实现类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreInstanceServiceImpl implements IStoreInstanceService {

    /**
     * 启用状态
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 分类: 向量库
     */
    private static final int CATEGORY_VECTOR = 1;

    /**
     * 分类: 搜索引擎
     */
    private static final int CATEGORY_SEARCH = 2;

    /**
     * 类型: PG_VECTOR（向量库）
     */
    private static final int TYPE_PG_VECTOR = 1;

    /**
     * 类型: MILVUS（向量库）
     */
    private static final int TYPE_MILVUS = 2;

    /**
     * 类型: ELASTICSEARCH（搜索引擎）
     */
    private static final int TYPE_ELASTICSEARCH = 3;

    /**
     * 类型: PG_FULLTEXT（搜索引擎）
     */
    private static final int TYPE_PG_FULLTEXT = 4;

    private final StoreInstanceMapper storeInstanceMapper;

    @Override
    public PageResult<StoreInstanceVo> queryPageList(StoreInstanceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<StoreInstance> lqw = buildQueryWrapper(bo);
        Page<StoreInstanceVo> result = storeInstanceMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<StoreInstanceVo> queryList(StoreInstanceBo bo) {
        LambdaQueryWrapper<StoreInstance> lqw = buildQueryWrapper(bo);
        return storeInstanceMapper.selectVoList(lqw);
    }

    @Override
    public StoreInstanceVo queryById(Long id) {
        StoreInstance entity = storeInstanceMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("存储实例不存在: " + id);
        }
        return MapstructUtils.convert(entity, StoreInstanceVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(StoreInstanceBo bo) {
        validEntityBeforeSave(bo);
        StoreInstance add = MapstructUtils.convert(bo, StoreInstance.class);
        assert add != null;
        add.setId(null);
        add.setStatus(bo.getStatus() == null ? STATUS_ENABLED : bo.getStatus());
        add.setIsDefault(Boolean.TRUE.equals(bo.getIsDefault()));
        boolean flag = storeInstanceMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
            if (add.getIsDefault()) {
                clearDefault(add.getCategory(), add.getId());
            }
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(StoreInstanceBo bo) {
        exist(bo.getId());
        validEntityBeforeSave(bo);
        StoreInstance update = MapstructUtils.convert(bo, StoreInstance.class);
        assert update != null;
        update.setId(bo.getId());
        boolean flag = storeInstanceMapper.updateById(update) > 0;
        if (flag && Boolean.TRUE.equals(update.getIsDefault())) {
            clearDefault(update.getCategory(), update.getId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("主键不能为空");
        }
        if (storeInstanceMapper.selectByIds(ids).size() != ids.size()) {
            throw new ServiceException("存储实例不存在或已被删除");
        }
        return storeInstanceMapper.deleteByIds(ids) > 0;
    }

    /**
     * 保存前校验：分类/类型取值及其组合合法性，config 必须为合法 JSON。
     */
    private void validEntityBeforeSave(StoreInstanceBo bo) {
        Integer category = bo.getCategory();
        Integer type = bo.getType();
        if (category == null
                || (category != CATEGORY_VECTOR && category != CATEGORY_SEARCH)) {
            throw new ServiceException("分类不合法: 1-向量库 2-搜索引擎");
        }
        if (type == null || (type < TYPE_PG_VECTOR || type > TYPE_PG_FULLTEXT)) {
            throw new ServiceException("类型不合法: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-PG_FULLTEXT");
        }
        boolean categoryMatch = category == CATEGORY_VECTOR
                ? (type == TYPE_PG_VECTOR || type == TYPE_MILVUS || type == TYPE_ELASTICSEARCH)
                : (type == TYPE_ELASTICSEARCH || type == TYPE_PG_FULLTEXT);
        if (!categoryMatch) {
            throw new ServiceException("类型与分类不匹配: 向量库支持 PG_VECTOR/MILVUS/ELASTICSEARCH，搜索引擎仅支持 ELASTICSEARCH/PG_FULLTEXT");
        }
        if (StrUtil.isNotBlank(bo.getConfig()) && !JSONUtil.isTypeJSON(bo.getConfig())) {
            throw new ServiceException("连接参数必须为合法 JSON");
        }
    }

    /**
     * 构造存储实例列表查询条件。
     */
    private LambdaQueryWrapper<StoreInstance> buildQueryWrapper(StoreInstanceBo bo) {
        return QueryBuilder.lambda(StoreInstance.class)
                .likeIfText(StoreInstance::getName, bo == null ? null : bo.getName())
                .eqIfPresent(StoreInstance::getCategory, bo == null ? null : bo.getCategory())
                .eqIfPresent(StoreInstance::getType, bo == null ? null : bo.getType())
                .eqIfPresent(StoreInstance::getStatus, bo == null ? null : bo.getStatus())
                .eqIfPresent(StoreInstance::getIsDefault, bo == null ? null : bo.getIsDefault())
                .orderByAsc(StoreInstance::getId)
                .build();
    }

    /**
     * 取消同分类下其它默认实例，保证每个分类至多一个默认实例。
     */
    private void clearDefault(Integer category, Long excludeId) {
        StoreInstance reset = new StoreInstance();
        reset.setIsDefault(false);
        storeInstanceMapper.update(reset, new LambdaUpdateWrapper<StoreInstance>()
                .eq(StoreInstance::getCategory, category)
                .ne(StoreInstance::getId, excludeId)
                .eq(StoreInstance::getIsDefault, true));
    }

    private StoreInstance exist(Long id) {
        StoreInstance existing = storeInstanceMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("存储实例不存在: " + id);
        }
        return existing;
    }
}
