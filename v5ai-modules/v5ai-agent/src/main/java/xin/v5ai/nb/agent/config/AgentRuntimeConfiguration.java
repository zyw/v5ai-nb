//package xin.v5ai.nb.agent.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
//import xin.v5ai.nb.common.agentscope.core.service.AgentService;
//
//@Configuration
//public class AgentRuntimeConfiguration {
//
//    /**
//     * 运行时 {@link AgentService} 由 {@code DBAgentServiceImpl}（{@code @Primary}，MyBatis-Plus）提供；
//     * 此处不再声明内存实现或演示数据，避免同类型多候选导致的静默降级。
//     */
//    @Bean
//    PublishedAgentResolver publishedAgentResolver(AgentService agentService) {
//        return new AgentScopePublishedAgentResolver(agentService);
//    }
//}
