package xin.v5ai.nb.platform.listener;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import xin.v5ai.nb.platform.event.OnlineUserCleanEvent;
import xin.v5ai.nb.platform.service.IPlmRoleService;

/**
 * 在线用户清理监听器。
 *
 * @author Lion Li
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlineUserCleanListener {

    private final IPlmRoleService roleService;

    /**
     * 权限或用户角色关系变化后清理受影响的在线用户。
     *
     * @param event 在线用户清理事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void cleanOnlineUser(OnlineUserCleanEvent event) {
        // 在线用户清理是尽力而为的收尾动作，不应因为个别会话不可用而让已提交的业务操作返回失败。
        try {
            if (event.roleId() != null) {
                roleService.cleanOnlineUserByRole(event.roleId());
            }
            if (CollUtil.isNotEmpty(event.userIds())) {
                roleService.cleanOnlineUser(event.userIds());
            }
        } catch (Exception e) {
            log.warn("在线用户清理失败，已忽略：roleId={}, userIds={}", event.roleId(), event.userIds(), e);
        }
    }

}
