# 文档解析器统一采用 Apache POI 与 Jsoup

早期为省依赖，DOCX 用「原生 OOXML zip + 正则去标签」解析（`DocxDocumentParser` 注释明确写着 Avoids a heavy Apache POI dependency）。需求新增 XLSX/PPTX/HTML/CSV 支持后，需在「继续零依赖手写」与「引入 POI/Jsoup」之间取舍。

我们决定：OOXML 类（DOCX/XLSX/PPTX）统一改用 Apache POI（`poi-ooxml`），DOCX 废弃原生的 zip 抽取实现；HTML 改用 Jsoup；CSV 复用文本解析器（UTF-8 原样返回）。同时 URL 导入从「抓网页文本」升级为「下载原始字节 + 按类型（URL 扩展名 → Content-Type → 网页 HTML）判定后分发解析」，历史 `URL` 类型仅存续兼容。

**Status**: accepted

**Considered Options**:
- 继续手写 OOXML（沿用 DOCX 路线）：零新依赖、与既有 DOCX 一致，但 XLSX 的共享字符串/数值/日期/公式解析脆弱且易丢数据、HTML 标签剥离不可靠 —— 否决。
- 仅 DOCX 保留手写、XLSX/PPTX 用 POI：改动面略小，但「同是 OOXML 却两套解析」的分裂令后来者困惑 —— 否决。
- 全部 OOXML 用 POI、HTML 用 Jsoup：正确性最好、解析器语义一致 —— 采纳。

**Consequences**:
- 引入 `poi-ooxml`（含 xmlbeans 等传递依赖）与 `jsoup`；DOCX 旧的 `ZipInputStream` 实现删除，改由 POI 段落+表格文本抽取（表格单元格更能一并覆盖）。
- `DocumentFileType` 新增 XLSX/PPTX/HTML/CSV，并承载扩展名→类型、MIME→类型、URL 判定与默认扩展名/Content-Type 的单一事实源；`URL` 保留仅作存量行兼容，不再由新导入产生。
- 新增/改名文件类型无需数据库迁移（`fileType` 为 varchar）。
- URL 导入现支持远程 PDF/DOCX/XLSX/PPTX/CSV/网页下载并按类型解析；fetcher 返回值由 `String` 改为 `UrlContent(byte[], contentType)`，避免二进制经 UTF-8 往返损坏。