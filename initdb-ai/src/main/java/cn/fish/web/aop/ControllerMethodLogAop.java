package cn.fish.web.aop;


import cn.fish.web.response.ResponseResult;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.util.*;

/**
 * controller方法切面，打印请求参数和返回数据对象信息，并且记录操作日志
 */
@Slf4j
@Aspect
public class ControllerMethodLogAop {


    /**
     * 在RestController注解下的所有方法
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void pointCut() {
        // DO NOTHING
    }


    /**
     * 使用环绕通知，不要使用单独的前后通知。
     * 因为前后处理都需要HttpServletRequest，但是HttpServletRequest只能读取一次，第二次读取到的属性为null。
     * 所以在环绕通知时，一次性读取所有需要HttpServletRequest处理的参数
     */
    @Around("pointCut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {

        HttpServletRequest request = getRequest();

        //方法执行前置处理，返回请求参数字符串
        String params = pre(proceedingJoinPoint, request);
        //调用方法
        Object result = null;
        String flag = "success";
        Date startDate = new Date();
        try {
            //必须要获取返回值并return，否则页面获取不到请求的返回数据
            result = proceedingJoinPoint.proceed();

        } catch (Throwable throwable) {
            flag = "error";
            throw throwable;
        } finally {
            try {
                if (log.isDebugEnabled()) {
                    String traceId = null;
                    if (result instanceof ResponseResult<?>) {
                        ResponseResult<?> responseResult = (ResponseResult<?>) result;
                        log.debug(ParamPrintProperty.RESULT_INFO, traceId, responseResult.getCode(),
                                responseResult.getMessage());
                    }
                }
            } catch (Exception e) {
                //后置处理，就算报错也不影响方法执行
                String requestUri = request.getRequestURI();
                log.error("request " + requestUri + ";ControllerMethodLogAop after() error!", e);
            }
        }
        return result;
    }

    /**
     * 方法执行的前置处理
     *
     * @param proceedingJoinPoint ProceedingJoinPoint
     * @param request             HttpServletRequest
     */
    private String pre(ProceedingJoinPoint proceedingJoinPoint, HttpServletRequest request) {
        //打印方法请求参数
        try {
            return printMethodParams(proceedingJoinPoint, request);
        } catch (Exception e) {
            //前置处理，就算报错也不影响方法执行
            log.error("ControllerMethodLogAop pre() error!", e);
        }
        return null;
    }


    private String printMethodParams(ProceedingJoinPoint proceedingJoinPoint, HttpServletRequest request) {
        String methodName = proceedingJoinPoint.getSignature().getName();
        //将参数map设置给外部变量的map
        Map<String, Object> paramMap = getNameAndValue(proceedingJoinPoint);
        String paramStr = JSON.toJSONString(paramMap);
        if (log.isDebugEnabled()) {
            String traceId = null;
            log.debug(ParamPrintProperty.REQUEST_INFO, traceId, request.getMethod(), methodName, request.getRequestURI(),
                    request.getRequestURI(), null, paramStr);
        }
        return paramStr;
    }

    /**
     * 获取某个Method的参数名称及对应的值
     *
     * @param joinPoint ProceedingJoinPoint
     * @return Map&lt;参数名称, 参数值$gt;
     */
    private Map<String, Object> getNameAndValue(ProceedingJoinPoint joinPoint) {
        Map<String, Object> param = new HashMap<>();
        Object[] paramValues = joinPoint.getArgs();
        String[] paramNames = ((CodeSignature) joinPoint.getSignature()).getParameterNames();
        for (int i = 0; i < paramNames.length; i++) {
            Object paramValue = paramValues[i];
            if (paramValue instanceof Serializable && !isContainsMultipartFile(paramValue)) {
                param.put(paramNames[i], paramValues[i]);
            }
            else {
                param.put(paramNames[i], null == paramValue ? null : paramValue.getClass().getName());
            }

        }
        return param;
    }

    private boolean isContainsMultipartFile(Object obj) {
        if (obj instanceof MultipartFile || obj instanceof MultipartFile[]) {
            return true;
        }
        if (obj instanceof List<?>) {
            if (CollectionUtils.isEmpty((List<?>) obj)) {
                return false;
            }
            return ((List<?>) obj).get(0) instanceof MultipartFile;
        }
        return false;
    }

    private HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
        return Objects.requireNonNull(attributes).getRequest();
    }

    private static class ParamPrintProperty {
        public static final String REQUEST_INFO = "Request   Info: traceId:{}, type:{}, method:{}, url:{}, client:{}, requestOrigin:{}, Request " +
                "Param:  {}";
        public static final String RESULT_INFO = "Result    Info: traceId:{}, code:{}, message:{}";
        public static final String REST_RESULT_INFO = "RPCResult Info: traceId:{}, code:{}, message:{}";

    }

}
