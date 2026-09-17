//package xin.v5ai.nb.mcp.mapper;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Repository;
//import xin.v5ai.nb.mcp.core.domain.dto.McpServerDTO;
//import xin.v5ai.nb.mcp.core.service.McpServerService;
//import xin.v5ai.nb.mcp.domain.McpServer;
//
///**
// * {@link McpServerService} 的 MyBatis-Plus 实现（运行时按 ID 读取 Server 配置）。
// *
// * @author ZYW
// * @since 2026-08-22
// */
//@Repository
//@RequiredArgsConstructor
//public class MyBatisMcpServerRepository implements McpServerService {
//
//    private final McpServerMapper serverMapper;
//
//    @Override
//    public McpServerDTO selectById(Long id) {
//        McpServer entity = serverMapper.selectById(id);
//        return entity == null ? null : toDomain(entity);
//    }
//
//    private McpServerDTO toDomain(McpServer entity) {
//        return buildMcpServerDTO(entity);
//    }
//}
