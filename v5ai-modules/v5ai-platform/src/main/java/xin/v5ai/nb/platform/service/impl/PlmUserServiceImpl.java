package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.constant.CacheNames;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.core.utils.*;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.api.UserService;
import xin.v5ai.nb.platform.api.domain.UserDTO;
import xin.v5ai.nb.platform.domain.PlmUser;
import xin.v5ai.nb.platform.domain.PlmUserRole;
import xin.v5ai.nb.platform.domain.bo.PlmUserBo;
import xin.v5ai.nb.platform.domain.vo.PlmRoleVo;
import xin.v5ai.nb.platform.domain.vo.PlmUserVo;
import xin.v5ai.nb.platform.mapper.PlmRoleMapper;
import xin.v5ai.nb.platform.mapper.PlmUserMapper;
import xin.v5ai.nb.platform.mapper.PlmUserRoleMapper;
import xin.v5ai.nb.platform.service.IPlmUserService;

import java.util.*;

/**
 * 用户信息Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PlmUserServiceImpl implements IPlmUserService, UserService {

    private final PlmUserMapper baseMapper;
    private final PlmRoleMapper roleMapper;
    private final PlmUserRoleMapper userRoleMapper;

    /**
     * 分页查询用户列表。
     *
     * @param user      用户筛选条件
     * @param pageQuery 分页参数
     * @return 用户分页结果
     */
    @Override
    public PageResult<PlmUserVo> selectPageUserList(PlmUserBo user, PageQuery pageQuery) {
        Page<PlmUserVo> page = baseMapper.selectPageUserList(pageQuery.build(), this.buildQueryWrapper(user));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 构造用户列表查询条件。
     *
     * @param user 用户筛选条件
     * @return 叠加部门、状态、时间区间等条件的查询包装器
     */
    private Wrapper<PlmUser> buildQueryWrapper(PlmUserBo user) {
        Map<String, Object> params = user.getParams();
        LambdaQueryWrapper<PlmUser> wrapper = QueryBuilder.lambda(PlmUser.class)
                .eq(PlmUser::getDelFlag, SystemConstants.NORMAL)
                .eqIfPresent(PlmUser::getId, user.getId())
                .in(StringUtils.isNotBlank(user.getUserIds()), PlmUser::getId, StringUtils.splitTo(user.getUserIds(), Convert::toLong))
                .likeIfText(PlmUser::getUserName, user.getUserName())
                .likeIfText(PlmUser::getNickName, user.getNickName())
                .eqIfText(PlmUser::getStatus, user.getStatus())
                .likeIfText(PlmUser::getPhoneNumber, user.getPhoneNumber())
                .betweenParams(PlmUser::getCreatedAt, params, "beginTime", "endTime")
                .orderByAsc(PlmUser::getId)
                .build();
        if (StringUtils.isNotBlank(user.getExcludeUserIds())) {
            wrapper.notIn(PlmUser::getId, StringUtils.splitTo(user.getExcludeUserIds(), Convert::toLong));
        }
        return wrapper;
    }

    /**
     * 根据条件分页查询已分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public PageResult<PlmUserVo> selectAllocatedList(PlmUserBo user, PageQuery pageQuery) {
        Page<PlmUserVo> page = baseMapper.selectAllocatedList(pageQuery.build(), user);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public PageResult<PlmUserVo> selectUnallocatedList(PlmUserBo user, PageQuery pageQuery) {
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(user.getRoleId());
        Page<PlmUserVo> page = baseMapper.selectUnallocatedList(pageQuery.build(), user, userIds);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    @Override
    public PlmUserVo selectUserByUserName(String userName) {
        return baseMapper.lambda().eq(PlmUser::getUserName, userName).voOne();
    }

    /**
     * 通过手机号查询用户
     *
     * @param phoneNumber 手机号
     * @return 用户对象信息
     */
    @Override
    public PlmUserVo selectUserByPhoneNumber(String phoneNumber) {
        return baseMapper.lambda().eq(PlmUser::getPhoneNumber, phoneNumber).voOne();
    }

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    @Override
    public PlmUserVo selectUserById(Long userId) {
        PlmUserVo user = baseMapper.selectVoById(userId);
        if (ObjectUtil.isNull(user)) {
            return user;
        }
        user.setRoles(roleMapper.selectRolesByUserId(user.getId()));
        return user;
    }

    /**
     * 通过用户ID串查询用户
     *
     * @param userIds 用户ID串
     * @param deptId  部门id
     * @return 用户列表信息
     */
    @Override
    public List<PlmUserVo> selectUserByIds(Collection<Long> userIds, Long deptId) {
        return baseMapper.selectUserList(baseMapper.lambda()
                .select(PlmUser::getId, PlmUser::getUserName, PlmUser::getNickName)
                .eq(PlmUser::getStatus, SystemConstants.NORMAL)
                .inIfNotEmpty(PlmUser::getId, userIds)
                .build());
    }

    /**
     * 查询用户所属角色组
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public String selectUserRoleGroup(Long userId) {
        List<PlmRoleVo> list = roleMapper.selectRolesByUserId(userId);
        if (CollUtil.isEmpty(list)) {
            return StringUtils.EMPTY;
        }
        return StreamUtils.join(list, PlmRoleVo::getRoleName);
    }

    /**
     * 校验用户账号是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkUserNameUnique(PlmUserBo user) {
        boolean exist = baseMapper.lambda()
                .eq(PlmUser::getUserName, user.getUserName())
                .neIfPresent(PlmUser::getId, user.getId())
                .exists();
        return !exist;
    }

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     */
    @Override
    public boolean checkPhoneUnique(PlmUserBo user) {
        boolean exist = baseMapper.lambda()
                .eq(PlmUser::getPhoneNumber, user.getPhoneNumber())
                .neIfPresent(PlmUser::getId, user.getId())
                .exists();
        return !exist;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     */
    @Override
    public boolean checkEmailUnique(PlmUserBo user) {
        boolean exist = baseMapper.lambda()
                .eq(PlmUser::getEmail, user.getEmail())
                .neIfPresent(PlmUser::getId, user.getId())
                .exists();
        return !exist;
    }

    /**
     * 校验用户是否允许操作
     *
     * @param userId 用户ID
     */
    @Override
    public void checkUserAllowed(Long userId) {
        if (ObjectUtil.isNotNull(userId) && LoginHelper.isSuperAdmin(userId)) {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    /**
     * 校验用户是否有数据权限
     *
     * @param userId 用户id
     */
    @Override
    public void checkUserDataScope(Long userId) {
        if (ObjectUtil.isNull(userId)) {
            return;
        }
        if (LoginHelper.isSuperAdmin()) {
            return;
        }
        if (baseMapper.countUserById(userId) == 0) {
            throw new ServiceException("没有权限访问用户数据！");
        }
    }

    /**
     * 新增保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertUser(PlmUserBo user) {
        PlmUser plmUser = MapstructUtils.convert(user, PlmUser.class);
        // 新增用户信息
        int rows = baseMapper.insert(plmUser);
        user.setId(plmUser.getId());

        // 新增用户与角色管理
        insertUserRole(user, false);
        return rows;
    }

    /**
     * 注册用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean registerUser(PlmUserBo user) {
        user.setCreatedBy(0L);
        user.setUpdatedBy(0L);
        PlmUser sysUser = MapstructUtils.convert(user, PlmUser.class);
        return baseMapper.insert(sysUser) > 0;
    }

    /**
     * 修改保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.id")
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(PlmUserBo user) {
        // 新增用户与角色管理
        insertUserRole(user, true);

        PlmUser sysUser = MapstructUtils.convert(user, PlmUser.class);
        // 防止错误更新后导致的数据误删除
        int flag = baseMapper.updateById(sysUser);
        if (flag < 1) {
            throw new ServiceException("修改用户{}信息失败", user.getUserName());
        }
        return flag;
    }

    /**
     * 用户授权角色
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUserAuth(Long userId, Long[] roleIds) {
        insertUserRole(userId, roleIds, true);
    }

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 账号状态
     * @return 结果
     */
    @Override
    public int updateUserStatus(Long userId, String status) {
        return baseMapper.lambda()
                .set(PlmUser::getStatus, status)
                .eq(PlmUser::getId, userId)
                .updateCount();
    }

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.id")
    @Override
    public int updateUserProfile(PlmUserBo user) {
        return baseMapper.lambda()
                .setIfPresent(PlmUser::getNickName, user.getNickName())
                .setIfPresent(PlmUser::getAvatar, user.getAvatar())
                .setIfPresent(PlmUser::getPhoneNumber, user.getPhoneNumber())
                .setIfPresent(PlmUser::getEmail, user.getEmail())
                .setIfPresent(PlmUser::getGender, user.getGender())
                .eq(PlmUser::getId, user.getId())
                .updateCount();
    }

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 密码
     * @return 结果
     */
    @Override
    public int resetUserPwd(Long userId, String password) {
        return baseMapper.lambda()
                .set(PlmUser::getPassword, password)
                .eq(PlmUser::getId, userId)
                .updateCount();
    }

    /**
     * 新增用户角色信息
     *
     * @param user  用户对象
     * @param clear 清除已存在的关联数据
     */
    private void insertUserRole(PlmUserBo user, boolean clear) {
        this.insertUserRole(user.getId(), user.getRoleIds(), clear);
    }

    /**
     * 新增用户角色信息
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     * @param clear   清除已存在的关联数据
     */
    private void insertUserRole(Long userId, Long[] roleIds, boolean clear) {
        if (ArrayUtil.isEmpty(roleIds)) {
            return;
        }

        List<Long> roleList = new ArrayList<>(Arrays.asList(roleIds));

        // 非超级管理员，禁止包含超级管理员角色
        if (!LoginHelper.isSuperAdmin(userId)) {
            roleList.remove(SystemConstants.SUPER_ADMIN_ROLE_ID);
        }

        // 移除超管角色后若无剩余角色，说明仅选了超管角色且不允许分配，显式报错
        if (roleList.isEmpty()) {
            throw new ServiceException("不允许为普通用户分配超级管理员角色，请至少选择一个其他角色");
        }

        // 校验是否有权限访问这些角色（含数据权限控制）
        if (roleMapper.selectRoleCount(roleList) != roleList.size()) {
            throw new ServiceException("没有权限访问角色的数据");
        }

        // 是否清除原有绑定
        if (clear) {
            userRoleMapper.lambda().eq(PlmUserRole::getUserId, userId).delete();
        }

        // 批量插入用户-角色关联
        List<PlmUserRole> list = StreamUtils.toList(roleList,
                roleId -> {
                    PlmUserRole ur = new PlmUserRole();
                    ur.setUserId(userId);
                    ur.setRoleId(roleId);
                    return ur;
                });
        userRoleMapper.insertBatch(list);
    }

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserById(Long userId) {
        // 删除用户与角色关联
        userRoleMapper.lambda().eq(PlmUserRole::getUserId, userId).delete();

        // 防止更新失败导致的数据删除
        int flag = baseMapper.deleteById(userId);
        if (flag < 1) {
            throw new ServiceException("删除用户失败!");
        }
        return flag;
    }

    /**
     * 批量删除用户信息
     *
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserByIds(Long[] userIds) {
        for (Long userId : userIds) {
            checkUserAllowed(userId);
            checkUserDataScope(userId);
        }
        List<Long> ids = List.of(userIds);
        // 删除用户与角色关联
        userRoleMapper.lambda().in(PlmUserRole::getUserId, ids).delete();

        // 防止更新失败导致的数据删除
        int flag = baseMapper.deleteByIds(ids);
        if (flag < 1) {
            throw new ServiceException("删除用户失败!");
        }
        return flag;
    }

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    @Cacheable(cacheNames = CacheNames.SYS_USER_NAME, key = "#userId")
    @Override
    public String selectUserNameById(Long userId) {
        PlmUser sysUser = baseMapper.lambda()
                .select(PlmUser::getUserName)
                .eq(PlmUser::getId, userId)
                .one();
        return ObjectUtils.notNullGetter(sysUser, PlmUser::getUserName);
    }

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @Override
    @Cacheable(cacheNames = CacheNames.SYS_NICKNAME, key = "#userId")
    public String selectNicknameById(Long userId) {
        PlmUser sysUser = baseMapper.lambda()
                .select(PlmUser::getNickName)
                .eq(PlmUser::getId, userId)
                .one();
        return ObjectUtils.notNullGetter(sysUser, PlmUser::getNickName);
    }

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userIds 用户ID 多个用逗号隔开
     * @return 用户昵称
     */
    @Override
    public String selectNicknameByIds(String userIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(userIds, Convert::toLong)) {
            String nickname = SpringUtils.getAopProxy(this).selectNicknameById(id);
            if (StringUtils.isNotBlank(nickname)) {
                list.add(nickname);
            }
        }
        return StringUtils.joinComma(list);
    }

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    @Override
    public String selectPhonenumberById(Long userId) {
        PlmUser sysUser = baseMapper.lambda()
                .select(PlmUser::getPhoneNumber)
                .eq(PlmUser::getId, userId)
                .one();
        return ObjectUtils.notNullGetter(sysUser, PlmUser::getPhoneNumber);
    }

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    @Override
    public String selectEmailById(Long userId) {
        PlmUser sysUser = baseMapper.lambda()
                .select(PlmUser::getEmail)
                .eq(PlmUser::getId, userId)
                .one();
        return ObjectUtils.notNullGetter(sysUser, PlmUser::getEmail);
    }

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户id
     * @return 用户列表
     */
    @Override
    public UserDTO selectById(Long userId) {
        PlmUserVo vo = baseMapper.lambda()
                .select(PlmUser::getId, PlmUser::getUserName,
                        PlmUser::getNickName, PlmUser::getUserType, PlmUser::getEmail,
                        PlmUser::getPhoneNumber, PlmUser::getGender, PlmUser::getStatus,
                        PlmUser::getCreatedAt)
                .eq(PlmUser::getStatus, SystemConstants.NORMAL)
                .eq(PlmUser::getId, userId)
                .voOne();
        return BeanUtil.copyProperties(vo, UserDTO.class);
    }

    /**
     * 通过用户ID查询用户列表
     *
     * @param userIds 用户ids
     * @return 用户列表
     */
    @Override
    public List<UserDTO> selectListByIds(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return List.of();
        }
        List<PlmUserVo> list = baseMapper.lambda()
                .select(PlmUser::getId, PlmUser::getUserName,
                        PlmUser::getNickName, PlmUser::getUserType, PlmUser::getEmail,
                        PlmUser::getPhoneNumber, PlmUser::getGender, PlmUser::getStatus,
                        PlmUser::getCreatedAt)
                .eq(PlmUser::getStatus, SystemConstants.NORMAL)
                .in(PlmUser::getId, userIds)
                .voList();
        return BeanUtil.copyToList(list, UserDTO.class);
    }

    /**
     * 通过角色ID查询用户ID
     *
     * @param roleIds 角色ids
     * @return 用户ids
     */
    @Override
    public List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }
        List<PlmUserRole> userRoles = userRoleMapper.lambda().in(PlmUserRole::getRoleId, roleIds).list();
        return StreamUtils.toList(userRoles, PlmUserRole::getUserId);
    }

    /**
     * 通过角色ID查询用户
     *
     * @param roleIds 角色ids
     * @return 用户
     */
    @Override
    public List<UserDTO> selectUsersByRoleIds(Collection<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }

        // 通过角色ID获取用户角色信息
        List<PlmUserRole> userRoles = userRoleMapper.lambda().in(PlmUserRole::getRoleId, roleIds).list();

        // 获取用户ID列表
        Set<Long> userIds = StreamUtils.toSet(userRoles, PlmUserRole::getUserId);

        return this.selectListByIds(new ArrayList<>(userIds));
    }

    /**
     * 根据用户 ID 列表查询用户昵称映射关系
     *
     * @param userIds 用户 ID 列表
     * @return Map，其中 key 为用户 ID，value 为对应的用户昵称
     */
    @Override
    public Map<Long, String> selectUserNicksByIds(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<PlmUser> list = baseMapper.lambda()
                .select(PlmUser::getId, PlmUser::getNickName)
                .in(PlmUser::getId, userIds)
                .list();
        return StreamUtils.toMap(list, PlmUser::getId, PlmUser::getNickName);
    }
}
