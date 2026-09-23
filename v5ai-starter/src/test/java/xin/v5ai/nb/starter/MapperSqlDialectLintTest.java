package xin.v5ai.nb.starter;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper XML 的方言 lint：防「新写了一段只有 PostgreSQL 能跑的 SQL，MySQL 部署时静默报错」。
 * <p>
 * 业务库支持 MySQL 后（见 docs/adr/0012），PG 专有写法必须包在
 * {@code <choose>} 的 {@code <otherwise>}（或显式的 {@code _databaseId == 'postgresql'} 分支）里。
 * 本测试把所有 mapper XML 的 PG 分支内容摘掉，剩下的文本（两方言共用部分 + MySQL 分支）
 * 不应再出现任何 PG 专有 token——这是唯一能在没有 MySQL 实例的机器上跑起来的方言防线。
 * <p>
 * 附带一条只有靠读 SQL 文本才能发现的规则：会话摘要那条「水位只增不减」的条件 upsert，
 * MySQL 分支必须把 {@code covered_until_message_id} 放在**最后一个**赋值，否则前面的
 * {@code IF()} 会读到已更新的水位、摘要能被改旧（顺序写反不报错，是静默的数据错误）。
 */
class MapperSqlDialectLintTest {

    /**
     * 与 {@link #mapperXmls()} 下标对齐的资源名，失败时报文件名而不是「某个 XML」。
     */
    private static final List<String> NAMES = new ArrayList<>();

    /**
     * PG 专有写法：出现在方言分支之外就是 bug。
     */
    private static final List<Pattern> PG_ONLY = List.of(
            Pattern.compile("::\\w"),                          // 类型强转 ::jsonb / ::date
            Pattern.compile("(?i)\\bilike\\b"),
            Pattern.compile("(?i)\\bon\\s+conflict\\b"),
            Pattern.compile("(?i)\\bcardinality\\s*\\("),
            Pattern.compile("(?i)\\bunnest\\s*\\("),
            Pattern.compile("(?i)\\binterval\\s*'"),
            Pattern.compile("&amp;&amp;"),                      // 数组重叠 &&
            Pattern.compile("@&gt;"),                          // 数组包含 @>
            Pattern.compile("(?i)\\btext\\[\\]"),
            Pattern.compile("(?i)\\bdistinct\\s+on\\s*\\("),
            Pattern.compile("(?i)\\bto_tsvector\\b"));

    /**
     * PG 分支（含等价写法）：整段摘掉后再检查剩余文本。
     */
    private static final Pattern PG_BRANCH = Pattern.compile(
            "<otherwise>.*?</otherwise>|<when\\s+test=\"_databaseId\\s*==\\s*'postgresql'\\s*\">.*?</when>",
            Pattern.DOTALL);

    @Test
    void noPostgresOnlySqlOutsideDialectBranch() throws Exception {
        List<String> offenders = new ArrayList<>();
        List<String> xmls = mapperXmls();
        for (int i = 0; i < xmls.size(); i++) {
            String shared = PG_BRANCH.matcher(xmls.get(i)).replaceAll(" ");
            // XML 注释（<!-- … -->）与 SQL 行注释（-- …）里提到 PG 写法是在解释差异，不算违规
            shared = shared.replaceAll("(?s)<!--.*?-->", " ").replaceAll("(?m)--.*$", " ");
            for (Pattern pattern : PG_ONLY) {
                Matcher matcher = pattern.matcher(shared);
                if (matcher.find()) {
                    offenders.add(NAMES.get(i) + " 命中 " + matcher.group());
                }
            }
        }
        assertThat(offenders)
                .as("mapper XML 中方言分支之外不得出现 PostgreSQL 专有 SQL（MySQL 部署会直接报错）")
                .isEmpty();
    }

    @Test
    void conversationSummaryMysqlUpsertAssignsWatermarkLast() throws Exception {
        String xml = mapperXmls().stream()
                .filter(s -> s.contains("upsertIfNewer"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 ConversationSummaryMapper.xml"));
        String mysqlBranch = extract(xml, "<when test=\"_databaseId == 'mysql'\">");

        int watermark = mysqlBranch.lastIndexOf("covered_until_message_id =");
        assertThat(watermark).as("MySQL 分支必须更新水位列").isPositive();
        // 水位之后的赋值只能是它自己：任何别的列排在它后面都会读到新水位
        String afterWatermark = mysqlBranch.substring(watermark).replaceAll("covered_until_message_id\\s*=[^,\n]+", "");
        assertThat(afterWatermark)
                .as("水位列必须是 ON DUPLICATE KEY UPDATE 的最后一个赋值，否则前面的 IF() 读到的是新水位")
                .doesNotContain("=");
    }

    /**
     * 取 {@code <when test="_databaseId == 'mysql'">…</when>} 的内容（假定该标签只出现一次）。
     */
    private static String extract(String xml, String openTag) {
        int start = xml.indexOf(openTag);
        assertThat(start).as("缺少 MySQL 方言分支：%s", openTag).isPositive();
        int end = xml.indexOf("</when>", start);
        return xml.substring(start + openTag.length(), end);
    }

    private static List<String> mapperXmls() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:mapper/**/*.xml");
        List<String> xmls = new ArrayList<>();
        NAMES.clear();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                xmls.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                NAMES.add(resource.getFilename());
            }
        }
        assertThat(xmls).as("应扫到各模块的 mapper XML").hasSizeGreaterThan(20);
        return xmls;
    }
}
