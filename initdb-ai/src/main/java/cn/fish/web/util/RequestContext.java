package cn.fish.web.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;



/**
 * @author onethefish
 **/
public class RequestContext {
    private RequestContext() {
    }

    /**
     * 获取当前的HttpServletRequest
     *
     * @return HttpServletRequest
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return null == attributes ? null : attributes.getRequest();
    }

}
