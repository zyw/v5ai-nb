//package xin.v5ai.nb.infrastructure.model;
//
//import org.springframework.stereotype.Component;
//import xin.v5ai.nb.agent.AgentManagementService;
//
//import java.util.function.Predicate;
//
///**
// * Backing predicate for {@link AgentManagementService}: an Agent can only be
// * published when its model exists and is enabled.
// */
//@Component
//public class ModelEnabledPredicate implements Predicate<Long> {
//    private final ModelConfigGateway gateway;
//
//    public ModelEnabledPredicate(ModelConfigGateway gateway) {
//        this.gateway = gateway;
//    }
//
//    @Override
//    public boolean test(Long modelId) {
//        if (modelId == null) {
//            return false;
//        }
//        var entity = gateway.selectModelById(modelId);
//        return entity != null && Boolean.TRUE.equals(entity.getEnabled());
//    }
//}
