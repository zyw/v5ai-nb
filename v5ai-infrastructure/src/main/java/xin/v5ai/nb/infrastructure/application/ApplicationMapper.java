package xin.v5ai.nb.infrastructure.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApplicationMapper extends BaseMapper<ApplicationEntity>, ApplicationGateway {
    @Override
    default void insertApplication(ApplicationEntity entity) {
        insert(entity);
    }

    @Override
    default ApplicationEntity selectByAppKey(String appKey) {
        return selectOne(new LambdaQueryWrapper<ApplicationEntity>().eq(ApplicationEntity::getAppKey, appKey));
    }

    @Override
    default List<ApplicationEntity> selectApplications() {
        return selectList(null);
    }

    @Override
    default void updateApplication(ApplicationEntity entity) {
        updateById(entity);
    }

    @Override
    default void disable(String appKey) {
        update(new LambdaUpdateWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getAppKey, appKey)
                .set(ApplicationEntity::getStatus, "DISABLED"));
    }

    @Override
    default void updatePublished(String appKey, String status, Long publishedVersion) {
        update(new LambdaUpdateWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getAppKey, appKey)
                .set(ApplicationEntity::getStatus, status)
                .set(ApplicationEntity::getPublishedVersion, publishedVersion));
    }
}
