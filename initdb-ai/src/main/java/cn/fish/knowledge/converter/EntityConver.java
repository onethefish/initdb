package cn.fish.knowledge.converter;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public class EntityConver {

    /**
     * 将一个对象转换为另一个对象。
     *
     * @param source      源对象
     * @param targetClass 目标对象的类
     * @param <S>         源对象的类型
     * @param <T>         目标对象的类型
     * @return 转换后的对象
     */
    public static <S, T> T convertObject(S source, Class<T> targetClass) {
        return BeanUtil.copyProperties(source, targetClass);
    }

    /**
     * 将一个对象列表转换为另一个对象列表。
     *
     * @param sources     源对象列表
     * @param targetClass 目标对象的类
     * @param <S>         源对象的类型
     * @param <T>         目标对象的类型
     * @return 转换后的对象列表
     */
    public static <S, T> List<T> convertList(List<S> sources, Class<T> targetClass) {
        return BeanUtil.copyToList(sources, targetClass);
    }

    /**
     * 将一个page对象转换为另一个page对象列表。
     *
     * @param sources     源page对象列表
     * @param targetClass 目标page对象的类
     * @param <S>         源page对象的类型
     * @param <T>         目标page对象的类型
     * @return 转换后的page对象列表
     */
    public static <S, T> IPage<T> convertPage(IPage<S> sources, Class<T> targetClass) {
        return sources.convert(e -> convertObject(e, targetClass));
    }
}
