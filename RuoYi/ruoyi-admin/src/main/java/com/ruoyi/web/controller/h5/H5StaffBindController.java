package com.ruoyi.web.controller.h5;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.framework.shiro.service.SysLoginService;
import com.ruoyi.gas.h5.H5TokenService;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.gas.service.IH5AuthService;

/** H5 员工账号绑定：复用后台密码校验，绑定成功后签发员工 JWT。 */
@Anonymous
@RestController
@RequestMapping("/api/h5/staff")
public class H5StaffBindController extends BaseController
{
    @Autowired
    private SysLoginService loginService;
    @Autowired
    private IH5AuthService authService;
    @Autowired
    private H5TokenService tokenService;

    @PostMapping("/bind")
    public AjaxResult bind(@RequestBody Map<String, String> body)
    {
        String loginName = body == null ? null : body.get("loginName");
        String password = body == null ? null : body.get("password");
        H5User current = H5UserContext.require();
        SysUser sysUser = loginService.login(loginName, password);
        H5User bound = authService.staffBind(current, sysUser);
        AjaxResult result = success("绑定成功");
        result.put("token", tokenService.issue(bound));
        result.put("role", bound.getRole());
        result.put("stationId", bound.getStationId());
        return result;
    }
}
