package com.gnilc.auth.authz.rbac.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnilc.auth.authz.rbac.constant.MenuConstant;
import com.gnilc.auth.authz.rbac.dao.MenuDao;
import com.gnilc.auth.authz.rbac.entity.bo.MenuBo;
import com.gnilc.auth.authz.rbac.entity.dto.MenuDto;
import com.gnilc.auth.authz.rbac.entity.enums.MenuType;
import com.gnilc.auth.authz.rbac.entity.vo.MenuRouteVo;
import com.gnilc.auth.authz.rbac.entity.vo.MenuVo;
import com.gnilc.auth.authz.rbac.event.MenuEvent;
import com.gnilc.auth.authz.rbac.service.MenuService;
import com.gnilc.auth.authz.rbac.service.RoleMenuService;
import com.gnilc.common.base.Preconditions;
import com.gnilc.common.exception.InvalidArgumentException;
import com.gnilc.common.utils.HttpUrlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 维护菜单合法层级和资源保护规则，并从已授权菜单构建可达的导航树。 */
@Service("menuService")
public class MenuServiceImpl extends ServiceImpl<MenuDao, MenuBo> implements MenuService {
    private static final String IFRAME_VIEW = "IFrameView";
    private static final TypeReference<Map<String, Object>> QUERY_TYPE = new TypeReference<>() {
    };

    private final MenuDao menuDao;
    private final RoleMenuService roleMenuService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public MenuServiceImpl(MenuDao menuDao,
                           RoleMenuService roleMenuService,
                           ApplicationEventPublisher eventPublisher,
                           ObjectMapper objectMapper) {
        this.menuDao = menuDao;
        this.roleMenuService = roleMenuService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MenuVo> getMenuTree() {
        List<MenuVo> vos = list().stream()
                .map(bo -> {
                    MenuVo vo = new MenuVo();
                    BeanUtils.copyProperties(bo, vo);
                    return vo;
                })
                .toList();
        Map<Long, MenuVo> voMap = vos.stream()
                .collect(Collectors.toMap(MenuVo::getId, vo -> vo));
        List<MenuVo> roots = new ArrayList<>();
        for (MenuVo vo : vos) {
            Long pid = vo.getPid();
            if (Objects.equals(pid, MenuConstant.ROOT_PARENT_ID)) {
                roots.add(vo);
            }
            MenuVo parent = voMap.get(pid);
            if (parent != null) {
                parent.getChildren().add(vo);
            }
        }
        sortMenuTree(roots);
        return roots;
    }

    @Override
    @Transactional
    public void createMenu(MenuDto dto) {
        Preconditions.checkArgument(dto != null, "菜单信息不能为空。");
        MenuBo bo = new MenuBo();
        BeanUtils.copyProperties(dto, bo);
        bo.setBuiltIn(Boolean.FALSE);
        validateMenu(bo);
        save(bo);
        eventPublisher.publishEvent(new MenuEvent(MenuEvent.Action.CREATE, bo.getId()));
    }

    /**
     * 使用同一个菜单对象完成完整请求的校验和持久化，确保校验值与落库值一致。
     */
    @Override
    @Transactional
    public void updateMenu(MenuDto dto) {
        Preconditions.checkArgument(dto != null, "菜单信息不能为空。");
        Long menuId = dto.getId();
        Preconditions.checkArgument(menuId != null, "请选择菜单。");
        MenuBo menu = getById(menuId);
        Preconditions.checkArgument(menu != null, "菜单已不存在，请刷新后重试。");
        Preconditions.checkCondition(!Boolean.TRUE.equals(menu.getBuiltIn()),
                "内置菜单不能修改。");
        Preconditions.checkArgument(dto.getType() != null, "请选择菜单类型。");
        Preconditions.checkCondition(Objects.equals(dto.getType(), menu.getType()),
                "菜单类型创建后不能修改。");
        BeanUtils.copyProperties(dto, menu);
        validateMenu(menu);
        updateById(menu);
        eventPublisher.publishEvent(new MenuEvent(MenuEvent.Action.UPDATE, menuId));
    }

    /** 保护整棵子树内的内置资源，释放可复用标识、删除绑定后再逻辑删除全部后代。 */
    @Transactional
    @Override
    public void removeMenu(Long id) {
        Preconditions.checkArgument(id != null, "请选择菜单。");
        MenuBo bo = getById(id);
        Preconditions.checkArgument(bo != null, "菜单已不存在，请刷新后重试。");
        Preconditions.checkCondition(!Boolean.TRUE.equals(bo.getBuiltIn()),
                "内置菜单不能删除。");
        List<Long> menuIds = getSubtreeIds(id);
        List<MenuBo> subtree = getMenus(menuIds);
        Preconditions.checkCondition(subtree.stream()
                        .noneMatch(menu -> Boolean.TRUE.equals(menu.getBuiltIn())),
                "该菜单包含内置菜单，无法删除。");
        subtree.forEach(menu -> {
            String suffix = "_del_" + menu.getId();
            menu.setName(menu.getName() + suffix);
            if (menu.getPath() != null) {
                menu.setPath(menu.getPath() + suffix);
            }
            if (menu.getAccessCode() != null) {
                menu.setAccessCode(menu.getAccessCode() + suffix);
            }
            updateById(menu);
        });
        roleMenuService.removeByMenuIds(menuIds);
        removeByIds(menuIds);
        eventPublisher.publishEvent(new MenuEvent(MenuEvent.Action.DELETE, id));
    }

    @Override
    public List<MenuBo> getMenus(List<Long> menuIds) {
        if (CollectionUtils.isEmpty(menuIds)) {
            return List.of();
        }
        return lambdaQuery()
                .in(MenuBo::getId, menuIds)
                .orderByAsc(MenuBo::getOrder)
                .list();
    }

    /** 补齐所选菜单到根的完整路径；严格模式拒绝缺失或循环层级，导航读取模式跳过无效选择。 */
    @Override
    public List<MenuBo> getMenusWithAncestors(Set<Long> menuIds, boolean thorough) {
        if (CollectionUtils.isEmpty(menuIds)) {
            return List.of();
        }
        Map<Long, MenuBo> menuMap = list().stream()
                .collect(Collectors.toMap(MenuBo::getId, menu -> menu));
        Map<Long, MenuBo> result = new LinkedHashMap<>();

        for (Long menuId : menuIds) {
            MenuBo menu = menuMap.get(menuId);
            Set<Long> visited = new HashSet<>();
            List<MenuBo> hierarchy = new ArrayList<>();

            // 从所选菜单回溯到根节点，同时检测层级循环。
            while (menu != null && visited.add(menu.getId())) {
                hierarchy.add(menu);
                if (Objects.equals(menu.getPid(), MenuConstant.ROOT_PARENT_ID)) {
                    break;
                }
                menu = menuMap.get(menu.getPid());
            }

            boolean reachesRoot = menu != null
                    && Objects.equals(menu.getPid(), MenuConstant.ROOT_PARENT_ID);
            if (reachesRoot) {
                hierarchy.forEach(m -> result.putIfAbsent(m.getId(), m));
                continue;
            }
            if (!thorough) {
                continue;
            }
            if (hierarchy.isEmpty()) {
                throw new InvalidArgumentException("所选菜单已不存在，请刷新后重试。");
            }
            if (menu == null) {
                throw new InvalidArgumentException("所选菜单层级不完整。");
            }
            throw new InvalidArgumentException("所选菜单层级无效。");
        }

        return result.values().stream()
                .sorted(Comparator.comparingInt(menu -> Optional.ofNullable(menu.getOrder()).orElse(999)))
                .toList();
    }

    /** 从授权闭包构建启用且可达的导航，移除按钮及没有可导航后代的空目录；不据此替代后端权限校验。 */
    @Override
    public List<MenuRouteVo> getMenuRoutes(List<Long> menuIds) {
        Set<Long> selectedMenuIds = CollectionUtils.isEmpty(menuIds)
                ? Set.of()
                : new HashSet<>(menuIds);
        List<MenuBo> menus = getMenusWithAncestors(selectedMenuIds, false).stream()
                .filter(menu -> Boolean.TRUE.equals(menu.getStatus()))
                .filter(menu -> menu.getType() != MenuType.BUTTON)
                .toList();
        Map<Long, MenuRouteVo> routeMap = menus.stream()
                .collect(Collectors.toMap(MenuBo::getId, this::toMenuRouteVo));
        Map<MenuRouteVo, MenuType> routeTypes = new IdentityHashMap<>();
        menus.forEach(menu -> routeTypes.put(routeMap.get(menu.getId()), menu.getType()));
        List<MenuRouteVo> roots = new ArrayList<>();
        for (MenuBo menu : menus) {
            MenuRouteVo route = routeMap.get(menu.getId());
            if (Objects.equals(menu.getPid(), MenuConstant.ROOT_PARENT_ID)) {
                roots.add(route);
                continue;
            }
            MenuRouteVo parent = routeMap.get(menu.getPid());
            if (parent != null) {
                parent.getChildren().add(route);
            }
        }
        roots.removeIf(route -> !hasNavigableRoute(route, routeTypes));
        sortMenuRoutes(roots);
        return roots;
    }

    @Override
    public MenuBo getMenuByPath(String path) {
        if (StringUtils.isNotBlank(path)) {
            return lambdaQuery()
                    .eq(MenuBo::getPath, path)
                    .one();
        }
        return null;
    }

    @Override
    public MenuBo getMenuByAccessCode(String accessCode) {
        if (StringUtils.isNotBlank(accessCode)) {
            return lambdaQuery()
                    .eq(MenuBo::getAccessCode, accessCode)
                    .one();
        }
        return null;
    }

    private MenuBo getMenuByName(String name) {
        if (StringUtils.isNotBlank(name)) {
            return lambdaQuery()
                    .eq(MenuBo::getName, name)
                    .one();
        }
        return null;
    }

    private void validateMenu(MenuBo bo) {
        Long menuId = bo.getId();
        Long pid = bo.getPid();
        MenuType type = bo.getType();
        String name = bo.getName();
        String title = bo.getTitle();
        String path = bo.getPath();
        String component = bo.getComponent();
        String accessCode = bo.getAccessCode();
        String iframeSrc = bo.getIframeSrc();
        String link = bo.getLink();
        Preconditions.checkArgument(type != null, "请选择菜单类型。");
        Preconditions.checkArgument(pid != null, "请选择父菜单。");
        validateParent(menuId, pid, type);
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "菜单名称不能为空。");
        Preconditions.checkArgument(StringUtils.isNotBlank(title), "菜单标题不能为空。");
        Preconditions.checkArgument(name.codePointCount(0, name.length()) <= 255,
                "菜单名称不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(title.codePointCount(0, title.length()) <= 255,
                "菜单标题不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(accessCode == null || accessCode.codePointCount(0, accessCode.length()) <= 255,
                "按钮权限码不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(path == null || path.codePointCount(0, path.length()) <= 500,
                "路由路径不能超过 %s 个字符。".formatted(500));
        Preconditions.checkArgument(component == null || component.codePointCount(0, component.length()) <= 255,
                "组件路径不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(bo.getRedirect() == null
                        || bo.getRedirect().codePointCount(0, bo.getRedirect().length()) <= 500,
                "重定向路径不能超过 %s 个字符。".formatted(500));
        Preconditions.checkArgument(bo.getActivePath() == null
                        || bo.getActivePath().codePointCount(0, bo.getActivePath().length()) <= 500,
                "激活菜单路径不能超过 %s 个字符。".formatted(500));
        Preconditions.checkArgument(bo.getBadge() == null
                        || bo.getBadge().codePointCount(0, bo.getBadge().length()) <= 100,
                "徽标内容不能超过 %s 个字符。".formatted(100));
        Preconditions.checkArgument(bo.getBadgeType() == null
                        || bo.getBadgeType().codePointCount(0, bo.getBadgeType().length()) <= 16,
                "徽标类型不能超过 %s 个字符。".formatted(16));
        Preconditions.checkArgument(bo.getBadgeVariants() == null
                        || bo.getBadgeVariants().codePointCount(0, bo.getBadgeVariants().length()) <= 32,
                "徽标样式不能超过 %s 个字符。".formatted(32));
        Preconditions.checkArgument(bo.getIcon() == null
                        || bo.getIcon().codePointCount(0, bo.getIcon().length()) <= 255,
                "图标不能超过 %s 个字符。".formatted(255));
        Preconditions.checkArgument(iframeSrc == null || iframeSrc.codePointCount(0, iframeSrc.length()) <= 500,
                "内嵌页面 URL 不能超过 %s 个字符。".formatted(500));
        Preconditions.checkArgument(link == null || link.codePointCount(0, link.length()) <= 500,
                "外链 URL 不能超过 %s 个字符。".formatted(500));
        switch (type) {
            case CATALOG ->
                    Preconditions.checkArgument(StringUtils.isNotBlank(path), "路由路径不能为空。");
            case MENU -> {
                Preconditions.checkArgument(StringUtils.isNotBlank(path), "路由路径不能为空。");
                Preconditions.checkArgument(StringUtils.isNotBlank(component),
                        "页面组件不能为空。");
            }
            case BUTTON -> Preconditions.checkArgument(StringUtils.isNotBlank(accessCode),
                    "权限码不能为空。");
            case EMBEDDED -> {
                Preconditions.checkArgument(StringUtils.isNotBlank(path), "路由路径不能为空。");
                Preconditions.checkArgument(StringUtils.isNotBlank(iframeSrc),
                        "内嵌页面地址不能为空。");
                Preconditions.checkArgument(HttpUrlUtils.isValid(iframeSrc),
                        "内嵌页面地址必须是完整的 http 或 https URL。");
            }
            case LINK -> {
                Preconditions.checkArgument(StringUtils.isNotBlank(path), "路由路径不能为空。");
                Preconditions.checkArgument(StringUtils.isNotBlank(link), "外部链接不能为空。");
                Preconditions.checkArgument(HttpUrlUtils.isValid(link),
                        "外部链接必须是完整的 http 或 https URL。");
            }
        }
        MenuBo nameBo = getMenuByName(name);
        Preconditions.checkArgument(nameBo == null || Objects.equals(nameBo.getId(), menuId),
                "已存在使用该名称的菜单。");
        MenuBo pathBo = getMenuByPath(path);
        Preconditions.checkArgument(pathBo == null || Objects.equals(pathBo.getId(), menuId),
                "已存在使用该路由路径的菜单。");
        MenuBo accessBo = getMenuByAccessCode(accessCode);
        Preconditions.checkArgument(accessBo == null || Objects.equals(accessBo.getId(), menuId),
                "已存在使用该权限码的菜单。");
    }

    /**
     * 校验候选父节点存在、层级完整，并确保调整父级后不会形成循环。
     */
    private void validateParent(Long menuId, Long pid, MenuType childType) {
        if (Objects.equals(pid, MenuConstant.ROOT_PARENT_ID)) {
            Preconditions.checkArgument(childType != MenuType.BUTTON,
                    "按钮不能直接放在根节点下。");
            return;
        }
        MenuBo parent = getById(pid);
        Preconditions.checkArgument(parent != null,
                "父菜单已不存在，请选择其他菜单。");
        Preconditions.checkArgument(allowsChild(parent.getType(), childType),
                "所选父菜单不能包含该菜单类型。");
        Set<Long> visited = new HashSet<>();
        // 从候选父节点回溯到根节点；途中遇到当前菜单即表示会形成父子环。
        while (true) {
            Preconditions.checkArgument(visited.add(parent.getId()),
                    "父菜单层级无效。");
            Preconditions.checkArgument(!Objects.equals(parent.getId(), menuId),
                    "不能将菜单自身或其子菜单设为父菜单。");
            if (Objects.equals(parent.getPid(), MenuConstant.ROOT_PARENT_ID)) {
                return;
            }
            parent = getById(parent.getPid());
            Preconditions.checkArgument(parent != null,
                    "父菜单层级不完整。");
        }
    }

    private boolean allowsChild(MenuType parentType, MenuType childType) {
        if (parentType == MenuType.CATALOG) {
            return true;
        }
        return parentType == MenuType.MENU && childType == MenuType.BUTTON;
    }

    private boolean hasNavigableRoute(
            MenuRouteVo route,
            Map<MenuRouteVo, MenuType> routeTypes) {
        route.getChildren().removeIf(child -> !hasNavigableRoute(child, routeTypes));
        return routeTypes.get(route) != MenuType.CATALOG || !route.getChildren().isEmpty();
    }

    private List<Long> getSubtreeIds(Long rootId) {
        // 常规查询会隐藏已逻辑删除节点；删除子树仍需穿过这些节点清理后代及角色绑定。
        return menuDao.getSubtreeIds(rootId, true);
    }

    private MenuRouteVo toMenuRouteVo(MenuBo menu) {
        MenuRouteVo route = new MenuRouteVo();
        route.setName(menu.getName());
        route.setPath(menu.getPath());
        route.setRedirect(menu.getRedirect());
        route.setComponent(switch (menu.getType()) {
            case MENU -> menu.getComponent();
            case EMBEDDED, LINK -> IFRAME_VIEW;
            case BUTTON, CATALOG -> null;
        });
        MenuRouteVo.Meta meta = new MenuRouteVo.Meta();
        BeanUtils.copyProperties(menu, meta, "query");
        meta.setQuery(parseQuery(menu.getQuery()));
        route.setMeta(meta);
        return route;
    }

    /**
     * 将数据库 JSON 字段转换为 Vben {@code RouteMeta.query} 所需的对象结构。
     * Jackson 会把返回的 Map 序列化为 JSON 对象；如果保留 String，则会序列化为带转义的 JSON 字符串。
     */
    private Map<String, Object> parseQuery(String query) {
        if (StringUtils.isBlank(query)) {
            return null;
        }
        try {
            return objectMapper.readValue(query, QUERY_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Menu route query must be a JSON object.", exception);
        }
    }

    private void sortMenuRoutes(List<MenuRouteVo> routes) {
        routes.sort(Comparator.comparingInt(route -> Optional.ofNullable(route.getMeta().getOrder()).orElse(999)));
        for (MenuRouteVo route : routes) {
            sortMenuRoutes(route.getChildren());
        }
    }

    private void sortMenuTree(List<MenuVo> vos) {
        vos.sort(Comparator.comparingInt(vo -> Optional.ofNullable(vo.getOrder()).orElse(999)));
        for (MenuVo vo : vos) {
            List<MenuVo> children = vo.getChildren();
            if (!CollectionUtils.isEmpty(children)) {
                sortMenuTree(children);
            }
        }
    }
}
