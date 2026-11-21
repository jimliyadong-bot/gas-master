package com.ruoyi.gas.h5;

import com.ruoyi.common.exception.ServiceException;

/**
 * H5 端登录上下文（ThreadLocal，由 H5AuthInterceptor 填充/清理）
 *
 * @author gas
 */
public class H5UserContext
{
    private static final ThreadLocal<H5User> HOLDER = new ThreadLocal<>();

    public static void set(H5User user)
    {
        HOLDER.set(user);
    }

    public static H5User get()
    {
        return HOLDER.get();
    }

    /** 必须已登录（拦截器已保证，防御性断言） */
    public static H5User require()
    {
        H5User user = HOLDER.get();
        if (user == null)
        {
            throw new ServiceException("请先授权登录");
        }
        return user;
    }

    /** 必须是已绑定手机号的会员 */
    public static H5User requireMember()
    {
        H5User user = require();
        if (!user.isMember())
        {
            throw new ServiceException("该操作仅限消费者会员");
        }
        if (!user.isPhoneBound())
        {
            throw new ServiceException("请先绑定手机号");
        }
        return user;
    }

    public static void clear()
    {
        HOLDER.remove();
    }
}
