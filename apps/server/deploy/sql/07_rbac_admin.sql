-- RBAC 与后台管理员管理功能初始化脚本。
-- 依赖依次执行 01_rbac.sql 至 05_admin_permissions.sql。
-- 面向 standard 新库初始化及重复执行，不作为其他版本数据库的升级脚本。

SET NAMES utf8mb4;

SET @permission_built_in_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'az_permission'
      AND column_name = 'built_in'
);
SET @permission_built_in_ddl := IF(
    @permission_built_in_exists = 0,
    'ALTER TABLE az_permission ADD COLUMN built_in tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否为系统维护的内置权限：0 否、1 是'' AFTER public_access',
    'SELECT 1'
);
PREPARE permission_built_in_statement FROM @permission_built_in_ddl;
EXECUTE permission_built_in_statement;
DEALLOCATE PREPARE permission_built_in_statement;

SET @menu_built_in_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'az_menu'
      AND column_name = 'built_in'
);
SET @menu_built_in_ddl := IF(
    @menu_built_in_exists = 0,
    'ALTER TABLE az_menu ADD COLUMN built_in tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否为系统维护的内置菜单：0 否、1 是'' AFTER title',
    'SELECT 1'
);
PREPARE menu_built_in_statement FROM @menu_built_in_ddl;
EXECUTE menu_built_in_statement;
DEALLOCATE PREPARE menu_built_in_statement;

-- 将旧角色标识迁移为统一的冒号格式，并在新旧角色并存时合并授权关系。
SET @legacy_rbac_manager_role_id := (
    SELECT id FROM az_role WHERE code = 'rbac-manager' AND del = 0 LIMIT 1
);
SET @rbac_manager_role_id := (
    SELECT id FROM az_role WHERE code = 'rbac:manager' AND del = 0 LIMIT 1
);

UPDATE az_role
SET code = 'rbac:manager',
    update_time = UTC_TIMESTAMP(6)
WHERE id = @legacy_rbac_manager_role_id
  AND @rbac_manager_role_id IS NULL;

SET @rbac_manager_role_id := (
    SELECT id FROM az_role WHERE code = 'rbac:manager' AND del = 0 LIMIT 1
);

INSERT INTO az_user_role (del, create_time, update_time, user_id, role_id)
SELECT 0, UTC_TIMESTAMP(6), NULL, legacy_binding.user_id, @rbac_manager_role_id
FROM az_user_role legacy_binding
WHERE legacy_binding.role_id = @legacy_rbac_manager_role_id
  AND legacy_binding.del = 0
  AND @legacy_rbac_manager_role_id <> @rbac_manager_role_id
  AND NOT EXISTS (
      SELECT 1 FROM az_user_role current_binding
      WHERE current_binding.user_id = legacy_binding.user_id
        AND current_binding.role_id = @rbac_manager_role_id
        AND current_binding.del = 0
  );

INSERT INTO az_role_permission (del, create_time, update_time, role_id, permission_id)
SELECT 0, UTC_TIMESTAMP(6), NULL, @rbac_manager_role_id, legacy_binding.permission_id
FROM az_role_permission legacy_binding
WHERE legacy_binding.role_id = @legacy_rbac_manager_role_id
  AND legacy_binding.del = 0
  AND @legacy_rbac_manager_role_id <> @rbac_manager_role_id
  AND NOT EXISTS (
      SELECT 1 FROM az_role_permission current_binding
      WHERE current_binding.role_id = @rbac_manager_role_id
        AND current_binding.permission_id = legacy_binding.permission_id
        AND current_binding.del = 0
  );

INSERT INTO az_role_menu (del, create_time, update_time, role_id, menu_id)
SELECT 0, UTC_TIMESTAMP(6), NULL, @rbac_manager_role_id, legacy_binding.menu_id
FROM az_role_menu legacy_binding
WHERE legacy_binding.role_id = @legacy_rbac_manager_role_id
  AND legacy_binding.del = 0
  AND @legacy_rbac_manager_role_id <> @rbac_manager_role_id
  AND NOT EXISTS (
      SELECT 1 FROM az_role_menu current_binding
      WHERE current_binding.role_id = @rbac_manager_role_id
        AND current_binding.menu_id = legacy_binding.menu_id
        AND current_binding.del = 0
  );

UPDATE az_user_role
SET del = 1, update_time = UTC_TIMESTAMP(6)
WHERE role_id = @legacy_rbac_manager_role_id
  AND @legacy_rbac_manager_role_id <> @rbac_manager_role_id;
UPDATE az_role_permission
SET del = 1, update_time = UTC_TIMESTAMP(6)
WHERE role_id = @legacy_rbac_manager_role_id
  AND @legacy_rbac_manager_role_id <> @rbac_manager_role_id;
UPDATE az_role_menu
SET del = 1, update_time = UTC_TIMESTAMP(6)
WHERE role_id = @legacy_rbac_manager_role_id
  AND @legacy_rbac_manager_role_id <> @rbac_manager_role_id;
UPDATE az_role
SET del = 1, update_time = UTC_TIMESTAMP(6)
WHERE id = @legacy_rbac_manager_role_id
  AND @legacy_rbac_manager_role_id <> @rbac_manager_role_id;

UPDATE az_role
SET del = 0,
    name = 'RBAC 管理',
    remark = '维护后台用户、角色、权限和菜单',
    built_in = 1,
    update_time = UTC_TIMESTAMP(6)
WHERE code = 'rbac:manager'
  AND (del <> 0 OR built_in <> 1
       OR name <> 'RBAC 管理'
       OR remark <> '维护后台用户、角色、权限和菜单');

INSERT INTO az_role (del, create_time, update_time, code, name, remark, built_in)
SELECT 0, UTC_TIMESTAMP(6), NULL, 'rbac:manager', 'RBAC 管理',
       '维护后台用户、角色、权限和菜单', 1
WHERE NOT EXISTS (
    SELECT 1 FROM az_role WHERE code = 'rbac:manager'
);

-- 关联集合保存接口取代旧 update 路径，并保留已有角色授权。
DROP TEMPORARY TABLE IF EXISTS permission_route_rename;
CREATE TEMPORARY TABLE permission_route_rename (
    old_code varchar(255) NOT NULL,
    new_code varchar(255) NOT NULL,
    new_target_identifier varchar(500) NOT NULL,
    PRIMARY KEY (old_code),
    UNIQUE KEY uk_new_code (new_code)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO permission_route_rename (old_code, new_code, new_target_identifier)
VALUES
    ('POST:/sys/admin/update-roles', 'POST:/sys/admin/roles/save', '/sys/admin/roles/save'),
    ('POST:/authz/role-permission/update', 'POST:/authz/role-permission/save', '/authz/role-permission/save'),
    ('POST:/authz/role-menu/update', 'POST:/authz/role-menu/save', '/authz/role-menu/save');

UPDATE az_permission old_permission
JOIN permission_route_rename route_rename ON route_rename.old_code = old_permission.code
LEFT JOIN az_permission new_permission ON new_permission.code = route_rename.new_code
SET old_permission.code = route_rename.new_code,
    old_permission.name = route_rename.new_code,
    old_permission.target_identifier = route_rename.new_target_identifier,
    old_permission.update_time = UTC_TIMESTAMP(6)
WHERE new_permission.id IS NULL;

UPDATE az_permission permission
JOIN permission_route_rename route_rename ON route_rename.new_code = permission.code
SET permission.del = 0,
    permission.name = route_rename.new_code,
    permission.target_identifier = route_rename.new_target_identifier,
    permission.update_time = UTC_TIMESTAMP(6);

INSERT INTO az_role_permission (del, create_time, update_time, role_id, permission_id)
SELECT 0, UTC_TIMESTAMP(6), NULL, old_binding.role_id, new_permission.id
FROM permission_route_rename route_rename
JOIN az_permission old_permission ON old_permission.code = route_rename.old_code
JOIN az_permission new_permission ON new_permission.code = route_rename.new_code
JOIN az_role_permission old_binding ON old_binding.permission_id = old_permission.id
WHERE old_binding.del = 0
  AND NOT EXISTS (
      SELECT 1
      FROM az_role_permission new_binding
      WHERE new_binding.role_id = old_binding.role_id
        AND new_binding.permission_id = new_permission.id
        AND new_binding.del = 0
  );

UPDATE az_role_permission old_binding
JOIN az_permission old_permission ON old_permission.id = old_binding.permission_id
JOIN permission_route_rename route_rename ON route_rename.old_code = old_permission.code
SET old_binding.del = 1,
    old_binding.update_time = UTC_TIMESTAMP(6)
WHERE old_binding.del = 0;

UPDATE az_permission old_permission
JOIN permission_route_rename route_rename ON route_rename.old_code = old_permission.code
SET old_permission.del = 1,
    old_permission.update_time = UTC_TIMESTAMP(6)
WHERE old_permission.del = 0;

DROP TEMPORARY TABLE permission_route_rename;

-- 系统端点权限是部署配置，不允许通过权限管理界面修改或删除。
UPDATE az_permission
SET built_in = 1,
    update_time = UTC_TIMESTAMP(6)
WHERE code = '*:/error'
   OR target_identifier LIKE '/authz/%'
   OR target_identifier LIKE '/sys/admin/%';

-- RBAC 管理接口和后台管理员管理接口统一要求 rbac:manager 权限。
UPDATE az_permission
SET public_access = 0,
    built_in = 1,
    update_time = UTC_TIMESTAMP(6)
WHERE target_identifier LIKE '/authz/%'
   OR code IN (
       'POST:/sys/admin/page',
       'POST:/sys/admin/create',
       'POST:/sys/admin/update',
       'POST:/sys/admin/roles/save',
       'POST:/sys/admin/remove/{id}'
   );

SET @rbac_manager_role_id := (
    SELECT id FROM az_role WHERE code = 'rbac:manager' AND del = 0 LIMIT 1
);

INSERT INTO az_role_permission (del, create_time, update_time, role_id, permission_id)
SELECT 0, UTC_TIMESTAMP(6), NULL, @rbac_manager_role_id, p.id
FROM az_permission p
WHERE p.del = 0
  AND (
      p.target_identifier LIKE '/authz/%'
      OR p.code IN (
          'POST:/sys/admin/page',
          'POST:/sys/admin/create',
          'POST:/sys/admin/update',
          'POST:/sys/admin/roles/save',
          'POST:/sys/admin/remove/{id}'
      )
  )
  AND @rbac_manager_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM az_role_permission rp
      WHERE rp.role_id = @rbac_manager_role_id
        AND rp.permission_id = p.id
        AND rp.del = 0
  );

INSERT INTO az_menu (
    del, create_time, update_time, pid, type, status, name, path, component,
    affix_tab, hide_in_menu, keep_alive, icon, `order`, title
)
SELECT
    0, UTC_TIMESTAMP(6), NULL, 0, 'catalog', 1, 'System', '/system', 'BasicLayout',
    0, 0, 0, 'lucide:settings', 50, '系统管理'
WHERE NOT EXISTS (
    SELECT 1 FROM az_menu WHERE name = 'System'
);

UPDATE az_menu
SET del = 0,
    pid = 0,
    type = 'catalog',
    status = 1,
    path = '/system',
    component = 'BasicLayout',
    hide_in_menu = 0,
    keep_alive = 0,
    icon = 'lucide:settings',
    `order` = 50,
    title = '系统管理',
    update_time = UTC_TIMESTAMP(6)
WHERE name = 'System';

SET @system_menu_id := (
    SELECT id FROM az_menu WHERE name = 'System' AND del = 0 LIMIT 1
);


DROP TEMPORARY TABLE IF EXISTS rbac_admin_menu_seed;
CREATE TEMPORARY TABLE rbac_admin_menu_seed (
    name varchar(255) NOT NULL,
    parent_name varchar(255) NOT NULL,
    type varchar(16) NOT NULL,
    access_code varchar(255) DEFAULT NULL,
    path varchar(500) DEFAULT NULL,
    component varchar(255) DEFAULT NULL,
    icon varchar(255) DEFAULT NULL,
    keep_alive tinyint(1) NOT NULL DEFAULT 0,
    menu_order int NOT NULL,
    title varchar(255) NOT NULL,
    PRIMARY KEY (name)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO rbac_admin_menu_seed
    (name, parent_name, type, access_code, path, component, icon, menu_order, title)
VALUES
    ('Admin', 'System', 'menu', NULL, '/system/admin', '/system/admin/index', 'lucide:user-cog', 20, '后台管理员'),
    ('Role', 'System', 'menu', NULL, '/system/role', '/system/role/index', 'lucide:users-round', 30, '角色管理'),
    ('Permission', 'System', 'menu', NULL, '/system/permission', '/system/permission/index', 'lucide:key-round', 40, '权限管理'),
    ('Menu', 'System', 'menu', NULL, '/system/menu', '/system/menu/index', 'lucide:list-tree', 50, '菜单管理');

UPDATE rbac_admin_menu_seed
SET keep_alive = 1;

INSERT INTO az_menu (
    del, create_time, update_time, pid, type, status, access_code, name, path,
    component, affix_tab, hide_in_menu, keep_alive, icon, `order`, title, built_in
)
SELECT
    0, UTC_TIMESTAMP(6), NULL, parent.id, seed.type, 1, seed.access_code, seed.name, seed.path,
    seed.component, 0, 0, seed.keep_alive, seed.icon, seed.menu_order, seed.title, 1
FROM rbac_admin_menu_seed seed
JOIN az_menu parent ON parent.name = seed.parent_name AND parent.del = 0
WHERE NOT EXISTS (
    SELECT 1 FROM az_menu current_menu WHERE current_menu.name = seed.name
);

INSERT INTO rbac_admin_menu_seed
    (name, parent_name, type, access_code, path, component, icon, menu_order, title)
VALUES
    ('AdminCreate', 'Admin', 'button', 'system:admin:create', NULL, NULL, NULL, 1, '新增后台管理员'),
    ('AdminUpdate', 'Admin', 'button', 'system:admin:update', NULL, NULL, NULL, 2, '修改后台管理员'),
    ('AdminRemove', 'Admin', 'button', 'system:admin:remove', NULL, NULL, NULL, 3, '删除后台管理员'),
    ('AdminRole', 'Admin', 'button', 'system:admin:manage-roles', NULL, NULL, NULL, 4, '分配角色'),
    ('RoleCreate', 'Role', 'button', 'system:role:create', NULL, NULL, NULL, 1, '新增角色'),
    ('RoleUpdate', 'Role', 'button', 'system:role:update', NULL, NULL, NULL, 2, '修改角色'),
    ('RoleRemove', 'Role', 'button', 'system:role:remove', NULL, NULL, NULL, 3, '删除角色'),
    ('RolePermission', 'Role', 'button', 'system:role:manage-permissions', NULL, NULL, NULL, 4, '分配权限'),
    ('RoleMenu', 'Role', 'button', 'system:role:manage-menus', NULL, NULL, NULL, 5, '菜单授权'),
    ('PermissionCreate', 'Permission', 'button', 'system:permission:create', NULL, NULL, NULL, 1, '新增权限'),
    ('PermissionUpdate', 'Permission', 'button', 'system:permission:update', NULL, NULL, NULL, 2, '修改权限'),
    ('PermissionRemove', 'Permission', 'button', 'system:permission:remove', NULL, NULL, NULL, 3, '删除权限'),
    ('MenuCreate', 'Menu', 'button', 'system:menu:create', NULL, NULL, NULL, 1, '新增菜单'),
    ('MenuUpdate', 'Menu', 'button', 'system:menu:update', NULL, NULL, NULL, 2, '修改菜单'),
    ('MenuRemove', 'Menu', 'button', 'system:menu:remove', NULL, NULL, NULL, 3, '删除菜单');

INSERT INTO az_menu (
    del, create_time, update_time, pid, type, status, access_code, name, path,
    component, affix_tab, hide_in_menu, keep_alive, `order`, title, built_in
)
SELECT
    0, UTC_TIMESTAMP(6), NULL, parent.id, seed.type, 1, seed.access_code, seed.name, seed.path,
    seed.component, 0, 0, seed.keep_alive, seed.menu_order, seed.title, 1
FROM rbac_admin_menu_seed seed
JOIN az_menu parent ON parent.name = seed.parent_name AND parent.del = 0
WHERE seed.type = 'button'
  AND NOT EXISTS (
      SELECT 1 FROM az_menu current_menu WHERE current_menu.name = seed.name
  );

UPDATE az_menu current_menu
JOIN rbac_admin_menu_seed seed ON seed.name = current_menu.name
JOIN az_menu parent ON parent.name = seed.parent_name AND parent.del = 0
SET current_menu.del = 0,
    current_menu.pid = parent.id,
    current_menu.type = seed.type,
    current_menu.status = 1,
    current_menu.access_code = seed.access_code,
    current_menu.path = seed.path,
    current_menu.component = seed.component,
    current_menu.keep_alive = seed.keep_alive,
    current_menu.icon = seed.icon,
    current_menu.`order` = seed.menu_order,
    current_menu.title = seed.title,
    current_menu.built_in = 1,
    current_menu.update_time = UTC_TIMESTAMP(6);

UPDATE az_menu
SET built_in = 1,
    update_time = UTC_TIMESTAMP(6)
WHERE name IN (
    'Dashboard',
    'Profile',
    'System'
)
  AND del = 0;

INSERT INTO az_role_menu (del, create_time, update_time, role_id, menu_id)
SELECT 0, UTC_TIMESTAMP(6), NULL, @rbac_manager_role_id, menu.id
FROM az_menu menu
WHERE menu.del = 0
  AND (menu.name = 'System' OR menu.name IN (SELECT name FROM rbac_admin_menu_seed))
  AND @rbac_manager_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM az_role_menu rm
      WHERE rm.role_id = @rbac_manager_role_id
        AND rm.menu_id = menu.id
        AND rm.del = 0
  );


DROP TEMPORARY TABLE IF EXISTS rbac_admin_menu_seed;

-- 新库首次创建默认管理员时授予本项目的管理能力；后续初始化不恢复操作者解除的绑定。
-- 02_admin.sql 在创建账号前记录首次创建标记；未执行前置脚本时不授予角色。
INSERT INTO az_user_role (del, create_time, update_time, user_id, role_id)
SELECT 0, UTC_TIMESTAMP(6), NULL, admin.user_id, role.id
FROM sys_admin admin
JOIN az_role role ON role.code = 'rbac:manager' AND role.del = 0
WHERE admin.username = 'admin'
  AND admin.del = 0
  AND @default_admin_created = 1
  AND NOT EXISTS (
      SELECT 1 FROM az_user_role binding
      WHERE binding.user_id = admin.user_id AND binding.role_id = role.id
  );

-- 同一连接单独重放本脚本也不应被误判为首次初始化。
SET @default_admin_created := 0;
