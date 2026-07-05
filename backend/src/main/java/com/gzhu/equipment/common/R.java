package com.gzhu.equipment.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String msg;
    private T data;

    private R() {}

    public static <T> R<T> ok() {
        return rest(200, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return rest(200, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return rest(200, msg, data);
    }

    public static <T> R<T> fail() {
        return rest(500, "操作失败", null);
    }

    public static <T> R<T> fail(String msg) {
        return rest(500, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return rest(code, msg, null);
    }

    private static <T> R<T> rest(int code, String msg, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
