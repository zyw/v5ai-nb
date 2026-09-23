package xin.v5ai.nb.model.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.bo.ModelProviderBo;
import xin.v5ai.nb.model.domain.vo.V5aiModelProviderVo;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;
import xin.v5ai.nb.model.service.IV5aiModelProviderService;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 模型供应商服务实现类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class V5aiModelProviderServiceImpl implements IV5aiModelProviderService {

    private final V5aiModelProviderMapper mapper;
    private final V5aiModelMapper modelMapper;

    @Override
    public PageResult<V5aiModelProviderVo> queryPageList(ModelProviderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<V5aiModelProvider> lqw = buildQueryWrapper(bo);
        Page<V5aiModelProviderVo> result = mapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<V5aiModelProviderVo> queryList(ModelProviderBo bo) {
        LambdaQueryWrapper<V5aiModelProvider> lqw = buildQueryWrapper(bo);
        return mapper.selectVoList(lqw);
    }

    @Override
    public Boolean insertByBo(ModelProviderBo bo) {
        var existing = mapper.selectOne(new LambdaQueryWrapper<V5aiModelProvider>()
                .eq(V5aiModelProvider::getProviderKey, bo.getProviderKey()));
        if (existing != null) {
            throw new IllegalArgumentException("provider key already exists: " + bo.getProviderKey());
        }
        V5aiModelProvider add = MapstructUtils.convert(bo, V5aiModelProvider.class);
        assert add != null;
        add.setId(null);
        add.setEnabled(Boolean.TRUE.equals(bo.getEnabled()));
        boolean flag = mapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(ModelProviderBo bo) {
        var existing = mapper.selectById(bo.getId());
        if (existing == null) {
            throw new IllegalArgumentException("provider does not exist: " + bo.getId());
        }
        // 停用被模型引用的供应商：存在任一模型引用时禁止停用（覆盖列表 Switch 与编辑表单两条路径）
        if (Boolean.FALSE.equals(bo.getEnabled()) && Boolean.TRUE.equals(existing.getEnabled())) {
            long referenced = modelMapper.selectCount(new LambdaQueryWrapper<V5aiModel>()
                    .eq(V5aiModel::getProviderId, bo.getId()));
            if (referenced > 0) {
                throw new V5aiException(ErrorCode.CONFLICT, "该供应商下存在模型，无法停用");
            }
        }
        V5aiModelProvider update = MapstructUtils.convert(bo, V5aiModelProvider.class);
        assert update != null;
        update.setId(bo.getId());
        // 供应商标识作为业务主键，创建后不可修改
        update.setProviderKey(existing.getProviderKey());
        return mapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        long referenced = modelMapper.selectCount(new LambdaQueryWrapper<V5aiModel>()
                .in(V5aiModel::getProviderId, ids));
        if (referenced > 0) {
            throw new V5aiException(ErrorCode.CONFLICT, "当前供应商下存在模型，无法删除");
        }
        return mapper.deleteByIds(ids) > 0;
    }

    /**
     * 构造供应商列表查询条件。
     *
     * @param bo 供应商筛选条件
     * @return 包含 providerKey、name、enabled 等条件的查询包装器
     */
    private LambdaQueryWrapper<V5aiModelProvider> buildQueryWrapper(ModelProviderBo bo) {
        return QueryBuilder.lambda(V5aiModelProvider.class)
                .likeIfText(V5aiModelProvider::getProviderKey, bo.getProviderKey())
                .likeIfText(V5aiModelProvider::getName, bo.getName())
                .eq(bo.getEnabled() != null, V5aiModelProvider::getEnabled, bo.getEnabled())
                .and(StrUtil.isNotBlank(bo.getKeyword()),
                        w -> w.like(V5aiModelProvider::getName, bo.getKeyword())
                                .or()
                                .like(V5aiModelProvider::getProviderKey, bo.getKeyword()))
                .orderByAsc(V5aiModelProvider::getId)
                .build();
    }
}
