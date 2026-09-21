package com.gnilc.auth.authz.rbac.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gnilc.common.base.Preconditions;
import com.gnilc.auth.authz.rbac.dao.RolePermissionDao;
import com.gnilc.auth.authz.rbac.entity.bo.RoleBo;
import com.gnilc.auth.authz.rbac.entity.bo.PermissionBo;
import com.gnilc.auth.authz.rbac.entity.bo.RolePermissionBo;
import com.gnilc.auth.authz.rbac.entity.dto.RolePermissionDto;
import com.gnilc.auth.authz.rbac.event.AuthorizationEvent;
import com.gnilc.auth.authz.rbac.service.RolePermissionService;
import com.gnilc.auth.authz.rbac.service.PermissionService;
import com.gnilc.auth.authz.rbac.service.RoleService;
import com.google.common.collect.Sets;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 校验权限选择后替换角色权限集合，内置角色的固定授权不允许修改。 */
@Service("rolePermissionService")
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionDao, RolePermissionBo>
        implements RolePermissionService {

    private final ApplicationEventPublisher eventPublisher;
    private final PermissionService permissionService;
    private final RoleService roleService;

    public RolePermissionServiceImpl(ApplicationEventPublisher eventPublisher,
                                     @Lazy PermissionService permissionService,
                                     RoleService roleService) {
        this.eventPublisher = eventPublisher;
        this.permissionService = permissionService;
        this.roleService = roleService;
    }

    @Override
    public List<Long> getPermissionIds(Long roleId) {
        Preconditions.checkArgument(roleId != null, "请选择角色。");
        return lambdaQuery()
                .select(RolePermissionBo::getPermissionId)
                .eq(RolePermissionBo::getRoleId, roleId)
                .list()
                .stream()
                .map(RolePermissionBo::getPermissionId)
                .distinct()
                .toList();
    }

    @Override
    public List<Long> getPermissionIds(List<Long> roleIds) {
        Preconditions.checkArgument(roleIds != null, "At least one role must be selected.");
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        return lambdaQuery()
                .select(RolePermissionBo::getPermissionId)
                .in(RolePermissionBo::getRoleId, roleIds)
                .list()
                .stream()
                .map(RolePermissionBo::getPermissionId)
                .distinct()
                .toList();
    }

    @Transactional
    @Override
    public void saveRolePermissions(RolePermissionDto dto) {
        Preconditions.checkArgument(dto != null, "角色权限分配信息不能为空。");
        Long roleId = dto.getRoleId();
        List<Long> permissionIds = dto.getPermissionIds();
        Preconditions.checkArgument(roleId != null, "请选择角色。");
        RoleBo role = roleService.getById(roleId);
        Preconditions.checkCondition(role != null, "角色已不存在，请刷新后重试。");
        Preconditions.checkCondition(!Boolean.TRUE.equals(role.getBuiltIn()),
                "内置角色的权限和菜单不能修改。");

        Preconditions.checkArgument(CollectionUtils.isEmpty(permissionIds)
                        || permissionIds.stream().noneMatch(Objects::isNull),
                "请选择权限。");
        Set<Long> newSet = CollectionUtils.isEmpty(permissionIds) ? Set.of() : Sets.newHashSet(permissionIds);
        if (!newSet.isEmpty()) {
            List<PermissionBo> selectedPermissions = permissionService.getPermissions(newSet.stream().toList());
            Preconditions.checkCondition(selectedPermissions.size() == newSet.size(),
                    "权限已不存在，请刷新后重试。");
        }

        Set<Long> oldSet = lambdaQuery()
                .select(RolePermissionBo::getPermissionId)
                .eq(RolePermissionBo::getRoleId, roleId)
                .list()
                .stream()
                .map(RolePermissionBo::getPermissionId)
                .collect(Collectors.toSet());
        Set<Long> removeSet = Sets.difference(oldSet, newSet);
        if (!removeSet.isEmpty()) {
            lambdaUpdate()
                    .eq(RolePermissionBo::getRoleId, roleId)
                    .in(RolePermissionBo::getPermissionId, removeSet)
                    .remove();
        }

        List<RolePermissionBo> bos = Sets.difference(newSet, oldSet)
                .stream()
                .map(permissionId -> {
                    RolePermissionBo bo = new RolePermissionBo();
                    bo.setRoleId(roleId);
                    bo.setPermissionId(permissionId);
                    return bo;
                }).toList();
        if (!bos.isEmpty()) {
            saveBatch(bos);
        }
        eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.ROLE_PERMISSION,
                AuthorizationEvent.Action.REPLACE,
                roleId));
    }

    @Override
    public List<Long> getRoleIds(Long permissionId) {
        if (permissionId == null) {
            return List.of();
        }
        return lambdaQuery()
                .select(RolePermissionBo::getRoleId)
                .eq(RolePermissionBo::getPermissionId, permissionId)
                .list()
                .stream()
                .map(RolePermissionBo::getRoleId)
                .distinct()
                .toList();
    }

    @Transactional
    @Override
    public void removeByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        lambdaUpdate()
                .eq(RolePermissionBo::getRoleId, roleId)
                .remove();
    }

    @Transactional
    @Override
    public void removeByPermissionId(Long permissionId) {
        if (permissionId == null) {
            return;
        }
        List<Long> roleIds = getRoleIds(permissionId);
        lambdaUpdate()
                .eq(RolePermissionBo::getPermissionId, permissionId)
                .remove();
        roleIds.forEach(roleId -> eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.ROLE_PERMISSION,
                AuthorizationEvent.Action.REPLACE,
                roleId)));
    }
}
