package xin.v5ai.nb.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.bo.ModelProviderBo;
import xin.v5ai.nb.model.domain.vo.V5aiModelProviderVo;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;
import xin.v5ai.nb.model.service.IV5aiModelProviderService;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 *  服务实现类
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

    @Override
    public PageResult<V5aiModelProviderVo> queryPageList(ModelProviderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<V5aiModelProvider> lqw = buildQueryWrapper(bo);
        Page<V5aiModelProviderVo> result = mapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<V5aiModelProviderVo> queryList(ModelProviderBo bo) {
        return List.of();
    }

    @Override
    public Boolean insertByBo(ModelProviderBo bo) {
        V5aiModelProvider modelProvider = mapper.selectOne(new LambdaQueryWrapper<V5aiModelProvider>()
                .eq(V5aiModelProvider::getProviderKey, bo.getProviderKey()));
        if (modelProvider != null) {
            throw new IllegalArgumentException("provider key already exists");
        }
        V5aiModelProvider add = MapstructUtils.convert(bo, V5aiModelProvider.class);
        boolean flag = mapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(ModelProviderBo bo) {
        return null;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return null;
    }


    /**
     * 构造客户端列表查询条件。
     *
     * @param bo 客户端筛选条件
     * @return 包含 clientId、clientKey、状态等条件的查询包装器
     */
    private LambdaQueryWrapper<V5aiModelProvider> buildQueryWrapper(ModelProviderBo bo) {
        return QueryBuilder.lambda(V5aiModelProvider.class)
//                .eqIfText(SysClient::getClientId, bo.getClientId())
//                .eqIfText(SysClient::getClientKey, bo.getClientKey())
//                .eqIfText(SysClient::getClientSecret, bo.getClientSecret())
//                .eqIfText(SysClient::getStatus, bo.getStatus())
//                .orderByAsc(SysClient::getId)
                .build();
    }
}
