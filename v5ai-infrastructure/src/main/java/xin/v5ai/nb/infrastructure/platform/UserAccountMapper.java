package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.infrastructure.auth.UserEntity;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserEntity> {
}
