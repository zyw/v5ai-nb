# Workflow Python 节点脚本案例

这些示例可直接复制到管理端 Workflow 编辑器的 Python 节点代码框（当前节点配置字段为 `code`，运行时也兼容 `script`）。脚本通过全局变量 `inputs` 读取数据，并必须给 `result` 赋一个 JSON object；Runner 将它作为该节点的 `outputs` 返回。

## 先了解输入上下文

Python 节点收到的是工作流运行时上下文快照，结构大致如下：

```json
{
  "inputs": {"value": 21, "records": []},
  "nodes": {"http_lookup": {"status": 200, "body": {"items": []}}},
  "customerId": "C-100"
}
```

- `inputs.get("inputs", {})`：START 节点传入的原始输入。
- `inputs.get("nodes", {})`：之前执行过的节点输出，按节点 ID 索引。
- 其它顶层键：工作流变量（例如 Variable、Agent 或已设置 `outputVar` 的节点写入的值）。
- 当前节点的结果必须放在 `result` 中，且最外层必须是对象；`print()` 不是输出协议。

在编辑器的「输出变量」填写名称（如 `pythonResult`）后，后续节点可通过 `pythonResult.someKey` 读取整个结果对象。没有特别需要时可留空，并通过节点输出引用访问。

## 1. Java ↔ Runner 冒烟测试

**输入**：START 输入 `{"value":21}`。**输出变量**：`runnerSmokeTest`。

```python
payload = inputs.get("inputs", {})
value = payload.get("value", 21)

if isinstance(value, bool) or not isinstance(value, (int, float)):
    raise ValueError("value must be a number")

result = {
    "message": "Python Runner 联调成功",
    "inputValue": value,
    "doubledValue": value * 2,
}
```

预期 `outputs`：`{"message":"Python Runner 联调成功","inputValue":21,"doubledValue":42}`。若此节点成功，说明 Java readiness 检查、Runner 鉴权、沙箱创建、脚本执行和 JSON 回传都已串通。

## 2. 清理并规范化记录

**输入**：`{"records":[{"id":1,"name":" Alice ","active":true},{"id":2,"name":"Bob","active":false}]}`。

```python
payload = inputs.get("inputs", {})
records = payload.get("records", [])
if not isinstance(records, list):
    raise ValueError("records must be a list")

clean_records = []
for item in records:
    if not isinstance(item, dict) or item.get("active") is not True:
        continue
    clean_records.append({
        "id": item.get("id"),
        "name": str(item.get("name", "")).strip(),
    })

result = {
    "count": len(clean_records),
    "records": clean_records,
}
```

预期只保留 Alice 记录。适合在 HTTP/Agent 节点之后做轻量字段清理；简单映射优先使用内置 JSON Transform 节点。

## 3. 读取上游 HTTP 节点响应

先放一个 HTTP 节点，假设它的节点 ID 为 `http_lookup`，响应 JSON 为 `{"items":[{"sku":"A-1","stock":3}]}`。把下面代码中的节点 ID 换成画布上实际 ID：

```python
nodes = inputs.get("nodes", {})
http_output = nodes.get("http_lookup", {})
body = http_output.get("body", {})
items = body.get("items", []) if isinstance(body, dict) else []

if not isinstance(items, list):
    raise ValueError("HTTP response items must be a list")

result = {
    "httpStatus": http_output.get("status"),
    "itemCount": len(items),
    "availableSkus": [
        item.get("sku")
        for item in items
        if isinstance(item, dict) and item.get("stock", 0) > 0
    ],
}
```

HTTP 节点输出形状为 `{status, body, contentType}`。若需让 Agent 或后续节点读取完整响应，也可以在 HTTP 节点配置 `outputVar`；不要在 Python 代码里重新请求同一个 API。

## 4. 生成条件分支标记

Python 节点可以输出供后续 Condition 节点判断的布尔/分类字段：

```python
payload = inputs.get("inputs", {})
amount = payload.get("amount", 0)
if isinstance(amount, bool) or not isinstance(amount, (int, float)):
    raise ValueError("amount must be a number")

result = {
    "approved": amount <= 500,
    "riskLevel": "LOW" if amount <= 500 else "REVIEW",
}
```

假设 Python 节点 ID 是 `python_check`，Condition 节点的 left 填 `{{nodes.python_check.approved}}`、operator 选 `==`、right 填 `true`，然后将 Condition 的 true/false 出边连到不同节点。节点 ID 按实际画布值替换（模板引用支持字母、数字、下划线和点）。保持判断逻辑简单、可审计；普通比较优先直接用 Condition 节点。

## 5. 标准库日期解析与 UTC 规范化

Runner 镜像不允许脚本临时联网安装依赖，但可以使用 Python 标准库：

```python
from datetime import datetime, timezone

payload = inputs.get("inputs", {})
raw_timestamp = payload.get("timestamp")
if not isinstance(raw_timestamp, str) or not raw_timestamp:
    raise ValueError("timestamp is required")

parsed = datetime.fromisoformat(raw_timestamp.replace("Z", "+00:00"))
if parsed.tzinfo is None:
    raise ValueError("timestamp must include a timezone")

result = {
    "utcTimestamp": parsed.astimezone(timezone.utc).isoformat(),
}
```

## 使用限制与排错

- 单脚本默认最多运行 30 秒、64 KiB；输入/输出默认上限 1 MiB。服务端配置不能被脚本突破。
- 只使用 executor 镜像预装包和 Python 标准库；沙箱默认无网络，不支持 `pip install`。
- `result` 必须是有限深度、可 JSON 序列化的对象；不要返回 `set`、日期对象、NaN/Infinity 或自定义 Python 对象。
- 脚本异常或非法结果返回 422；超时返回 504；Runner 容量满返回 429；未就绪返回 503。Java 节点运行错误中会带 Runner 稳定错误码。
- 禁止把 Runner Token、密码或其他凭据写进脚本、输入、输出或 `print()`；stdout/stderr 不作为工作流输出。

部署和 Java ↔ Runner 联调步骤见 [Workflow Python Runner 部署指南](../deploy/workflow-python-runner.md#python-节点-java--runner-联调脚本)。
