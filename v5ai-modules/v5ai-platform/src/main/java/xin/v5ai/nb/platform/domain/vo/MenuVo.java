package xin.v5ai.nb.platform.domain.vo;

import com.baomidou.mybatisplus.annotation.TableId;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.Menu;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = Menu.class)
public class MenuVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private String type;
    private String routeName;
    private String path;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
