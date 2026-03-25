package cn.fish.web.exception;


import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Level;

import java.io.Serial;
import java.text.MessageFormat;


/**
 * 通用异常
 *
 * @author onethefish
 */
@Getter
@Setter
public class CommonException extends RuntimeException {


    @Serial
    private static final long serialVersionUID = 882853763899651927L;

    private final Level level;

    private String code;

    private String solutionUrl;

    public CommonException(String code, Object... args) {
        super(MessageFormat.format(code, args));
        this.level = Level.WARN;
        this.code = code;
    }

    public CommonException(String code, Exception e) {
        super(code, e);
        this.level = Level.ERROR;
        this.code = code;
    }

    public CommonException(Exception e) {
        super(e);
        this.level = Level.ERROR;
    }

    public CommonException(String code, Level level, Object... args) {
        super(MessageFormat.format(code, args));
        this.code = code;
        this.level = level;
    }

    public CommonException(String code, Level level, Exception e) {
        super(code, e);
        this.code = code;
        this.level = level;
    }

    public CommonException(Exception e, Level level) {
        super(e);
        this.level = level;
    }


}