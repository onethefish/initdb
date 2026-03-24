package cn.fish.web.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class PageParamView implements Serializable {

    private static final long serialVersionUID = 7441552405745764825L;
    /**
     * 当前页码
     */
    private long pageNumber;
    /**
     * 每页记录数
     */
    private long pageSize;
    /**
     * 总记录数
     */
    private long total;
    /**
     * 总页数
     */
    private long totalPage;
}
