//package xin.v5ai.nb.runtime.core.mapper;
//
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import org.apache.ibatis.annotations.Mapper;
//import xin.v5ai.nb.runtime.core.domain.RuntimeRunEventEntity;
//import xin.v5ai.nb.runtime.core.gateway.RuntimeRunEventGateway;
//
//@Mapper
//public interface RuntimeRunEventMapper extends BaseMapper<RuntimeRunEventEntity>, RuntimeRunEventGateway {
//    @Override
//    default void insertRunEvent(RuntimeRunEventEntity entity) {
//        insert(entity);
//    }
//}
