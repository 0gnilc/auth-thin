package com.gnilc.auth.authz.rbac.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gnilc.common.base.Preconditions;
import com.gnilc.auth.authz.rbac.dao.PermissionDao;
import com.gnilc.auth.authz.rbac.entity.bo.PermissionBo;
import com.gnilc.auth.authz.rbac.entity.dto.PermissionDto;
import com.gnilc.auth.authz.rbac.entity.dto.PermissionQueryDto;
import com.gnilc.auth.authz.rbac.entity.vo.PermissionVo;
import com.gnilc.auth.authz.rbac.event.AuthorizationEvent;
import com.gnilc.auth.authz.rbac.service.PermissionService;
import com.gnilc.auth.authz.rbac.service.RolePermissionService;
import com.gnilc.auth.authz.rbac.service.UserRoleService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;

/** 管理权限目标与公开访问标记，保护内置权限并同步清理角色绑定。 */
@Service("permissionService")
public class PermissionServiceImpl extends ServiceImpl<PermissionDao, PermissionBo> implements PermissionService {
    private final ApplicationEventPublisher eventPublisher;
    private final UserRoleService userRoleService;
    private final RolePermissionService rolePermissionService;

    public PermissionServiceImpl(ApplicationEventPublisher eventPublisher,
                                 UserRoleService userRoleService,
                                 RolePermissionService rolePermissionService) {
        this.eventPublisher = eventPublisher;
        this.userRoleService = userRoleService;
        this.rolePermissionService = rolePermissionService;
    }

    @Transactional
    @Override
    public void createPermission(PermissionDto dto) {
        validatePermission(dto, false);
        String name = dto.getName();
        String code = dto.getCode();
        String targetIdentifier = dto.getTargetIdentifier();
        String targetQualifier = dto.getTargetQualifier();
        String remark = dto.getRemark();
        Boolean publicAccess = dto.getPublicAccess();
        PermissionBo bo = new PermissionBo();
        bo.setName(name);
        bo.setCode(code);
        bo.setTargetIdentifier(targetIdentifier);
        bo.setTargetQualifier(targetQualifier);
        bo.setRemark(remark);
        bo.setPublicAccess(publicAccess);
        bo.setBuiltIn(Boolean.FALSE);
        save(bo);

        eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.PERMISSION,
                AuthorizationEvent.Action.CREATE,
                bo.getId()));
    }

    @Transactional
    @Override
    public void updatePermission(PermissionDto dto) {
        PermissionBo bo = validatePermission(dto, true);
        Long permissionId = dto.getId();
        String name = dto.getName();
        String code = dto.getCode();
        String targetIdentifier = dto.getTargetIdentifier();
        String targetQualifier = dto.getTargetQualifier();
        String remark = dto.getRemark();
        Boolean publicAccess = dto.getPublicAccess();
        bo.setName(name);
        bo.setCode(code);
        bo.setTargetIdentifier(targetIdentifier);
        bo.setTargetQualifier(targetQualifier);
        bo.setRemark(remark);
        bo.setPublicAccess(Boolean.TRUE.equals(publicAccess));
        updateById(bo);

        eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.PERMISSION,
                AuthorizationEvent.Action.UPDATE, permissionId));
    }

    /** 禁止删除内置权限；清理角色绑定并释放唯一编码，再逻辑删除并发布权限变化事件。 */
    @Transactional
    @Override
    public void removePermission(Long id) {
        Preconditions.checkArgument(id != null, "请选择权限。");
        PermissionBo bo = getById(id);
        Preconditions.checkCondition(bo != null, "权限已不存在，请刷新后重试。");
        Preconditions.checkCondition(!Boolean.TRUE.equals(bo.getBuiltIn()),
                "内置权限不能删除。");
        bo.setCode(bo.getCode() + "_del_" + id);
        updateById(bo);
        rolePermissionService.removeByPermissionId(id);
        removeById(id);

        eventPublisher.publishEvent(AuthorizationEvent.of(
                AuthorizationEvent.Type.PERMISSION,
                AuthorizationEvent.Action.DELETE, id));
    }

    @Override
    public List<PermissionVo> getPermissions(PermissionQueryDto dto) {
        String code = dto.getCode();
        String name = dto.getName();
        String targetIdentifier = dto.getTargetIdentifier();
        String targetQualifier = dto.getTargetQualifier();
        Boolean publicAccess = dto.getPublicAccess();
        return lambdaQuery()
                .eq(StringUtils.isNotBlank(code), PermissionBo::getCode, code)
                .like(StringUtils.isNotBlank(name), PermissionBo::getName, name)
                .like(StringUtils.isNotBlank(targetIdentifier), PermissionBo::getTargetIdentifier, targetIdentifier)
                .eq(StringUtils.isNotBlank(targetQualifier), PermissionBo::getTargetQualifier, targetQualifier)
                .eq(publicAccess != null, PermissionBo::getPublicAccess, publicAccess)
                .list()
                .stream()
                .map(bo -> {
                    PermissionVo vo = new PermissionVo();
                    BeanUtils.copyProperties(bo, vo);
                    return vo;
                })
                .toList();
    }

    @Override
    public PermissionBo getPermissionByCode(String code) {
        if (StringUtils.isNotBlank(code)) {
            return lambdaQuery()
                    .eq(PermissionBo::getCode, code)
                    .one();
        }
        return null;
    }

    @Override
    public List<PermissionBo> getPermissions(Long userId) {
        List<Long> roleIds = userRoleService.getRoleIds(userId);
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionService.getPermissionIds(roleIds);
        if (CollectionUtils.isEmpty(permissionIds)) {
            return List.of();
        }
        return getPermissions(permissionIds);
    }

    @Override
    public List<PermissionBo> getPermissions(List<Long> ids) {
        Preconditions.checkArgument(ids != null, "At least one permission must be selected.");
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }
        return listByIds(new LinkedHashSet<>(ids));
    }

    private PermissionBo validatePermission(PermissionDto dto, boolean update) {
        Preconditions.checkArgument(dto != null, "权限信息不能为空。");
        PermissionBo permission = null;
        if (update) {
            Preconditions.checkArgument(dto.getId() != null,
                    "请选择权限。");
            permission = getById(dto.getId());
            Preconditions.checkCondition(permission != null, "权限已不存在，请刷新后重试。");
            Preconditions.checkCondition(!Boolean.TRUE.equals(permission.getBuiltIn()),
                    "内置权限不能修改。");
        }
        String code = dto.getCode();
        String name = dto.getName();
        String targetIdentifier = dto.getTargetIdentifier();
        String targetQualifier = dto.getTargetQualifier();
        String remark = dto.getRemark();
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "权限名称不能为空。");
        Preconditions.checkArgument(StringUtils.isNotBlank(code), "权限编码不能为空。");
        Preconditions.checkArgument(StringUtils.isNotBlank(targetIdentifier),
                "访问目标标识不能为空。");
        Preconditions.checkArgument(code.codePointCount(0, code.length()) <= 255,
                "权限编码不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(name.codePointCount(0, name.length()) <= 255,
                "权限名称不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(targetIdentifier.codePointCount(0, targetIdentifier.length()) <= 500,
                "访问目标标识不能超过 %s 个字符。".formatted(500));
        Preconditions.checkArgument(targetQualifier == null
                        || targetQualifier.codePointCount(0, targetQualifier.length()) <= 100,
                "目标限定符不能超过 %s 个字符。".formatted(100));
        Preconditions.checkArgument(remark == null || remark.codePointCount(0, remark.length()) <= 500,
                "权限描述不能超过 %s 个字符。".formatted(500));
        if (!update || !code.equals(permission.getCode())) {
            Preconditions.checkArgument(getPermissionByCode(code) == null,
                    "已存在使用该编码的权限。");
        }
        return permission;
    }
}
