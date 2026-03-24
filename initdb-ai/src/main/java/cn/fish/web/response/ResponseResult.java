package cn.fish.web.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.text.MessageFormat;

/**
 * 前后台数据响应结果对象
 */
@Data
public class ResponseResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = -5945988848945139985L;
    /**
     * 状态码
     */
    private String code;
    /**
     * 提示信息
     */
    private String message;
    /**
     * 错误解决方案界面地址
     */
    private String solutionUrl;
    /**
     * 日志跟踪id
     */
    private String traceId;
    /**
     * 数据
     */
    private T data;

    private PageParamView pageParam;


    private ResponseResult() {

    }

    /**
     * 返回响应结果
     *
     * @param code    状态码
     * @param message 信息
     * @param data    数据
     */
    @SuppressWarnings("unchecked")
    public ResponseResult(String code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = (T) data;
    }

    /**
     * 返回响应结果
     *
     * @param code    状态码
     * @param message 信息
     */
    public ResponseResult(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 返回响应结果
     *
     * @param obj 数据对象
     */
    public ResponseResult(T obj) {
        this(StatusEnum.SUCCESS.getCode(), null, obj);
    }


    public static PageParamView initPageParam(long pageNumber, long pageSize, long total, long totalPage) {
        PageParamView pageParam = new PageParamView();
        pageParam.setPageNumber(pageNumber);
        pageParam.setPageSize(pageSize);
        pageParam.setTotal(total);
        pageParam.setTotalPage(totalPage);
        return pageParam;
    }

    /**
     * 专门用户不需要对Message进行国际化处理的方法调用
     *
     * @param code    状态编码
     * @param message 信息，无需国际化
     * @return ResponseResult
     */
    public static <T> ResponseResult<T> response(String code, String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> ResponseResult<T> newInstance() {
        return new ResponseResult<>();
    }

    public static <T> ResponseResult<T> success() {
        return success(null);
    }

    public static <T> ResponseResult<T> success(T obj) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(StatusEnum.SUCCESS.getCode());
        result.setMessage("success");
        result.setData(obj);
        return result;
    }

    /**
     * 返回失败信息
     *
     * @param solutionUrl 失败解决方案界面url
     * @param code        失败码
     * @param args        参数
     * @param <T>         失败对象
     * @return 响应对象
     */
    public static <T> ResponseResult<T> error(String solutionUrl, String code, Object... args) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(code);
        result.setMessage(MessageFormat.format(code, args));
        result.setSolutionUrl(solutionUrl);
        return result;
    }

    /**
     * 返回失败信息
     *
     * @param solutionUrl 失败解决方案界面url
     * @param code        失败码
     * @param message     信息
     * @param <T>         失败对象
     * @return 响应对象
     */
    public static <T> ResponseResult<T> error(String solutionUrl, String code, String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setSolutionUrl(solutionUrl);
        return result;
    }

    /**
     * 返回失败信息
     *
     * @param code 失败码
     * @param <T>  失败对象
     * @return 响应对象
     */
    public static <T> ResponseResult<T> error(String code, Object... args) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(code);
        result.setMessage(MessageFormat.format(code, args));
        return result;
    }

    /**
     * 返回失败信息
     *
     * @param solutionUrl 失败解决方案界面url
     * @param message     信息
     * @param <T>         失败对象
     * @return 响应对象
     */
    public static <T> ResponseResult<T> error(String solutionUrl, String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(StatusEnum.FAIL.getCode());
        result.setMessage(message);
        result.setSolutionUrl(solutionUrl);
        return result;
    }

    /**
     * 返回失败信息
     *
     * @param message 信息
     * @param <T>     失败对象
     * @return 响应对象
     */
    public static <T> ResponseResult<T> error(String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(StatusEnum.FAIL.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 返回成功信息
     *
     * @param code 信息编码
     * @param args 参数
     * @return 响应对象
     */
    public static <T> ResponseResult<T> successMessage(String code, Object... args) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(StatusEnum.SUCCESS.getCode());
        result.setMessage(MessageFormat.format(code, args));
        return result;
    }

    /**
     * 返回成功信息
     *
     * @param message 信息
     * @return 响应对象
     */
    public static <T> ResponseResult<T> successMessage(String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(StatusEnum.SUCCESS.getCode());
        result.setMessage(message);
        return result;
    }
}
