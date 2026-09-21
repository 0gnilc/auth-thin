package com.gnilc.auth.authz.rbac.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gnilc.common.base.Preconditions;
import com.gnilc.common.utils.PageResult;
import com.gnilc.auth.authz.rbac.dao.RoleDao;
import com.gnilc.auth.authz.rbac.entity.bo.RoleBo;
import com.gnilc.auth.authz.rbac.entity.dto.RoleDto;
import com.gnilc.auth.authz.rbac.entity.dto.RolePageDto;
import com.gnilc.auth.authz.rbac.entity.dto.RoleQueryDto;
import com.gnilc.auth.authz.rbac.entity.vo.RoleVo;
import com.gnilc.auth.authz.rbac.event.AuthorizationEvent;
import com.gnilc.auth.authz.rbac.service.RoleMenuService;
import com.gnilc.auth.authz.rbac.service.RolePermissionService;
import com.gnilc.auth.authz.rbac.service.RoleService;
import com.gnilc.auth.authz.rbac.service.UserRoleService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

/** 管理自定义角色生命周期，保护内置角色并在删除时清理所属绑定。 */
@Service("roleService")
public class RoleServiceImpl extends ServiceImpl<RoleDao, RoleBo> implements RoleService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRoleService userRoleService;
    private final RolePermissionService rolePermissionService;
    private final RoleMenuService roleMenuService;

    public RoleServiceImpl(ApplicationEventPublisher eventPublisher,
                           UserRoleService userRoleService,
                           @Lazy RolePermissionService rolePermissionService,
                           @Lazy RoleMenuService roleMenuService) {
        this.eventPublisher = eventPublisher;
        this.userRoleService = userRoleService;
        this.rolePermissionService = rolePermissionService;
        this.roleMenuService = roleMenuService;
    }

    @Override
    public PageResult<RoleVo> getRolePage(RolePageDto dto) {
        String code = dto.getCode();
        String name = dto.getName();
        IPage<RoleBo> page = lambdaQuery()
                .eq(StringUtils.isNotBlank(code), RoleBo::getCode, code)
                .like(StringUtils.isNotBlank(name), RoleBo::getName, name)
                .page(dto.getPage());
        List<RoleVo> vos = page.getRecords().stream()
                .map(bo -> {
                    RoleVo vo = new RoleVo();
                    BeanUtils.copyProperties(bo, vo);
                    return vo;
                })
                .toList();
        return PageResult.of(page, vos);
    }

    @Override
    public List<RoleVo> getRoles(RoleQueryDto dto) {
        String code = dto.getCode();
        String name = dto.getName();
        Boolean builtIn = dto.getBuiltIn();
        return lambdaQuery().eq(StringUtils.isNotBlank(code), RoleBo::getCode, code)
                .eq(builtIn != null, RoleBo::getBuiltIn, builtIn)
                .like(StringUtils.isNotBlank(name), RoleBo::getName, name)
                .list()
                .stream()
                .map(bo -> {
                    RoleVo vo = new RoleVo();
                    BeanUtils.copyProperties(bo, vo);
                    return vo;
                })
                .toList();
    }

    @Transactional
    @Override
    public void createRole(RoleDto dto) {
        validateRole(dto, false);
        String name = dto.getName();
        String code = dto.getCode();
        String remark = dto.getRemark();
        RoleBo bo = new RoleBo();
        bo.setName(name);
        bo.setCode(code);
        bo.setRemark(remark);
        bo.setBuiltIn(Boolean.FALSE);
        save(bo);

        eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.ROLE,
                AuthorizationEvent.Action.CREATE,
                bo.getId()));
    }

    @Override
    public RoleBo getRoleByCode(String code) {
        if (StringUtils.isNotBlank(code)) {
            return lambdaQuery()
                    .eq(RoleBo::getCode, code)
                    .one();
        }
        return null;
    }

    @Transactional
    @Override
    public void updateRole(RoleDto dto) {
        RoleBo bo = validateRole(dto, true);
        Long roleId = dto.getId();
        String name = dto.getName();
        String code = dto.getCode();
        String remark = dto.getRemark();
        bo.setName(name);
        bo.setCode(code);
        bo.setRemark(remark);
        updateById(bo);

        eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.ROLE,
                AuthorizationEvent.Action.UPDATE,
                roleId));
    }

    /** 禁止删除内置角色；先释放唯一编码并清理权限、菜单及用户绑定，再逻辑删除角色。 */
    @Transactional
    @Override
    public void removeRole(Long id) {
        Preconditions.checkArgument(id != null, "请选择角色。");
        RoleBo bo = getById(id);
        Preconditions.checkCondition(bo != null, "角色已不存在，请刷新后重试。");
        Preconditions.checkCondition(!Boolean.TRUE.equals(bo.getBuiltIn()), "内置角色不能删除。");
        bo.setCode(bo.getCode() + "_del_" + id);
        updateById(bo);
        rolePermissionService.removeByRoleId(id);
        roleMenuService.removeByRoleId(id);
        userRoleService.removeByRoleId(id);
        removeById(id);

        eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.ROLE,
                AuthorizationEvent.Action.DELETE,
                id));
    }

    @Override
    public List<RoleBo> getRoles(Long userId) {
        Preconditions.checkArgument(userId != null, "A user must be selected.");
        List<Long> roleIds = userRoleService.getRoleIds(userId);
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        return listByIds(roleIds);
    }

    private RoleBo validateRole(RoleDto dto, boolean update) {
        Preconditions.checkArgument(dto != null, "角色信息不能为空。");
        RoleBo role = null;
        if (update) {
            Preconditions.checkArgument(dto.getId() != null, "请选择角色。");
            role = getById(dto.getId());
            Preconditions.checkCondition(role != null, "角色已不存在，请刷新后重试。");
            Preconditions.checkCondition(!Boolean.TRUE.equals(role.getBuiltIn()),
                    "内置角色不能修改。");
        }
        String code = dto.getCode();
        String name = dto.getName();
        String remark = dto.getRemark();
        Preconditions.checkArgument(StringUtils.isNotBlank(code), "角色编码不能为空。");
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "角色名称不能为空。");
        Preconditions.checkArgument(code.codePointCount(0, code.length()) <= 255,
                "角色编码不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(name.codePointCount(0, name.length()) <= 255,
                "角色名称不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(remark == null || remark.codePointCount(0, remark.length()) <= 500,
                "角色描述不能超过 %s 个字符。".formatted(500));
        if (!update || !code.equals(role.getCode())) {
            Preconditions.checkArgument(getRoleByCode(code) == null, "已存在使用该编码的角色。");
        }
        return role;
    }

}
