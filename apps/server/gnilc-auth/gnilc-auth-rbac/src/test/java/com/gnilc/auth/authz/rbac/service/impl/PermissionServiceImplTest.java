package com.gnilc.auth.authz.rbac.service.impl;

import com.gnilc.auth.authz.rbac.entity.bo.PermissionBo;
import com.gnilc.auth.authz.rbac.entity.dto.PermissionDto;
import com.gnilc.auth.authz.rbac.event.AuthorizationEvent;
import com.gnilc.auth.authz.rbac.service.RolePermissionService;
import com.gnilc.auth.authz.rbac.service.UserRoleService;
import com.gnilc.common.exception.IllegalConditionException;
import com.gnilc.common.exception.InvalidArgumentException;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 验证内置权限保护、原文字符串约束及删除时关系清理，非法输入不得先持久化。 */
class PermissionServiceImplTest {
    @Test
    void createPermissionRejectsMissingInformationWithChineseMessages() {
        PermissionServiceImpl permissions = new PermissionServiceImpl(
                mock(ApplicationEventPublisher.class),
                mock(UserRoleService.class),
                mock(RolePermissionService.class));

        assertThatThrownBy(() -> permissions.createPermission(null))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("权限信息不能为空。");
    }

    @Test
    void updateAndRemoveRejectBuiltInPermissions() {
        PermissionServiceImpl permissions = spy(new PermissionServiceImpl(
                mock(ApplicationEventPublisher.class),
                mock(UserRoleService.class),
                mock(RolePermissionService.class)));
        PermissionBo builtIn = new PermissionBo();
        builtIn.setId(1L);
        builtIn.setBuiltIn(true);
        doReturn(builtIn).when(permissions).getById(1L);
        PermissionDto update = new PermissionDto();
        update.setId(1L);

        assertThatThrownBy(() -> permissions.updatePermission(update))
                .isInstanceOf(IllegalConditionException.class)
                .hasMessage("内置权限不能修改。");
        assertThatThrownBy(() -> permissions.removePermission(1L))
                .isInstanceOf(IllegalConditionException.class)
                .hasMessage("内置权限不能删除。");
    }

    @ParameterizedTest(name = "rejects blank required field {0}")
    @MethodSource("permissionRequiredFields")
    void createPermissionRejectsWhitespaceOnlyRequiredFields(String field, String message) {
        PermissionFixture fixture = permissionFixture();
        PermissionDto dto = validPermission();
        setField(dto, field, "   ");

        assertThatThrownBy(() -> fixture.getService().createPermission(dto))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage(message);
        verifyNoPermissionWrite(fixture);
    }

    @ParameterizedTest(name = "preserves exact permission strings with {0} remark")
    @MethodSource("exactPermissionRemarks")
    void createPermissionPreservesExactStrings(
            String caseName,
            String remark) {
        PermissionFixture fixture = permissionFixture();
        PermissionDto dto = new PermissionDto();
        dto.setCode("  reports:read  ");
        dto.setName("  Read reports  ");
        dto.setTargetIdentifier("  /reports/**  ");
        dto.setTargetQualifier("  GET  ");
        dto.setRemark(remark);
        dto.setPublicAccess(false);
        doReturn(null).when(fixture.getService()).getPermissionByCode(anyString());

        fixture.getService().createPermission(dto);

        ArgumentCaptor<PermissionBo> savedPermission = ArgumentCaptor.forClass(PermissionBo.class);
        verify(fixture.getService()).save(savedPermission.capture());
        assertThat(savedPermission.getValue()).satisfies(saved -> {
            assertThat(saved.getCode()).isEqualTo("  reports:read  ");
            assertThat(saved.getName()).isEqualTo("  Read reports  ");
            assertThat(saved.getTargetIdentifier()).isEqualTo("  /reports/**  ");
            assertThat(saved.getTargetQualifier()).isEqualTo("  GET  ");
            assertThat(saved.getRemark()).isEqualTo(remark);
        });
        assertThat(dto.getCode()).isEqualTo("  reports:read  ");
        assertThat(dto.getName()).isEqualTo("  Read reports  ");
        assertThat(dto.getTargetIdentifier()).isEqualTo("  /reports/**  ");
        assertThat(dto.getTargetQualifier()).isEqualTo("  GET  ");
        assertThat(dto.getRemark()).isEqualTo(remark);
    }

    @ParameterizedTest(name = "accepts exact {0} business limit")
    @MethodSource("permissionLengthBoundaries")
    void createPermissionAcceptsExactBusinessLimits(
            String field,
            int maximum,
            String character,
            String ignoredMessage) {
        PermissionFixture fixture = permissionFixture();
        PermissionDto dto = validPermission();
        setField(dto, field, character.repeat(maximum));
        doReturn(null).when(fixture.getService()).getPermissionByCode(dto.getCode());

        fixture.getService().createPermission(dto);

        verify(fixture.getService()).save(any(PermissionBo.class));
        verify(fixture.getPublisher()).publishEvent(any(AuthorizationEvent.class));
    }

    @ParameterizedTest(name = "rejects {0} beyond business limit")
    @MethodSource("permissionLengthBoundaries")
    void createPermissionRejectsFieldsBeyondBusinessLimits(
            String field,
            int maximum,
            String character,
            String message) {
        PermissionFixture fixture = permissionFixture();
        PermissionDto dto = validPermission();
        setField(dto, field, character.repeat(maximum + 1));

        assertThatThrownBy(() -> fixture.getService().createPermission(dto))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage(message);
        verifyNoPermissionWrite(fixture);
    }

    @Test
    void removePermissionClearsRoleBindings() {
        RolePermissionService rolePermissions = mock(RolePermissionService.class);
        PermissionServiceImpl permissions = spy(new PermissionServiceImpl(
                mock(ApplicationEventPublisher.class),
                mock(UserRoleService.class),
                rolePermissions));
        PermissionBo permission = new PermissionBo();
        permission.setId(2L);
        String originalCode = "\uD83D\uDE00".repeat(255);
        permission.setCode(originalCode);
        permission.setBuiltIn(false);
        doReturn(permission).when(permissions).getById(2L);
        doReturn(true).when(permissions).updateById(permission);
        doReturn(true).when(permissions).removeById(2L);

        permissions.removePermission(2L);

        verify(rolePermissions).removeByPermissionId(2L);
        verify(permissions).removeById(2L);
        assertThat(permission.getCode()).isEqualTo(originalCode + "_del_2");
    }

    private PermissionFixture permissionFixture() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PermissionServiceImpl service = spy(new PermissionServiceImpl(
                publisher,
                mock(UserRoleService.class),
                mock(RolePermissionService.class)));
        doAnswer(invocation -> {
            ((PermissionBo) invocation.getArgument(0)).setId(20L);
            return true;
        }).when(service).save(any(PermissionBo.class));
        return new PermissionFixture(service, publisher);
    }

    private PermissionDto validPermission() {
        PermissionDto dto = new PermissionDto();
        dto.setCode("reports:read");
        dto.setName("Read reports");
        dto.setTargetIdentifier("/reports/**");
        dto.setTargetQualifier("GET");
        dto.setRemark("Allows reports to be read");
        dto.setPublicAccess(false);
        return dto;
    }

    private void verifyNoPermissionWrite(PermissionFixture fixture) {
        verify(fixture.getService(), never()).save(any(PermissionBo.class));
        verify(fixture.getService(), never()).updateById(any(PermissionBo.class));
        verifyNoInteractions(fixture.getPublisher());
    }

    private static void setField(PermissionDto dto, String field, String value) {
        switch (field) {
            case "code" -> dto.setCode(value);
            case "name" -> dto.setName(value);
            case "targetIdentifier" -> dto.setTargetIdentifier(value);
            case "targetQualifier" -> dto.setTargetQualifier(value);
            case "remark" -> dto.setRemark(value);
            default -> throw new IllegalArgumentException("Unknown permission field: " + field);
        }
    }

    private static Stream<Arguments> permissionRequiredFields() {
        return Stream.of(
                Arguments.of("code", "权限编码不能为空。"),
                Arguments.of("name", "权限名称不能为空。"),
                Arguments.of("targetIdentifier", "访问目标标识不能为空。"));
    }

    private static Stream<Arguments> permissionLengthBoundaries() {
        return Stream.of(
                Arguments.of("code", 255, "p", "权限编码不能超过 255 个字符。"),
                Arguments.of("name", 255, "\uD83D\uDE00", "权限名称不能超过 255 个字符。"),
                Arguments.of("targetIdentifier", 500, "t",
                        "访问目标标识不能超过 500 个字符。"),
                Arguments.of("targetQualifier", 100, "q", "目标限定符不能超过 100 个字符。"),
                Arguments.of("remark", 500, "m", "权限描述不能超过 500 个字符。"));
    }

    private static Stream<Arguments> exactPermissionRemarks() {
        return Stream.of(
                Arguments.of("null", (Object) null),
                Arguments.of("empty", ""),
                Arguments.of("whitespace-only", "   "));
    }

    @Data
    private static final class PermissionFixture {
        private final PermissionServiceImpl service;
        private final ApplicationEventPublisher publisher;
    }
}
