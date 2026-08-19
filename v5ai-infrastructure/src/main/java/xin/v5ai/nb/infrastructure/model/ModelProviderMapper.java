//package xin.v5ai.nb.infrastructure.model;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import org.apache.ibatis.annotations.Mapper;
//
//import java.util.List;
//
//@Mapper
//public interface ModelProviderMapper extends BaseMapper<ModelProviderEntity>, ModelProviderGateway {
//    @Override
//    default void insertProvider(ModelProviderEntity entity) {
//        insert(entity);
//    }
//
//    @Override
//    default void updateProvider(ModelProviderEntity entity) {
//        updateById(entity);
//    }
//
//    @Override
//    default void deleteProvider(String providerKey) {
//        delete(new LambdaQueryWrapper<ModelProviderEntity>()
//                .eq(ModelProviderEntity::getProviderKey, providerKey));
//    }
//
//    @Override
//    default ModelProviderEntity selectProviderById(Long id) {
//        return selectById(id);
//    }
//
//    @Override
//    default ModelProviderEntity selectByProviderKey(String providerKey) {
//        return selectOne(new LambdaQueryWrapper<ModelProviderEntity>()
//                .eq(ModelProviderEntity::getProviderKey, providerKey));
//    }
//
//    @Override
//    default List<ModelProviderEntity> selectProviders() {
//        return selectList(null);
//    }
//}
