package xin.v5ai.nb.common.satoken.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import xin.v5ai.nb.common.core.factory.YmlPropertySourceFactory;
import xin.v5ai.nb.common.satoken.core.dao.PlusSaTokenDao;
import xin.v5ai.nb.common.satoken.core.service.SaPermissionImpl;
import xin.v5ai.nb.common.satoken.handler.SaTokenExceptionHandler;
import xin.v5ai.nb.common.satoken.token.RefreshTokenService;
import xin.v5ai.nb.common.satoken.token.RefreshTokenServiceImpl;

/**
 * Sa-Token 配置
 *
 * @author Lion Li
 */
@AutoConfiguration
@PropertySource(value = "classpath:common-satoken.yml", factory = YmlPropertySourceFactory.class)
public class SaTokenConfiguration {

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }

    /**
     * 权限接口实现(使用bean注入方便用户替换)
     */
    @Bean
    public StpInterface stpInterface() {
        return new SaPermissionImpl();
    }

    /**
     * 自定义dao层存储
     */
    @Bean
    public SaTokenDao saTokenDao() {
        return new PlusSaTokenDao();
    }

    /**
     * 异常处理器
     */
    @Bean
    public SaTokenExceptionHandler saTokenExceptionHandler() {
        return new SaTokenExceptionHandler();
    }


    /**
     * 刷新Token服务
     */
    @Bean
    public RefreshTokenService refreshTokenService() {
        return new RefreshTokenServiceImpl();
    }

}
