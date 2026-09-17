package com.anfioo.howtocook.app.config;

import cn.dev33.satoken.stp.StpInterface;
import com.anfioo.howtocook.app.service.AuthService;

import java.util.List;

/**
 * Sa-Token RBAC 数据源：角色来自 user_role JOIN role（Step 1.2）。
 * <p>本系统无细粒度权限点，仅角色鉴权，权限列表恒为空。</p>
 */
public class StpInterfaceImpl implements StpInterface {

    private final AuthService authService;

    public StpInterfaceImpl(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return authService.getRoleCodes(Long.parseLong(loginId.toString()));
    }
}
