# 会话与上传附件的归属主体是 API Key，而不是用户

独立对话门户需要把会话变成服务端的一等对象（列表、命名、归档），并支持终端用户上传图片附件。但运行时表里能当归属键的列只有 `v5ai_conversation.user_id`，而这一列**当前恒为 NULL**（`ensureConversation` 只写 `id` 与 `agent_key`），且 `plm_resource.created_by` 依赖 Sa-Token 会话（`LoginHelper.getUserId()`），在 API Key 鉴权路径下根本没有会话可取。可选的归属主体有三种：用户、Agent、API Key。我们决定：**以 API Key 为终端用户数据的租户边界**——会话按 `api_key_id` 归属，上传附件同样只认发起上传的那把 Key；`user_id` 保留为展示与审计冗余，不参与任何归属判定。理由是只有 Key 这一层同时满足三点：调用方可识别（每个运行请求都带 Key）、可分发（一把 Key 可以被交给不同的人使用）、可吊销（吊销 Key 即切断其会话与附件的访问入口）。

**Status**: accepted

**Considered Options**:
- 按用户归属（`(user_id, agentKey)`）：一把 Key 可能被分发给多人使用，用户维度的列表会让共用同一把 Key 的人互相看见、互相改名、互相归档 —— 否决。
- 按 Agent 归属（仅 `agentKey`）：任何持有该 Agent 合法 Key 的调用方共享同一份会话列表，等价于把会话公开 —— 否决。
- 不设归属、凭会话 ID 访问：会话 ID 由调用方生成，无法枚举"我的会话"，也无法安全地列出列表 —— 否决。
- 按 API Key 归属（本方案）：与运行请求的鉴权粒度同源，一处判定即覆盖会话、附件与其读取 —— 采纳。

**Consequences**:
- `v5ai_conversation` 新增归属列 `api_key_id`；调试入口（Sa-Token）产生的会话该列为 NULL，因而**天然不出现在门户列表**里。
- `v5ai_conversation.user_id` 只作为展示/审计冗余落值，**不是租户边界**——读代码时不要拿它做过滤条件。
- 同一用户的两把 Key 之间**不共享**会话与附件；吊销一把 Key 后其会话与附件失去访问入口，但数据保留。
- 附件上传请求不带 `agentKey`（只有 Key 与资源参数），因此附件的归属只能来自 Key，与会话边界同源。
- 门户的会话列表、命名、归档，以及附件的读取，都必须以 `api_key_id` 为过滤/判定依据。
