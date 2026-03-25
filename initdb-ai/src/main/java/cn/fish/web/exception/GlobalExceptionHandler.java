package cn.fish.web.exception;


import cn.fish.web.response.ResponseResult;
import cn.fish.web.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.DecodeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.validation.ConstraintViolationException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/**
 * 定义全局异常处理
 *
 * @author onethefish
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Autowired
    private MultipartProperties multipartProperties;

    /**
     * 处理所有未知异常
     *
     * @param e {@link Exception}
     * @return {@link ResponseResult}
     */
    @ExceptionHandler({Exception.class})
    public Object handleException(Exception e) {
        String traceId = null;
        //不管如何，先打印异常信息
        log.error(MessageFormat.format("traceId:{0}; {1}", traceId, e.getMessage()), e);
        if (e instanceof HttpRequestMethodNotSupportedException) {
            HttpServletRequest request = RequestContext.getRequest();
            if (null != request) {
                log.error("traceId:" + traceId + ", exception url:" + request.getRequestURI() + "; Method:" + request.getMethod(), e);
            }
        }
        else if (e.getCause() instanceof DecodeException) {
            //feign请求结果失败异常
            return ResponseResult.error(e.getCause().getMessage());
        }
        //返回到页面
        return ResponseResult.error("系统异常，请查看日志");
    }

    /**
     * 处理通用异常 CommonException
     *
     * @param ex {@link CommonException}
     * @return {@link ResponseResult}
     */
    @ExceptionHandler({CommonException.class})
    public Object handleCommonException(CommonException ex) {
        Level level = ex.getLevel();
        HttpServletRequest request = RequestContext.getRequest();
        String traceId = null;
        if (Level.WARN.equals(level)) {
            log.warn(MessageFormat.format("traceId:{0},url:{1}; common exception:{2}", traceId, request.getRequestURI(), ex.getMessage()));
        }
        if (Level.ERROR.equals(level)) {
            log.error(MessageFormat.format("traceId:{0},url:{1}; common exception:{2}", traceId, request.getRequestURI(), ex.getMessage()), ex);
        }
        return ResponseResult.error(ex.getSolutionUrl(), ex.getCode(), ex.getMessage());
    }


    @ExceptionHandler({IllegalArgumentException.class})
    public Object handleIllegalArgumentException(IllegalArgumentException ex) {
        String traceId = null;
        log.error(MessageFormat.format("traceId:{0}; IllegalArgumentException", traceId), ex);
        return ResponseResult.error(ex.getMessage());
    }

    @ExceptionHandler({DecodeException.class})
    public Object handleFeignDecodeException(DecodeException ex) {
        String traceId = null;
        log.error(MessageFormat.format("traceId:{0}; DecodeException", traceId), ex);
        return ResponseResult.error(ex.getMessage());
    }

    /**
     * {@link org.springframework.validation.annotation.Validated} {@link javax.validation.Valid} spring mvc controller中
     * 校验 Bean信息 使用上面两个都可以的，后面跟上一个BindResult，这样非常不方便，统一起来处理
     *
     * @param exception {@link MethodArgumentNotValidException}
     * @return {@link ResponseResult}
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object handleMethodArgumentNotValidException(Exception exception) {
        String traceId = null;
        log.error(MessageFormat.format("traceId:{0}; Param valid error", traceId), exception);
        BindingResult bindResult = null;
        if (exception instanceof BindException bindException) {
            bindResult = bindException.getBindingResult();
        }
        String msg = null;
        if (bindResult != null && bindResult.hasErrors()) {
            List<String> toSort = new ArrayList<>();
            for (ObjectError objectError : bindResult.getAllErrors()) {
                String defaultMessage = objectError.getDefaultMessage();
                toSort.add(defaultMessage);
            }
            toSort.sort(Comparator.reverseOrder());
            StringJoiner joiner = new StringJoiner(";", "", "");
            for (String defaultMessage : toSort) {
                joiner.add(defaultMessage);
            }
            msg = joiner.toString();
        }
        return ResponseResult.error(msg);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class})
    public Object handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        String traceId = null;
        String maxMega = multipartProperties.getMaxFileSize().toMegabytes() + "MB";
        log.error(MessageFormat.format("traceId:{0}; Upload or import file exceeds the maximum size", traceId), ex);
        return ResponseResult.error("", "msdp.error.0017", maxMega);
    }

    @ExceptionHandler({ConstraintViolationException.class})
    public Object handleConstraintViolationException(ConstraintViolationException ex) {
        String traceId = null;
        log.error(MessageFormat.format("traceId:{0}; ConstraintViolationException", traceId), ex);
        return ResponseResult.error(ex.getMessage());
    }

}
