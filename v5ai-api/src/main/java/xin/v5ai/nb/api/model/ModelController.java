package xin.v5ai.nb.api.model;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.agent.AgentRepository;
import xin.v5ai.nb.api.common.OptionResponse;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.model.CreateModelCommand;
import xin.v5ai.nb.model.ModelManagementService;
import xin.v5ai.nb.model.UpdateModelCommand;

import java.util.List;

@RestController
@RequestMapping("/api/admin/models")
public class ModelController {
    private final ModelManagementService modelManagementService;
    private final AgentRepository agentRepository;

    public ModelController(ModelManagementService modelManagementService, AgentRepository agentRepository) {
        this.modelManagementService = modelManagementService;
        this.agentRepository = agentRepository;
    }

    @PostMapping
    public R<ModelResponse> createModel(@RequestBody CreateModelRequest request) {
        return R.ok(ModelResponse.from(modelManagementService.createModel(new CreateModelCommand(
                request.providerKey(), request.modelKey(), request.type(), request.credentials()))));
    }

    @GetMapping
    public R<PageResult<ModelResponse>> listModels(PageQuery pageQuery) {
        var list = modelManagementService.listModels().stream()
                .map(ModelResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionResponse>> listModelOptions() {
        return R.ok(modelManagementService.listModelOptions().stream()
                .map(model -> new OptionResponse(model.id(),
                        model.providerKey() + "/" + model.modelKey() + " (" + model.type() + ")"))
                .toList());
    }

    @PutMapping("/{id}")
    public R<ModelResponse> updateModel(@PathVariable("id") Long id, @RequestBody UpdateModelRequest request) {
        return R.ok(ModelResponse.from(modelManagementService.updateModel(new UpdateModelCommand(
                id, request.modelKey(), request.type(), request.credentials(),
                request.enabled() == null || request.enabled()))));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteModel(@PathVariable("id") Long id) {
        if (modelManagementService.findModel(id) == null) {
            throw new V5aiException(ErrorCode.NOT_FOUND, "model does not exist: " + id);
        }
        boolean referenced = agentRepository.list().stream()
                .anyMatch(application -> application.modelId() != null && application.modelId().equals(id));
        if (referenced) {
            throw new V5aiException(ErrorCode.CONFLICT,
                    "model is used by an application and cannot be deleted");
        }
        modelManagementService.deleteModel(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<TestConnectionResponse> testConnection(@PathVariable("id") Long id) {
        try {
            var result = modelManagementService.testConnection(id);
            return R.ok(result.ok()
                    ? TestConnectionResponse.ok(id)
                    : TestConnectionResponse.failed(id, result.message()));
        } catch (Exception exception) {
            return R.ok(TestConnectionResponse.failed(id, exception.getMessage()));
        }
    }
}
