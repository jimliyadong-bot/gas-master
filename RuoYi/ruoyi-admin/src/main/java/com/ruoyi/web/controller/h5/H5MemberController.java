package com.ruoyi.web.controller.h5;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.gas.service.IH5AuthService;

/**
 * H5 会员信息（Dev_Plan §5 /api/h5/c/*，JWT + 会员）
 *
 * <p>Shiro 层匿名放行，认证由 H5AuthInterceptor 承担。</p>
 *
 * @author gas
 */
@Anonymous
@RestController
@RequestMapping("/api/h5/c")
public class H5MemberController extends BaseController
{
    @Autowired
    private IH5AuthService authService;

    /**
     * 当前登录身份（前端用它判断授权状态、是否已绑定手机号、角色分流）
     */
    @GetMapping("/me")
    public AjaxResult me()
    {
        H5User user = H5UserContext.require();
        return success(authService.me(user));
    }
}
