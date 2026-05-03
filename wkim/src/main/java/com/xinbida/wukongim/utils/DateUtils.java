package com.xinbida.wukongim.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 2019-11-10 16:32
 * 日期时间处理
 */
public class DateUtils {
    private DateUtils() {

    }

    private static class DateUtilsBinder {
        private static final DateUtils DATE_UTILS = new DateUtils();
    }

    public static DateUtils getInstance() {
        return DateUtilsBinder.DATE_UTILS;
    }

    /**
     * Bugly#35089/#35070: 历史消息/群成员分页查询时，每行反序列化都 new 一次 SimpleDateFormat，
     * 100 行 = ~200 个实例的瞬时分配。SimpleDateFormat 非线程安全，用 ThreadLocal 让每个线程
     * 复用一个实例：分配从 O(N 行) 降到 O(线程数)。
     */
    private static final ThreadLocal<SimpleDateFormat> SDF_CACHE = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        }
    };

    public String time2DateStr(long timeStamp) {
        if (String.valueOf(timeStamp).length() < 13) {
            timeStamp = timeStamp * 1000;
        }
        return SDF_CACHE.get().format(new Date(timeStamp));
    }


    // 毫秒
    public long getCurrentMills() {
        return System.currentTimeMillis();
    }

    // 秒
    public long getCurrentSeconds() {
        return (System.currentTimeMillis() / 1000);
    }

    // 小时
    public int getHour() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR);
    }

    // 分钟
    public int getMinute() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.MINUTE);
    }

}
