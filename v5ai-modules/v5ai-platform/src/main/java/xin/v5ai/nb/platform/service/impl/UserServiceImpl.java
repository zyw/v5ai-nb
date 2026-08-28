//package xin.v5ai.nb.platform.service.impl;
//
//import cn.hutool.core.util.ObjectUtil;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import xin.v5ai.nb.platform.domain.vo.UserVo;
//import xin.v5ai.nb.platform.mapper.RoleMapper;
//import xin.v5ai.nb.platform.mapper.UserMapper;
//import xin.v5ai.nb.platform.service.IUserService;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class UserServiceImpl implements IUserService {
//
//    private final UserMapper mapper;
//    private final RoleMapper roleMapper;
//
//
//    @Override
//    public UserVo selectUserById(Long userId) {
//        UserVo user = mapper.selectVoById(userId);
//        if (ObjectUtil.isNull(user)) {
//            return user;
//        }
//        user.setRoles(roleMapper.selectRolesByUserId(user.getId()));
//        return user;
//    }
//}
