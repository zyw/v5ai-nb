package xin.v5ai.nb.model.service;

import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.model.*;

import java.util.List;

public interface IModelProviderService {
    ModelProvider createProvider(CreateProviderCommand command);

    ModelProvider updateProvider(UpdateProviderCommand command);

    void deleteProvider(String providerKey);

    List<ModelProvider> listProviders();

    PageResult<ModelProvider> queryPageList(PageQuery pageQuery);
}
