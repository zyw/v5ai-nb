package xin.v5ai.nb.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.V5aiModelUsage;
import xin.v5ai.nb.platform.domain.bo.ModelUsageListBo;
import xin.v5ai.nb.platform.domain.vo.V5aiModelUsageVo;
import xin.v5ai.nb.platform.mapper.V5aiModelUsageMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V5aiModelUsageServiceImplTest {

    private V5aiModelUsageMapper mapper;
    private V5aiModelUsageServiceImpl service;
    private Wrapper<V5aiModelUsage>[] capturedWrapper;

    @BeforeEach
    void setUp() {
        var configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), V5aiModelUsage.class);
        mapper = mock(V5aiModelUsageMapper.class);
        capturedWrapper = new Wrapper[1];
        when(mapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            capturedWrapper[0] = invocation.getArgument(1);
            return new Page<V5aiModelUsageVo>(1, 10, 0);
        });
        service = new V5aiModelUsageServiceImpl(mapper);
    }

    @Test
    void queryPageListAppliesInclusiveFromAndToAlongsideAgentKey() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-01T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-09-25T23:59:59+08:00");
        var bo = new ModelUsageListBo();
        bo.setAgentKey("demo");
        bo.setFrom(from);
        bo.setTo(to);

        service.queryPageList(bo, new PageQuery());

        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<V5aiModelUsage> wrapper = (LambdaQueryWrapper<V5aiModelUsage>) capturedWrapper[0];
        assertThat(wrapper.getSqlSegment())
                .contains("agentKey =")
                .contains("createdAt >=")
                .contains("createdAt <=");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("demo", from, to);
    }

    @Test
    void queryPageListAllowsOnlyFromBoundary() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-01T00:00:00+08:00");
        var bo = new ModelUsageListBo();
        bo.setFrom(from);

        service.queryPageList(bo, new PageQuery());

        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<V5aiModelUsage> wrapper = (LambdaQueryWrapper<V5aiModelUsage>) capturedWrapper[0];
        assertThat(wrapper.getSqlSegment()).contains("createdAt >=").doesNotContain("createdAt <=");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly(from);
    }

    @Test
    void queryPageListAllowsOnlyToBoundary() {
        OffsetDateTime to = OffsetDateTime.parse("2026-09-25T23:59:59+08:00");
        var bo = new ModelUsageListBo();
        bo.setTo(to);

        service.queryPageList(bo, new PageQuery());

        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<V5aiModelUsage> wrapper = (LambdaQueryWrapper<V5aiModelUsage>) capturedWrapper[0];
        assertThat(wrapper.getSqlSegment()).contains("createdAt <=").doesNotContain("createdAt >=");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly(to);
    }
}
