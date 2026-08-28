package xin.v5ai.nb.platform.api.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppUsageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用标识
     */
    String agentKey;
    /**
     * 使用日期
     */
    LocalDate usageDate;
    /**
     * 当日模型调用次数
     */
    long modelCalls;
    /**
     * 当日 Token 用量
     */
    long tokens;
}
