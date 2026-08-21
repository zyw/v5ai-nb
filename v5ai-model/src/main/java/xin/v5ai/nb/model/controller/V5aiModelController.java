package xin.v5ai.nb.model.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.model.CreateModelCommand;
import xin.v5ai.nb.model.UpdateModelCommand;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.service.IV5aiModelService;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/models")
public class V5aiModelController extends BaseController {

    private final IV5aiModelService service;

    @PostMapping
    public R<Void> createModel(@Validated(AddGroup.class) @RequestBody ModelBo bo) {
        return toAjax(service.insertByBo(bo));
    }

    @GetMapping
    public R<PageResult<ModelResponse>> listModels(PageQuery pageQuery) {
        var list = service.listModels().stream()
                .map(ModelResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionResponse>> listModelOptions() {
        return R.ok(service.listModelOptions().stream()
                .map(model -> new OptionResponse(model.id(),
                        model.providerKey() + "/" + model.modelKey() + " (" + model.type() + ")"))
                .toList());
    }

    @PutMapping("/{id}")
    public R<ModelResponse> updateModel(@PathVariable("id") Long id, @RequestBody UpdateModelRequest request) {
        return R.ok(ModelResponse.from(service.updateModel(new UpdateModelCommand(
                id, request.modelKey(), request.type(), request.credentials(),
                request.enabled() == null || request.enabled()))));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteModel(@PathVariable("id") Long id) {
        if (service.findModel(id) == null) {
            throw new V5aiException(ErrorCode.NOT_FOUND, "model does not exist: " + id);
        }
        boolean referenced = agentRepository.list().stream()
                .anyMatch(application -> application.modelId() != null && application.modelId().equals(id));
        if (referenced) {
            throw new V5aiException(ErrorCode.CONFLICT,
                    "model is used by an application and cannot be deleted");
        }
        service.deleteModel(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<TestConnectionResponse> testConnection(@PathVariable("id") Long id) {
        try {
            var result = service.testConnection(id);
            return R.ok(result.ok()
                    ? TestConnectionResponse.ok(id)
                    : TestConnectionResponse.failed(id, result.message()));
        } catch (Exception exception) {
            return R.ok(TestConnectionResponse.failed(id, exception.getMessage()));
        }
    }
}
