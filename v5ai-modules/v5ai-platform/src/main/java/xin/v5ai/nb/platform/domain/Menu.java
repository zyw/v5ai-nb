package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import xin.v5ai.nb.common.core.constant.Constants;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.utils.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("v5ai_menu")
public class Menu implements Serializable {

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

    /**
     * 子菜单
     */
    @TableField(exist = false)
    private List<Menu> children = new ArrayList<>();



    /**
     * 获取路由地址
     */
    public String getRouterPath() {
        String routerPath = this.path;
        // 内链打开外网方式
//        if (!Constants.TOP_PARENT_ID.equals(getParentId()) && isInnerLink()) {
//            routerPath = innerLinkReplaceEach(routerPath);
//        }
        // 非外链并且是一级目录（类型为目录）
        if (Constants.TOP_PARENT_ID.equals(getParentId()) && SystemConstants.TYPE_DIR.equals(getType())) {
            routerPath = "/" + this.path;
        }
        // 非外链并且是一级目录（类型为菜单）
        else if (isMenuFrame()) {
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * 是否为菜单内部跳转
     */
    public boolean isMenuFrame() {
        return Constants.TOP_PARENT_ID.equals(getParentId()) && SystemConstants.TYPE_MENU.equals(type);
    }

    /**
     * 获取组件信息
     */
    public String getComponentInfo() {
        String component = SystemConstants.LAYOUT;
//        if (StringUtils.isNotEmpty(this.component) && !isMenuFrame()) {
//            component = this.component;
//        } else if (StringUtils.isEmpty(this.component) && !Constants.TOP_PARENT_ID.equals(getParentId()) && isInnerLink()) {
//            component = SystemConstants.INNER_LINK;
//        } else if (StringUtils.isEmpty(this.component) && isParentView()) {
//            component = SystemConstants.PARENT_VIEW;
//        }
        return component;
    }
}
